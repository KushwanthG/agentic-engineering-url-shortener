package com.agentic.urlshortener.orchestration.engine;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.agentic.urlshortener.common.util.CanonicalJson;
import com.agentic.urlshortener.common.util.Fingerprints;
import com.agentic.urlshortener.orchestration.agent.AgentRegistry;
import com.agentic.urlshortener.orchestration.agent.ArtifactDraft;
import com.agentic.urlshortener.orchestration.agent.PermissionAudit;
import com.agentic.urlshortener.orchestration.agent.StageAgent;
import com.agentic.urlshortener.orchestration.agent.StageContext;
import com.agentic.urlshortener.orchestration.agent.StageContext.ArtifactInput;
import com.agentic.urlshortener.orchestration.agent.StageResult;
import com.agentic.urlshortener.orchestration.config.OrchestrationProperties;
import com.agentic.urlshortener.orchestration.domain.Artifact;
import com.agentic.urlshortener.orchestration.domain.AttemptOutcome;
import com.agentic.urlshortener.orchestration.domain.AwaitingType;
import com.agentic.urlshortener.orchestration.domain.FailureClass;
import com.agentic.urlshortener.orchestration.domain.RequirementVersion;
import com.agentic.urlshortener.orchestration.domain.RunStatus;
import com.agentic.urlshortener.orchestration.domain.StageAttempt;
import com.agentic.urlshortener.orchestration.domain.StageNode;
import com.agentic.urlshortener.orchestration.domain.StageStatus;
import com.agentic.urlshortener.orchestration.domain.StageType;
import com.agentic.urlshortener.orchestration.domain.WorkflowRun;
import com.agentic.urlshortener.orchestration.port.ApplicationPlanePort;
import com.agentic.urlshortener.orchestration.port.DeferredApplicationPlanePort;
import com.agentic.urlshortener.orchestration.port.PermissionScopedPort;
import com.agentic.urlshortener.orchestration.policy.PolicyEvaluationRecorder;
import com.agentic.urlshortener.orchestration.repository.RequirementVersionRepository;
import com.agentic.urlshortener.orchestration.repository.StageAttemptRepository;
import com.agentic.urlshortener.orchestration.repository.StageNodeRepository;
import com.agentic.urlshortener.orchestration.repository.WorkflowRunRepository;

/**
 * The single place where scheduling decisions are made (plan.md §3). {@link #advance} runs under the
 * run's lock: stages whose dependencies are satisfied are skipped (condition false), failed (entry
 * criterion unmet), opened as gates, or dispatched; every change is persisted and audited before any
 * agent runs. Agents run outside transactions and locks; {@link #onAttemptFinished} applies their
 * results, discarding stale ones, and advances again.
 */
@Component
public class RunCoordinator {

    private static final Comparator<StageNode> PLAN_ORDER = Comparator.comparing(StageNode::getStageKey);

    private final WorkflowRunRepository runs;
    private final StageNodeRepository nodes;
    private final StageAttemptRepository attempts;
    private final RequirementVersionRepository requirements;
    private final ArtifactStore artifacts;
    private final ConditionEvaluator conditions;
    private final EntryExitCriteria criteria;
    private final AgentRegistry registry;
    private final StageDispatcher dispatcher;
    private final RunLocks locks;
    private final RunAudit audit;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final OrchestrationProperties properties;
    private final ObjectProvider<ApplicationPlanePort> port;
    private final PermissionAudit permissionAudit;
    private final PolicyEvaluationRecorder policyRecorder;
    private final Set<UUID> stopping = ConcurrentHashMap.newKeySet();

