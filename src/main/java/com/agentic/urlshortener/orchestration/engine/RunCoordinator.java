package com.agentic.urlshortener.orchestration.engine;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.agentic.urlshortener.common.util.CanonicalJson;
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
import com.agentic.urlshortener.orchestration.domain.ActorType;
import com.agentic.urlshortener.orchestration.domain.AwaitingType;
import com.agentic.urlshortener.orchestration.domain.Decision;
import com.agentic.urlshortener.orchestration.domain.DecisionType;
import com.agentic.urlshortener.orchestration.domain.FailureClass;
import com.agentic.urlshortener.orchestration.domain.FaultPlan;
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
import com.agentic.urlshortener.orchestration.reliability.CompensationCoordinator;
import com.agentic.urlshortener.orchestration.reliability.FailureEventRecorder;
import com.agentic.urlshortener.orchestration.reliability.FaultInjector;
import com.agentic.urlshortener.orchestration.reliability.RetryPolicy;
import com.agentic.urlshortener.orchestration.reliability.StagePolicyProperties;
import com.agentic.urlshortener.orchestration.metrics.OrchestrationMeters;
import com.agentic.urlshortener.orchestration.planning.InputFingerprinter;
import com.agentic.urlshortener.orchestration.planning.ReplanningService;
import com.agentic.urlshortener.orchestration.policy.PolicyEvaluationRecorder;
import com.agentic.urlshortener.orchestration.repository.DecisionRepository;
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
    private final DecisionRepository decisions;
    private final StagePolicyProperties stagePolicies;
    private final FailureEventRecorder failureEvents;
    private final OrchestrationMeters meters;
    private final TaskScheduler scheduler;
    private final CompensationCoordinator compensations;
    private final FaultInjector faults;
    private final ReplanningService replanning;
    private final InputFingerprinter fingerprinter;
    private final Set<UUID> stopping = ConcurrentHashMap.newKeySet();

    public RunCoordinator(WorkflowRunRepository runs, StageNodeRepository nodes, StageAttemptRepository attempts,
            RequirementVersionRepository requirements, ArtifactStore artifacts, ConditionEvaluator conditions,
            EntryExitCriteria criteria, AgentRegistry registry, StageDispatcher dispatcher, RunLocks locks, RunAudit audit,
            PlatformTransactionManager transactionManager, Clock clock, OrchestrationProperties properties,
            ObjectProvider<ApplicationPlanePort> port, PermissionAudit permissionAudit, PolicyEvaluationRecorder policyRecorder,
            DecisionRepository decisions, StagePolicyProperties stagePolicies, FailureEventRecorder failureEvents,
            TaskScheduler scheduler, CompensationCoordinator compensations, FaultInjector faults, ReplanningService replanning,
            InputFingerprinter fingerprinter, OrchestrationMeters meters) {
        this.fingerprinter = fingerprinter;
        this.replanning = replanning;
        this.faults = faults;
        this.compensations = compensations;
        this.stagePolicies = stagePolicies;
        this.failureEvents = failureEvents;
        this.meters = meters;
        this.scheduler = scheduler;
        this.decisions = decisions;
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
        Scheduled scheduled = locks.withLock(runId, () -> tx.execute(status -> schedule(runId)));
        for (Dispatch dispatch : scheduled.dispatches()) {
            dispatcher.submit(dispatch, result -> onAttemptFinished(dispatch, result), late -> onLateResult(dispatch, late));
        }
        if (scheduled.wakeUpAt() != null) {
            scheduler.schedule(() -> advance(runId), scheduled.wakeUpAt());
        }
    }

    /** What one scheduling cycle produced: attempts to dispatch, and when the earliest waiting retry is due. */
    private record Scheduled(List<Dispatch> dispatches, Instant wakeUpAt) {
    }

    /** A result that arrived after its attempt timed out: recorded, never applied (FR-REL-09). */
    public void onLateResult(Dispatch dispatch, StageResult result) {
        UUID runId = dispatch.context().runId();
        locks.withLock(runId, () -> tx.execute(status -> {
            audit.system(runId, "ATTEMPT_DISCARDED", dispatch.context().stageType().name(), "DISCARDED",
                    "late " + result.getClass().getSimpleName() + " result of attempt " + dispatch.attemptNo() + " after timeout",
                    Map.of("attemptNo", dispatch.attemptNo(), "generation", dispatch.generation()));
            return null;
        }));
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

    /**
     * Safe-stops a run that is not yet terminal (FR-REL-06) for {@code trigger}; returns whether this call
     * stopped it (false when the run was already terminal: exactly one terminal outcome per run).
     */
    public boolean safeStop(UUID runId, String trigger, String reason, ActorType actorType, String actorId) {
        return Boolean.TRUE.equals(locks.withLock(runId, () -> tx.execute(status -> {
            WorkflowRun run = runs.findById(runId).orElseThrow();
            if (run.getStatus().isTerminal()) {
                return false;
            }
            safeStop(runId, trigger, reason, actorType, actorId, now());
            return true;
        })));
    }

    /**
     * Recovery after a restart (FR-REL-08, plan.md §6 "Resumption"): attempts still open when the process
     * stopped become {@code INTERRUPTED}, a transient failure with cause {@code PROCESS_INTERRUPTION} that
     * counts toward the stage's attempts and follows the normal retry path; completed stages are never
     * repeated. Returns the number of attempts interrupted.
     */
    public int recoverInterrupted(UUID runId) {
        Integer interrupted = locks.withLock(runId, () -> tx.execute(status -> {
            WorkflowRun run = runs.findById(runId).orElseThrow();
            if (run.getStatus().isTerminal()) {
                return 0;
            }
            Instant now = now();
            int count = 0;
            for (StageAttempt attempt : attempts.findByRunIdAndFinishedAtIsNull(runId)) {
                StageNode node = nodes.findByRunIdAndStageKey(runId, attempt.getStageKey()).orElseThrow();
                if (node.getStatus() == StageStatus.RUNNING && node.getGeneration() == attempt.getGeneration()
                        && node.getAttempts() == attempt.getAttemptNo()) {
                    failAttempt(run, node, attempt, FailureClass.TRANSIENT, AttemptOutcome.INTERRUPTED,
                            FailureEventRecorder.PROCESS_INTERRUPTION + ": the process stopped during this attempt", now);
                } else {
                    attempt.finish(AttemptOutcome.DISCARDED, null, "stale unfinished attempt found at recovery", now);
                }
                count++;
            }
            if (count > 0) {
                audit.system(runId, "RUN_RECOVERED", "RUN", "OK", count + " interrupted attempt(s) returned to the retry path", null);
            }
            return count;
        }));
        return interrupted == null ? 0 : interrupted;
    }

    /**
     * Escalates a gate whose decision deadline has passed (FR-GOV-07): the gate is never approved by
     * default; the escalation is audited and the run safe-stops. No-op if the gate was decided meanwhile.
     */
    public boolean escalateExpiredGate(UUID runId, StageType gate) {
        return Boolean.TRUE.equals(locks.withLock(runId, () -> tx.execute(status -> {
            WorkflowRun run = runs.findById(runId).orElseThrow();
            StageNode node = nodes.findByRunIdAndStageKey(runId, gate).orElseThrow();
            Instant now = now();
            if (run.getStatus().isTerminal() || node.getStatus() != StageStatus.AWAITING_DECISION
                    || node.getDecisionDeadline() == null || !now.isAfter(node.getDecisionDeadline())) {
                return false;
            }
            String role = node.getStageType().requiredRole() == null ? "approver" : node.getStageType().requiredRole().name();
            String reason = "decision deadline " + node.getDecisionDeadline() + " passed for " + gate + " (awaiting "
                    + node.getAwaiting() + "); escalated to " + role + ", not approved by default";
            audit.system(runId, "GATE_ESCALATED", gate.name(), "ESCALATED", reason, Map.of("deadline",
                    node.getDecisionDeadline().toString(), "awaiting", String.valueOf(node.getAwaiting()), "requiredRole", role));
            safeStop(runId, "GATE_DEADLINE", reason, ActorType.SYSTEM, RunAudit.SYSTEM, now);
            return true;
        })));
    }

    private Scheduled schedule(UUID runId) {
        WorkflowRun run = runs.findById(runId).orElseThrow();
        if (run.getStatus().isTerminal() || run.getStatus() == RunStatus.PAUSED || run.getStatus() == RunStatus.COMPENSATING) {
            return new Scheduled(List.of(), null);
        }
        Instant now = now();
        List<StageNode> plan = new ArrayList<>(nodes.findByRunIdOrderByStageKeyAsc(runId));
        plan.sort(PLAN_ORDER);
        Map<StageType, StageNode> byKey = new EnumMap<>(StageType.class);
        plan.forEach(n -> byKey.put(n.getStageKey(), n));
        revalidateApprovals(runId, plan, byKey);
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
                boolean retryDue = node.getStatus() == StageStatus.RETRY_WAIT && !node.getNextAttemptAt().isAfter(now);
                if (node.getStatus() == StageStatus.READY || retryDue) {
                    Dispatch dispatch = start(run, node, requirement, cycle, now);
                    if (dispatch != null) {
                        dispatches.add(dispatch);
                    }
                    changed = true;
                }
            }
        } while (changed);

        if (deriveRunStatus(run, plan, now)) {
            return new Scheduled(List.of(), null);
        }
        Instant wakeUpAt = plan.stream()
                .filter(n -> n.getStatus() == StageStatus.RETRY_WAIT).map(StageNode::getNextAttemptAt)
                .min(Comparator.naturalOrder()).orElse(null);
        return new Scheduled(dispatches, wakeUpAt);
    }

    /**
     * Re-validation guard (FR-GOV-05): an approved gate stays approved only while every artifact bound to
     * its decision still has the approved fingerprint. Otherwise the decision is invalidated with the
     * reason, and the gate and every stage downstream of it are re-opened as a new generation.
     */
    private void revalidateApprovals(UUID runId, List<StageNode> plan, Map<StageType, StageNode> byKey) {
        Map<String, Artifact> current = null;
        for (StageNode gate : plan) {
            if (!gate.getStageType().isGate() || gate.getStatus() != StageStatus.SUCCEEDED) {
                continue;
            }
            Optional<Decision> approval = decisions.findByRunIdAndStageKeyAndValidTrueOrderByCreatedAtDesc(runId, gate.getStageKey())
                    .stream().filter(d -> "APPROVED".equals(d.getOutcome())).findFirst();
            if (approval.isEmpty() || approval.get().getBoundFingerprints() == null) {
                continue;
            }
            if (current == null) {
                current = artifacts.current(runId);
            }
            Optional<String> change = boundChange(approval.get(), current);
            if (change.isPresent()) {
                approval.get().invalidate(change.get());
                audit.system(runId, "DECISION_INVALIDATED", gate.getStageKey().name(), "INVALIDATED", change.get(),
                        Map.of("decisionId", approval.get().getId().toString()));
                replanning.reopen(runId, gate, plan, "approval invalidated: " + change.get(), true);
            }
        }
    }

    private static Optional<String> boundChange(Decision approval, Map<String, Artifact> current) {
        List<String> changes = new ArrayList<>();
        CanonicalJson.parse(approval.getBoundFingerprints()).properties().forEach(entry -> {
            String approved = entry.getValue().asString();
            Artifact now = current.get(entry.getKey());
            if (now == null || !now.getFingerprint().equals(approved)) {
                changes.add("bound artifact " + entry.getKey() + " changed from " + approved.substring(0, Math.min(12, approved.length()))
                        + " to " + (now == null ? "absent" : now.getFingerprint().substring(0, 12)));
            }
        });
        return changes.isEmpty() ? Optional.empty() : Optional.of(String.join("; ", changes));
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
    /**
     * Reuse path (FR-RPL-05): a re-opened stage whose input fingerprint equals that of an earlier
     * successful attempt does not run its agent again; the earlier artifacts are recorded again as the
     * new generation's output, with identical content and fingerprints, so unchanged downstream stages
     * can be reused too.
     */
    private boolean reuse(WorkflowRun run, StageNode node, String agentId, String fingerprint, Map<String, ArtifactInput> inputs,
            long cycle, Instant now) {
        UUID runId = run.getId();
        StageType type = node.getStageKey();
        Optional<StageAttempt> earlier = attempts.findByRunIdOrderByStartedAtAsc(runId).stream()
                .filter(a -> a.getStageKey() == type && a.getGeneration() < node.getGeneration()
                        && a.getOutcome() == AttemptOutcome.SUCCEEDED && fingerprint.equals(a.getInputFingerprint()))
                .reduce((first, second) -> second);
        if (earlier.isEmpty()) {
            return false;
        }
        List<ArtifactDraft> drafts = artifacts.producedBy(runId, type, earlier.get().getGeneration()).stream()
                .filter(a -> a.getAttemptNo() == earlier.get().getAttemptNo())
                .map(a -> new ArtifactDraft(a.getArtifactType(), a.getMediaType(), a.getContent())).toList();
        if (criteria.exit(type, drafts).isPresent()) {
            return false;
        }
        int attemptNo = node.getAttempts() + 1;
        StageAttempt reused = attempts.save(StageAttempt.start(runId, type, node.getGeneration(), attemptNo, agentId, false, null, cycle,
                now, fingerprint));
        reused.finish(AttemptOutcome.REUSED, null, "inputs unchanged since generation " + earlier.get().getGeneration(), now);
        artifacts.store(runId, type, node.getGeneration(), attemptNo, agentId + " (reused)", drafts, inputs, now);
        drafts.stream().filter(d -> d.type().equals("COMPLIANCE_REPORT")).findFirst().ifPresent(report ->
                policyRecorder.record(runId, type, node.getGeneration(), report.content(), agentId, now));
        node.markReused(now);
        audit.system(runId, "ATTEMPT_REUSED", type.name(), "REUSED", "input fingerprint unchanged since generation "
                + earlier.get().getGeneration(), Map.of("generation", node.getGeneration(), "fingerprint", fingerprint));
        audit.transition(runId, type.name(), StageStatus.READY, StageStatus.SUCCEEDED, "reused: inputs unchanged");
        afterSuccess(run, type);
        return true;
    }

    /**
     * A re-opened gate keeps its approval when every artifact the approval is bound to is unchanged
     * (FR-GOV-05); a stale approval is invalidated with the reason and the gate waits for a new decision.
     */
    private boolean carryOverApproval(UUID runId, StageNode gate, Instant now) {
        if (gate.getStageType().gateAwaiting() != AwaitingType.APPROVAL) {
            return false;
        }
        Optional<Decision> approval = decisions.findByRunIdAndStageKeyAndValidTrueOrderByCreatedAtDesc(runId, gate.getStageKey())
                .stream().filter(d -> "APPROVED".equals(d.getOutcome()) && d.getBoundFingerprints() != null).findFirst();
        if (approval.isEmpty()) {
            return false;
        }
        Optional<String> change = boundChange(approval.get(), artifacts.current(runId));
        if (change.isPresent()) {
            approval.get().invalidate(change.get());
            audit.system(runId, "DECISION_INVALIDATED", gate.getStageKey().name(), "INVALIDATED", change.get(),
                    Map.of("decisionId", approval.get().getId().toString()));
            return false;
        }
        gate.succeedGate(approval.get(), now);
        audit.system(runId, "APPROVAL_CARRIED_OVER", gate.getStageKey().name(), "OK", "bound artifacts unchanged",
                Map.of("decisionId", approval.get().getId().toString()));
        audit.transition(runId, gate.getStageKey().name(), StageStatus.READY, StageStatus.SUCCEEDED, "approval carried over");
        return true;
    }

    private Dispatch start(WorkflowRun run, StageNode node, RequirementVersion requirement, long cycle, Instant now) {
        UUID runId = run.getId();
        StageType type = node.getStageKey();
        if (type.isGate()) {
            if (carryOverApproval(runId, node, now)) {
                return null;
            }
            Instant deadline = now.plus(gateDeadline(run));
            node.awaitDecision(type.gateAwaiting(), deadline);
            audit.transition(runId, type.name(), StageStatus.READY, StageStatus.AWAITING_DECISION,
                    "waiting for a " + type.requiredRole() + " decision until " + deadline);
            return null;
        }
        boolean fallback = node.isDegraded();
        Optional<StageAgent> selected = fallback ? registry.fallback(type) : registry.primary(type);
        Map<String, ArtifactInput> inputs = artifacts.inputsFor(runId, type);
        String agentId = selected.map(StageAgent::agentId).orElse("none");
        String fingerprint = fingerprinter.fingerprint(requirement.getFingerprint(), inputs, agentId);
        if (!fallback && node.getStatus() == StageStatus.READY && node.getAttempts() == 0 && node.getGeneration() > 1
                && reuse(run, node, agentId, fingerprint, inputs, cycle, now)) {
            return null;
        }
        StageStatus from = node.getStatus();
        boolean recovering = node.getAttempts() > 0;
        Optional<FaultPlan.Fault> fault = run.getFaultPlan() == null ? Optional.empty()
                : faults.faultFor(run.getFaultPlan(), type, attempts.findByRunIdOrderByStartedAtAsc(runId));
        int attemptNo = node.startAttempt(now, fingerprint);
        attempts.save(StageAttempt.start(runId, type, node.getGeneration(), attemptNo, agentId, fallback,
                fault.map(FaultPlan.Fault::type).orElse(null), cycle, now, fingerprint));
        run.countAttempt();
        meters.attemptStarted(type, attemptNo, fallback);
        if (recovering) {
            failureEvents.recoveryStarted(runId, type, node.getGeneration(),
                    fallback ? FailureEventRecorder.FALLBACK : FailureEventRecorder.RETRY, now);
        }
        audit.transition(runId, type.name(), from, StageStatus.RUNNING, "dispatched to " + agentId);
        audit.system(runId, "ATTEMPT_STARTED", type.name(), "OK", null, Map.of("attemptNo", attemptNo,
                "generation", node.getGeneration(), "agentId", agentId, "schedulingCycle", cycle, "fallback", fallback));
        if (selected.isEmpty()) {
            failAttempt(run, node, attemptFor(runId, type, node.getGeneration(), attemptNo), FailureClass.PERMANENT,
                    AttemptOutcome.FAILED_PERMANENT, "no agent is registered for " + type, now);
            return null;
        }
        StageAgent agent = selected.get();
        StageContext context = new StageContext(runId, type, node.getGeneration(), attemptNo, requirement.getVersion(),
                requirement.getContent(), inputs, run.getPolicySetVersion(), scopedPort(runId, type, agent),
                () -> stopping.contains(runId));
        StageAgent effective = fault.map(f -> faults.inject(agent, f)).orElse(agent);
        return new Dispatch(effective, context, node.getGeneration(), attemptNo, cycle, stagePolicies.policyFor(type).timeout());
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
            meters.attemptFinished(type, AttemptOutcome.DISCARDED.name(), Duration.between(attempt.getStartedAt(), now));
            audit.system(runId, "ATTEMPT_DISCARDED", type.name(), "DISCARDED", "stage is " + node.getStatus()
                    + " (generation " + node.getGeneration() + ", attempt " + node.getAttempts() + ")", null);
            return;
        }
        run.addProcessingMillis(Duration.between(attempt.getStartedAt(), now).toMillis());
        switch (result) {
            case StageResult.Succeeded succeeded -> {
                Optional<String> unmet = criteria.exit(type, succeeded.artifacts());
                if (unmet.isPresent()) {
                    failAttempt(run, node, attempt, FailureClass.PERMANENT, AttemptOutcome.FAILED_PERMANENT, unmet.get(), now);
                    return;
                }
                artifacts.store(runId, type, dispatch.generation(), dispatch.attemptNo(), dispatch.agent().agentId(),
                        succeeded.artifacts(), context.inputs(), now);
                recordPolicies(runId, type, dispatch, succeeded.artifacts(), now);
                attempt.finish(AttemptOutcome.SUCCEEDED, null, null, now);
                node.succeed(now);
                failureEvents.stageSucceeded(runId, type, dispatch.generation(), now);
                audit.system(runId, "ATTEMPT_FINISHED", type.name(), "SUCCEEDED", succeeded.notes(),
                        Map.of("attemptNo", dispatch.attemptNo(), "generation", dispatch.generation()));
                audit.transition(runId, type.name(), StageStatus.RUNNING, StageStatus.SUCCEEDED,
                        node.isDegraded() ? "exit criteria met by the fallback agent (degraded)" : "exit criteria met");
                afterSuccess(run, type);
            }
            case StageResult.Failed failed -> failAttempt(run, node, attempt, failed.failureClass(),
                    failed.failureClass() == FailureClass.TRANSIENT ? AttemptOutcome.FAILED_TRANSIENT : AttemptOutcome.FAILED_PERMANENT,
                    failed.reason(), now);
            case StageResult.TimedOut timedOut -> failAttempt(run, node, attempt, FailureClass.TRANSIENT, AttemptOutcome.TIMED_OUT,
                    "attempt timed out after " + timedOut.timeout(), now);
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
        AttemptOutcome outcome = attempt.getOutcome();
        if (outcome == AttemptOutcome.SUCCEEDED || outcome == AttemptOutcome.NEEDS_CLARIFICATION || outcome == AttemptOutcome.POLICY_BLOCKED) {
            meters.attemptFinished(type, outcome.name(), Duration.between(attempt.getStartedAt(), now));
        }
    }

    /**
     * A failed attempt (plan.md §6): a transient failure is retried after backoff while attempts remain;
     * a permanent failure or exhausted retries switch to the stage's fallback agent once, if it has one
     * (verification stages have none), recorded as a {@code FALLBACK_USED} decision; otherwise the stage
     * fails, which safe-stops the run.
     */
    private void failAttempt(WorkflowRun run, StageNode node, StageAttempt attempt, FailureClass failureClass, AttemptOutcome outcome,
            String reason, Instant now) {
        UUID runId = run.getId();
        StageType type = node.getStageKey();
        attempt.finish(outcome, failureClass, reason, now);
        meters.attemptFinished(type, outcome.name(), Duration.between(attempt.getStartedAt(), now));
        audit.system(runId, "ATTEMPT_FAILED", type.name(), failureClass.name(), reason, Map.of("attemptNo", attempt.getAttemptNo(),
                "generation", attempt.getGeneration(), "outcome", outcome.name()));
        failureEvents.attemptFailed(runId, type, node.getGeneration(), failureClass,
                FailureEventRecorder.causeOf(outcome, attempt.getSimulatedFault(), reason), attempt.getSimulatedFault() != null, now);
        node.recordFailure(failureClass, reason);

        RetryPolicy policy = stagePolicies.policyFor(type);
        if (!node.isDegraded() && failureClass == FailureClass.TRANSIENT && policy.allowsRetryAfter(node.getAttempts())) {
            Duration backoff = policy.backoffAfter(node.getAttempts());
            node.scheduleRetry(now.plus(backoff));
            audit.transition(runId, type.name(), StageStatus.RUNNING, StageStatus.RETRY_WAIT, "transient failure: " + reason);
            audit.system(runId, "RETRY_SCHEDULED", type.name(), "OK", "attempt " + (node.getAttempts() + 1) + " of "
                    + policy.maxAttempts() + " after " + backoff.toMillis() + " ms", Map.of("nextAttemptAt",
                    node.getNextAttemptAt().toString(), "backoffMillis", backoff.toMillis(), "failedAttemptNo", attempt.getAttemptNo()));
            return;
        }
        String finalReason = failureClass == FailureClass.TRANSIENT && !node.isDegraded()
                ? "retries exhausted after " + node.getAttempts() + " attempts: " + reason
                : reason;
        Optional<StageAgent> fallback = node.isDegraded() ? Optional.empty() : registry.fallback(type);
        if (fallback.isPresent()) {
            node.markDegraded();
            node.scheduleRetry(now);
            decisions.save(Decision.create(runId, type, DecisionType.FALLBACK_USED, "FALLBACK", ActorType.SYSTEM, RunAudit.SYSTEM, null,
                    "primary agent failed (" + finalReason + "); fallback agent " + fallback.get().agentId()
                            + " produces a degraded result that lowers readiness",
                    CanonicalJson.write(Map.of("fallbackAgent", fallback.get().agentId(), "primaryFailure", finalReason)), null, now));
            audit.transition(runId, type.name(), StageStatus.RUNNING, StageStatus.RETRY_WAIT, "switching to fallback agent");
            audit.system(runId, "FALLBACK_SCHEDULED", type.name(), "DEGRADED", finalReason,
                    Map.of("fallbackAgent", fallback.get().agentId()));
            return;
        }
        node.fail(failureClass, finalReason, now);
        failureEvents.stageFailed(runId, type, node.getGeneration());
        audit.transition(runId, type.name(), StageStatus.RUNNING, StageStatus.FAILED, finalReason);
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

    /** Derives the run status from its plan; returns whether the run became terminal. */
    private boolean deriveRunStatus(WorkflowRun run, List<StageNode> plan, Instant now) {
        boolean clarificationAgain = plan.stream().anyMatch(n -> n.getStageKey() == StageType.CLARIFICATION
                && n.getStatus() == StageStatus.AWAITING_DECISION);
        if (clarificationAgain && run.getClarificationRounds() >= properties.maxClarificationRounds()) {
            safeStop(run.getId(), "CLARIFICATION_ROUNDS_EXCEEDED", "clarification still required after " + run.getClarificationRounds()
                    + " rounds (maximum " + properties.maxClarificationRounds() + "); the requirement stays ambiguous",
                    ActorType.SYSTEM, RunAudit.SYSTEM, now);
            return true;
        }
        Optional<StageNode> failed = plan.stream().filter(n -> n.getStatus() == StageStatus.FAILED).findFirst();
        if (failed.isPresent()) {
            safeStop(run.getId(), "STAGE_FAILED", "stage " + failed.get().getStageKey() + " failed: "
                    + failed.get().getLastFailureReason(), ActorType.SYSTEM, RunAudit.SYSTEM, now);
            return true;
        }
        boolean allDone = plan.stream().allMatch(n -> n.getStatus().satisfiesDependency() || n.getStatus() == StageStatus.REMOVED);
        if (allDone) {
            resume(run, now);
            run.terminate(RunStatus.COMPLETED, "all stages completed", now);
            meters.runTerminated(RunStatus.COMPLETED.name());
            failureEvents.runTerminated(run.getId());
            audit.system(run.getId(), "RUN_TERMINATED", "RUN", "COMPLETED", "all stages completed",
                    Map.of("readiness", String.valueOf(run.getReadiness())));
            return true;
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
        return false;
    }

    private void resume(WorkflowRun run, Instant now) {
        if (run.getStatus() == RunStatus.AWAITING_HUMAN) {
            run.transitionTo(RunStatus.RUNNING, now);
            audit.runTransition(run.getId(), RunStatus.AWAITING_HUMAN, RunStatus.RUNNING, "work can continue");
        }
    }

    /**
     * The safe-stop procedure of plan.md §6 (FR-REL-06): (1) no new dispatch; (2) open stages cancelled,
     * late results of in-flight attempts discarded; (3, 4) side effects compensated in reverse completion
     * order and synthetic data removed; (5) terminal outcome persisted, with manual intervention flagged if
     * compensation failed; (6) final summary produced; (7) {@code RUN_TERMINATED} emitted. Runs inside the
     * caller's transaction under the run lock.
     */
    private void safeStop(UUID runId, String trigger, String reason, ActorType actorType, String actorId, Instant now) {
        markStopping(runId);
        WorkflowRun run = runs.findById(runId).orElseThrow();
        for (StageNode node : nodes.findByRunIdOrderByStageKeyAsc(runId)) {
            StageStatus status = node.getStatus();
            if (!status.isTerminal() && !status.satisfiesDependency()) {
                node.transitionTo(StageStatus.CANCELLED);
                audit.transition(runId, node.getStageKey().name(), status, StageStatus.CANCELLED, "run safe-stopped");
            }
        }
        RunStatus from = run.getStatus();
        run.transitionTo(RunStatus.COMPENSATING, now);
        audit.runTransition(runId, from, RunStatus.COMPENSATING, reason);
        CompensationCoordinator.Result compensation = compensations.compensate(runId, reason);
        // Compensation flushes and clears the persistence context: continue with fresh copies.
        run = runs.findById(runId).orElseThrow();
        if (!compensation.succeeded()) {
            run.requireManualIntervention();
        }
        run.terminate(RunStatus.SAFE_STOPPED, reason, now);
        meters.runTerminated(RunStatus.SAFE_STOPPED.name());
        failureEvents.runTerminated(runId);
        audit.runTransition(runId, RunStatus.COMPENSATING, RunStatus.SAFE_STOPPED, reason);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("trigger", trigger);
        payload.put("compensation", compensation.actions());
        payload.put("manualInterventionRequired", !compensation.succeeded());
        decisions.save(Decision.create(runId, null, DecisionType.SAFE_STOP, "SAFE_STOPPED", actorType, actorId, null, reason,
                CanonicalJson.write(payload), null, now));
        produceFinalSummary(run, now);
        audit.system(runId, "RUN_TERMINATED", "RUN", "SAFE_STOPPED", reason,
                Map.of("trigger", trigger, "manualInterventionRequired", !compensation.succeeded()));
    }

    /** Step 6 of safe-stop (FR-ORC-17): every terminal run has a final summary, from the fallback if need be. */
    private void produceFinalSummary(WorkflowRun run, Instant now) {
        UUID runId = run.getId();
        if (artifacts.current(runId).containsKey("FINAL_SUMMARY")) {
            return;
        }
        RequirementVersion requirement = currentRequirement(run);
        Map<String, ArtifactInput> inputs = artifacts.inputsFor(runId, StageType.FINAL_SUMMARY);
        int generation = nodes.findByRunIdAndStageKey(runId, StageType.FINAL_SUMMARY).map(StageNode::getGeneration).orElse(1);
        for (Optional<StageAgent> candidate : List.of(registry.primary(StageType.FINAL_SUMMARY), registry.fallback(StageType.FINAL_SUMMARY))) {
            if (candidate.isEmpty()) {
                continue;
            }
            StageAgent agent = candidate.get();
            StageContext context = new StageContext(runId, StageType.FINAL_SUMMARY, generation, 0, requirement.getVersion(),
                    requirement.getContent(), inputs, run.getPolicySetVersion(), scopedPort(runId, StageType.FINAL_SUMMARY, agent),
                    () -> false);
            try {
                if (agent.execute(context) instanceof StageResult.Succeeded summary
                        && criteria.exit(StageType.FINAL_SUMMARY, summary.artifacts()).isEmpty()) {
                    artifacts.store(runId, StageType.FINAL_SUMMARY, generation, 0, agent.agentId(), summary.artifacts(), inputs, now);
                    return;
                }
            } catch (RuntimeException e) {
                audit.system(runId, "SUMMARY_FAILED", StageType.FINAL_SUMMARY.name(), "FAILED",
                        agent.agentId() + ": " + e.getClass().getSimpleName(), null);
            }
        }
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