    public RunCoordinator(WorkflowRunRepository runs, StageNodeRepository nodes, StageAttemptRepository attempts,
            RequirementVersionRepository requirements, ArtifactStore artifacts, ConditionEvaluator conditions,
            EntryExitCriteria criteria, AgentRegistry registry, StageDispatcher dispatcher, RunLocks locks, RunAudit audit,
            PlatformTransactionManager transactionManager, Clock clock, OrchestrationProperties properties,
            ObjectProvider<ApplicationPlanePort> port, PermissionAudit permissionAudit, PolicyEvaluationRecorder policyRecorder) {
        this.policyRecorder = policyRecorder;
        this.runs = runs;
        this.nodes = nodes;
        this.attempts = attempts;
        this.requirements = requirements;
        this.artifacts = artifacts;
        this.conditions = conditions;
        this.criteria = criteria;
        this.registry = registry;
        this.dispatcher = dispatcher;
        this.locks = locks;
        this.audit = audit;
        this.tx = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.properties = properties;
        this.port = port;
        this.permissionAudit = permissionAudit;
    }

    /** Schedules everything that can progress, persists it, then dispatches the new attempts. */
    public void advance(UUID runId) {
        List<Dispatch> dispatches = locks.withLock(runId, () -> tx.execute(status -> schedule(runId)));
        for (Dispatch dispatch : dispatches) {
            dispatcher.submit(dispatch, result -> onAttemptFinished(dispatch, result));
        }
    }

    /** Applies an attempt's result under the run lock (stale or duplicate results are discarded), then advances. */
    public void onAttemptFinished(Dispatch dispatch, StageResult result) {
        UUID runId = dispatch.context().runId();
        locks.withLock(runId, () -> tx.execute(status -> {
            apply(dispatch, result);
            return null;
        }));
        advance(runId);
    }

    /** Marks a run as stopping: agents observe it through their cancellation signal. */
    public void markStopping(UUID runId) {
        stopping.add(runId);
    }

    private List<Dispatch> schedule(UUID runId) {
        WorkflowRun run = runs.findById(runId).orElseThrow();
        if (run.getStatus().isTerminal() || run.getStatus() == RunStatus.PAUSED || run.getStatus() == RunStatus.COMPENSATING) {
            return List.of();
        }
        Instant now = now();
        List<StageNode> plan = new ArrayList<>(nodes.findByRunIdOrderByStageKeyAsc(runId));
        plan.sort(PLAN_ORDER);
        Map<StageType, StageNode> byKey = new EnumMap<>(StageType.class);
        plan.forEach(n -> byKey.put(n.getStageKey(), n));
        RequirementVersion requirement = currentRequirement(run);
        long cycle = attempts.maxSchedulingCycle(runId) + 1;
        List<Dispatch> dispatches = new ArrayList<>();

        boolean changed;
        do {
            changed = false;
            Map<String, Artifact> current = artifacts.current(runId);
            for (StageNode node : plan) {
                if (node.getStatus() == StageStatus.PENDING && dependenciesSatisfied(node, byKey)) {
                    resolvePending(runId, node, current, now);
                    changed = true;
                }
            }
            for (StageNode node : plan) {
                if (node.getStatus() == StageStatus.READY) {
                    Dispatch dispatch = start(run, node, requirement, cycle, now);
                    if (dispatch != null) {
                        dispatches.add(dispatch);
                    }
                    changed = true;
                }
            }
        } while (changed);

        deriveRunStatus(run, plan, now);
        return dispatches;
    }

    private void resolvePending(UUID runId, StageNode node, Map<String, Artifact> current, Instant now) {
        String key = node.getStageKey().name();
        Optional<String> skipReason = conditions.skipReason(node.getStageKey(), current);
        if (skipReason.isPresent()) {
            node.skip(skipReason.get());
            audit.transition(runId, key, StageStatus.PENDING, StageStatus.SKIPPED, skipReason.get());
            return;
        }
        Optional<String> unmet = criteria.entry(node.getStageKey(), current);
        if (unmet.isPresent()) {
            node.fail(FailureClass.PERMANENT, unmet.get(), now);
            audit.transition(runId, key, StageStatus.PENDING, StageStatus.FAILED, unmet.get());
            return;
        }
        node.transitionTo(StageStatus.READY);
        audit.transition(runId, key, StageStatus.PENDING, StageStatus.READY, "dependencies satisfied; entry criteria met");
    }

    /** Opens a gate, or starts an attempt and returns its dispatch. */
    private Dispatch start(WorkflowRun run, StageNode node, RequirementVersion requirement, long cycle, Instant now) {
        UUID runId = run.getId();
        StageType type = node.getStageKey();
        if (type.isGate()) {
            Instant deadline = now.plus(gateDeadline(run));
            node.awaitDecision(type.gateAwaiting(), deadline);
            audit.transition(runId, type.name(), StageStatus.READY, StageStatus.AWAITING_DECISION,
                    "waiting for a " + type.requiredRole() + " decision until " + deadline);
            return null;
        }
        Optional<StageAgent> primary = registry.primary(type);
        Map<String, ArtifactInput> inputs = artifacts.inputsFor(runId, type);
        String agentId = primary.map(StageAgent::agentId).orElse("none");
        String fingerprint = inputFingerprint(requirement, inputs, agentId);
        int attemptNo = node.startAttempt(now, fingerprint);
        attempts.save(StageAttempt.start(runId, type, node.getGeneration(), attemptNo, agentId, false, null, cycle, now, fingerprint));
        run.countAttempt();
        audit.transition(runId, type.name(), StageStatus.READY, StageStatus.RUNNING, "dispatched to " + agentId);
        audit.system(runId, "ATTEMPT_STARTED", type.name(), "OK", null, Map.of("attemptNo", attemptNo,
                "generation", node.getGeneration(), "agentId", agentId, "schedulingCycle", cycle));
        if (primary.isEmpty()) {
            failAttempt(run, node, attemptFor(runId, type, node.getGeneration(), attemptNo), FailureClass.PERMANENT,
                    "no agent is registered for " + type, now);
            return null;
        }
        StageAgent agent = primary.get();
        StageContext context = new StageContext(runId, type, node.getGeneration(), attemptNo, requirement.getVersion(),
                requirement.getContent(), inputs, run.getPolicySetVersion(), scopedPort(runId, type, agent),
                () -> stopping.contains(runId));
        return new Dispatch(agent, context, node.getGeneration(), attemptNo, cycle);
    }

    private void apply(Dispatch dispatch, StageResult result) {
        StageContext context = dispatch.context();
        UUID runId = context.runId();
        StageType type = context.stageType();
        Instant now = now();
        WorkflowRun run = runs.findById(runId).orElseThrow();
        StageNode node = nodes.findByRunIdAndStageKey(runId, type).orElseThrow();
        StageAttempt attempt = attemptFor(runId, type, dispatch.generation(), dispatch.attemptNo());
        if (attempt.isFinished()) {
            return;
        }
        boolean current = node.getStatus() == StageStatus.RUNNING && node.getGeneration() == dispatch.generation()
                && node.getAttempts() == dispatch.attemptNo() && !run.getStatus().isTerminal();
        if (!current) {
            attempt.finish(AttemptOutcome.DISCARDED, null, "late or stale result discarded", now);
            audit.system(runId, "ATTEMPT_DISCARDED", type.name(), "DISCARDED", "stage is " + node.getStatus()
                    + " (generation " + node.getGeneration() + ", attempt " + node.getAttempts() + ")", null);
            return;
        }
        run.addProcessingMillis(Duration.between(attempt.getStartedAt(), now).toMillis());
        switch (result) {
            case StageResult.Succeeded succeeded -> {
                Optional<String> unmet = criteria.exit(type, succeeded.artifacts());
                if (unmet.isPresent()) {
                    failAttempt(run, node, attempt, FailureClass.PERMANENT, unmet.get(), now);
                    return;
                }
                artifacts.store(runId, type, dispatch.generation(), dispatch.attemptNo(), dispatch.agent().agentId(),
                        succeeded.artifacts(), context.inputs(), now);
                recordPolicies(runId, type, dispatch, succeeded.artifacts(), now);
                attempt.finish(AttemptOutcome.SUCCEEDED, null, null, now);
                node.succeed(now);
                audit.system(runId, "ATTEMPT_FINISHED", type.name(), "SUCCEEDED", succeeded.notes(),
                        Map.of("attemptNo", dispatch.attemptNo(), "generation", dispatch.generation()));
                audit.transition(runId, type.name(), StageStatus.RUNNING, StageStatus.SUCCEEDED, "exit criteria met");
                afterSuccess(run, type);
            }
            case StageResult.Failed failed -> failAttempt(run, node, attempt, failed.failureClass(), failed.reason(), now);
            case StageResult.NeedsClarification clarification -> {
                artifacts.store(runId, type, dispatch.generation(), dispatch.attemptNo(), dispatch.agent().agentId(),
                        List.of(clarification.clarificationRequest()), context.inputs(), now);
                attempt.finish(AttemptOutcome.NEEDS_CLARIFICATION, null, clarification.reason(), now);
                node.awaitDecision(AwaitingType.CLARIFICATION, now.plus(gateDeadline(run)));
                audit.transition(runId, type.name(), StageStatus.RUNNING, StageStatus.AWAITING_DECISION, clarification.reason());
            }
            case StageResult.PolicyBlocked blocked -> {
                artifacts.store(runId, type, dispatch.generation(), dispatch.attemptNo(), dispatch.agent().agentId(),
                        blocked.artifacts(), context.inputs(), now);
                recordPolicies(runId, type, dispatch, blocked.artifacts(), now);
                attempt.finish(AttemptOutcome.POLICY_BLOCKED, null, blocked.reason(), now);
                node.awaitDecision(AwaitingType.POLICY_EXCEPTION, now.plus(gateDeadline(run)));
                run.readiness("NOT_READY");
                audit.transition(runId, type.name(), StageStatus.RUNNING, StageStatus.AWAITING_DECISION,
                        "mandatory policy failed: " + blocked.blockingPolicies());
            }
        }
    }

    private void failAttempt(WorkflowRun run, StageNode node, StageAttempt attempt, FailureClass failureClass, String reason,
            Instant now) {
        String key = node.getStageKey().name();
        attempt.finish(failureClass == FailureClass.TRANSIENT ? AttemptOutcome.FAILED_TRANSIENT : AttemptOutcome.FAILED_PERMANENT,
                failureClass, reason, now);
        audit.system(run.getId(), "ATTEMPT_FAILED", key, failureClass.name(), reason, Map.of("attemptNo", attempt.getAttemptNo(),
                "generation", attempt.getGeneration()));
        node.fail(failureClass, reason, now);
        audit.transition(run.getId(), key, StageStatus.RUNNING, StageStatus.FAILED, reason);
    }

    private void recordPolicies(UUID runId, StageType type, Dispatch dispatch, List<ArtifactDraft> drafts, Instant now) {
        drafts.stream().filter(d -> d.type().equals("COMPLIANCE_REPORT")).findFirst().ifPresent(report ->
                policyRecorder.record(runId, type, dispatch.generation(), report.content(), dispatch.agent().agentId(), now));
    }

    private void afterSuccess(WorkflowRun run, StageType type) {
        if (type == StageType.COMPLIANCE_EVALUATION) {
            Artifact readiness = artifacts.current(run.getId()).get("READINESS_REPORT");
            if (readiness != null) {
                run.readiness(CanonicalJson.parse(readiness.getContent()).path("outcome").asString("NOT_READY"));
            }
        }
    }

    private void deriveRunStatus(WorkflowRun run, List<StageNode> plan, Instant now) {
        Optional<StageNode> failed = plan.stream().filter(n -> n.getStatus() == StageStatus.FAILED).findFirst();
        if (failed.isPresent()) {
            safeStop(run, plan, "stage " + failed.get().getStageKey() + " failed: " + failed.get().getLastFailureReason(), now);
            return;
        }
        boolean allDone = plan.stream().allMatch(n -> n.getStatus().satisfiesDependency() || n.getStatus() == StageStatus.REMOVED);
        if (allDone) {
            resume(run, now);
            run.terminate(RunStatus.COMPLETED, "all stages completed", now);
            audit.system(run.getId(), "RUN_TERMINATED", "RUN", "COMPLETED", "all stages completed",
                    Map.of("readiness", String.valueOf(run.getReadiness())));
            return;
        }
        boolean active = plan.stream().anyMatch(n -> n.getStatus() == StageStatus.RUNNING || n.getStatus() == StageStatus.READY
                || n.getStatus() == StageStatus.RETRY_WAIT);
        boolean waiting = plan.stream().anyMatch(n -> n.getStatus() == StageStatus.AWAITING_DECISION);
        if (!active && waiting && run.getStatus() == RunStatus.RUNNING) {
            run.transitionTo(RunStatus.AWAITING_HUMAN, now);
            audit.runTransition(run.getId(), RunStatus.RUNNING, RunStatus.AWAITING_HUMAN, "waiting for a human decision");
        } else if (active) {
            resume(run, now);
        }
    }

    private void resume(WorkflowRun run, Instant now) {
        if (run.getStatus() == RunStatus.AWAITING_HUMAN) {
            run.transitionTo(RunStatus.RUNNING, now);
            audit.runTransition(run.getId(), RunStatus.AWAITING_HUMAN, RunStatus.RUNNING, "work can continue");
        }
    }

    /** Stops the run: no new starts, open stages cancelled, terminal outcome recorded (plan.md §6). */
    private void safeStop(WorkflowRun run, List<StageNode> plan, String reason, Instant now) {
        markStopping(run.getId());
        for (StageNode node : plan) {
            StageStatus status = node.getStatus();
            if (!status.isTerminal() && !status.satisfiesDependency()) {
                node.transitionTo(StageStatus.CANCELLED);
                audit.transition(run.getId(), node.getStageKey().name(), status, StageStatus.CANCELLED, "run safe-stopped");
            }
        }
        RunStatus from = run.getStatus();
        run.terminate(RunStatus.SAFE_STOPPED, reason, now);
        audit.runTransition(run.getId(), from, RunStatus.SAFE_STOPPED, reason);
        audit.system(run.getId(), "RUN_TERMINATED", "RUN", "SAFE_STOPPED", reason, null);
    }

    private StageAttempt attemptFor(UUID runId, StageType type, int generation, int attemptNo) {
        return attempts.findByRunIdAndStageKeyAndGenerationAndAttemptNo(runId, type, generation, attemptNo).orElseThrow();
    }

    private RequirementVersion currentRequirement(WorkflowRun run) {
        return requirements.findByRunIdOrderByVersionAsc(run.getId()).stream()
                .filter(r -> r.getVersion() == run.getCurrentRequirementVersion())
                .findFirst().orElseThrow();
    }

    private static boolean dependenciesSatisfied(StageNode node, Map<StageType, StageNode> byKey) {
        return node.getDependsOn().stream().allMatch(d -> byKey.containsKey(d) && byKey.get(d).getStatus().satisfiesDependency());
    }

    /** Fingerprint of everything an attempt depends on (requirement, inputs, agent version) for reuse decisions. */
    static String inputFingerprint(RequirementVersion requirement, Map<String, ArtifactInput> inputs, String agentId) {
        Map<String, Object> basis = new TreeMap<>();
        basis.put("requirement", requirement.getFingerprint());
        basis.put("agent", agentId);
        Map<String, String> inputFingerprints = new TreeMap<>();
        inputs.forEach((type, input) -> inputFingerprints.put(type, input.fingerprint()));
        basis.put("inputs", inputFingerprints);
        return Fingerprints.ofValue(basis);
    }

    private ApplicationPlanePort scopedPort(UUID runId, StageType type, StageAgent agent) {
        return new PermissionScopedPort(new DeferredApplicationPlanePort(port::getObject), agent.permissions(), runId, type,
                agent.agentId(), permissionAudit);
    }

    private Duration gateDeadline(WorkflowRun run) {
        return run.getGateDeadlineSeconds() != null ? Duration.ofSeconds(run.getGateDeadlineSeconds()) : properties.gateDeadline();
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
