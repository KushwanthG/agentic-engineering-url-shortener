package com.agentic.urlshortener.orchestration.governance;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.agentic.urlshortener.common.exception.ApiException;
import com.agentic.urlshortener.common.exception.ErrorCode;
import com.agentic.urlshortener.common.security.ApiPrincipal;
import com.agentic.urlshortener.common.util.CanonicalJson;
import com.agentic.urlshortener.orchestration.domain.ActorType;
import com.agentic.urlshortener.orchestration.domain.Artifact;
import com.agentic.urlshortener.orchestration.domain.AwaitingType;
import com.agentic.urlshortener.orchestration.domain.Decision;
import com.agentic.urlshortener.orchestration.domain.DecisionType;
import com.agentic.urlshortener.orchestration.domain.FailureClass;
import com.agentic.urlshortener.orchestration.domain.PolicyExceptionRecord;
import com.agentic.urlshortener.orchestration.domain.RunStatus;
import com.agentic.urlshortener.orchestration.domain.StageNode;
import com.agentic.urlshortener.orchestration.domain.StageStatus;
import com.agentic.urlshortener.orchestration.domain.StageType;
import com.agentic.urlshortener.orchestration.domain.WorkflowRun;
import com.agentic.urlshortener.orchestration.dto.GateDecisionRequest;
import com.agentic.urlshortener.orchestration.engine.ArtifactStore;
import com.agentic.urlshortener.orchestration.engine.RunAudit;
import com.agentic.urlshortener.orchestration.engine.RunCoordinator;
import com.agentic.urlshortener.orchestration.engine.RunLocks;
import com.agentic.urlshortener.orchestration.reliability.CompensationCoordinator;
import com.agentic.urlshortener.orchestration.metrics.OrchestrationMeters;
import com.agentic.urlshortener.orchestration.repository.DecisionRepository;
import com.agentic.urlshortener.orchestration.repository.PolicyExceptionRepository;
import com.agentic.urlshortener.orchestration.repository.StageNodeRepository;
import com.agentic.urlshortener.orchestration.repository.WorkflowRunRepository;

/**
 * Human decisions on approval gates (FR-GOV-01..07, 09; ADR-008). Only an authenticated principal
 * holding the gate's role, who is not the run's requester, may decide, and only before the gate's
 * deadline. The decision records the actor, role, rationale, and the fingerprints of the reviewed
 * artifacts. The engine can never mark a gate succeeded on its own: this service is the only path
 * from {@code AWAITING_DECISION} to {@code SUCCEEDED} for a gate. A rejection compensates the run's
 * side effects and ends it {@code REJECTED}.
 */
@Service
public class GateService {

    /** Gates the run's requester may not decide (FR-GOV-04). */
    private static final Set<StageType> SEPARATED = EnumSet.of(StageType.ARCHITECTURE_APPROVAL, StageType.CHANGE_APPROVAL,
            StageType.RELEASE_APPROVAL);

    private final WorkflowRunRepository runs;
    private final StageNodeRepository nodes;
    private final DecisionRepository decisions;
    private final PolicyExceptionRepository exceptions;
    private final ArtifactStore artifacts;
    private final RunCoordinator coordinator;
    private final CompensationCoordinator compensation;
    private final RunLocks locks;
    private final RunAudit audit;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final OrchestrationMeters meters;

    public GateService(WorkflowRunRepository runs, StageNodeRepository nodes, DecisionRepository decisions, ArtifactStore artifacts,
            RunCoordinator coordinator, CompensationCoordinator compensation, RunLocks locks, RunAudit audit,
            PolicyExceptionRepository exceptions,
            PlatformTransactionManager transactionManager, Clock clock, OrchestrationMeters meters) {
        this.meters = meters;
        this.exceptions = exceptions;
        this.runs = runs;
        this.nodes = nodes;
        this.decisions = decisions;
        this.artifacts = artifacts;
        this.coordinator = coordinator;
        this.compensation = compensation;
        this.locks = locks;
        this.audit = audit;
        this.tx = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    public Decision decide(UUID runId, StageType gate, GateDecisionRequest request, ApiPrincipal principal) {
        Decision decision = locks.withLock(runId, () -> {
            ApiException refusal = tx.execute(status -> check(runId, gate, principal, request));
            if (refusal != null) {
                audit.record(runId, ActorType.HUMAN, principal.id(), "DECISION_REFUSED", gate.name(), null, null, "REFUSED",
                        refusal.getMessage(), Map.of("code", refusal.code().name()));
                throw refusal;
            }
            try {
                return tx.execute(status -> record(runId, gate, request, principal));
            } catch (ObjectOptimisticLockingFailureException e) {
                throw new ApiException(ErrorCode.CONCURRENT_DECISION, "Another decision on " + gate + " was recorded first.");
            }
        });
        coordinator.advance(runId);
        return decision;
    }

    /** Returns the reason to refuse the decision (audited), or null when it may be recorded; throws for unknown targets. */
    private ApiException check(UUID runId, StageType gate, ApiPrincipal principal, GateDecisionRequest request) {
        WorkflowRun run = runs.findById(runId)
                .orElseThrow(() -> new ApiException(ErrorCode.RUN_NOT_FOUND, "No workflow run " + runId + "."));
        if (!ReviewBundles.isApprovalGate(gate)) {
            throw new ApiException(ErrorCode.ILLEGAL_STATE, gate + " is not an approval gate decided on this endpoint.");
        }
        StageNode node = nodes.findByRunIdAndStageKey(runId, gate)
                .orElseThrow(() -> new ApiException(ErrorCode.ILLEGAL_STATE, gate + " is not part of this run's plan."));
        if (alreadyDecided(runId, node)) {
            return new ApiException(ErrorCode.CONCURRENT_DECISION, "A decision on " + gate + " was already recorded.");
        }
        if (run.getStatus().isTerminal()) {
            return new ApiException(ErrorCode.RUN_TERMINAL, "The run is " + run.getStatus() + "; no further decisions are possible.");
        }
        if (node.getStatus() != StageStatus.AWAITING_DECISION || node.getAwaiting() != AwaitingType.APPROVAL) {
            return new ApiException(ErrorCode.ILLEGAL_STATE, gate + " is not awaiting a decision (status " + node.getStatus() + ").");
        }
        if (!principal.hasRole(gate.requiredRole())) {
            return new ApiException(ErrorCode.FORBIDDEN, "Deciding " + gate + " requires the role " + gate.requiredRole() + ".");
        }
        if (SEPARATED.contains(gate) && principal.id().equals(run.getRequestedBy())) {
            return new ApiException(ErrorCode.SEPARATION_OF_DUTIES,
                    principal.id() + " is the requester of this run and cannot decide its " + gate + ".");
        }
        if (node.getDecisionDeadline() != null && now().isAfter(node.getDecisionDeadline())) {
            return new ApiException(ErrorCode.DEADLINE_PASSED,
                    "The decision deadline " + node.getDecisionDeadline() + " for " + gate + " has passed; the gate is escalated.");
        }
        if (gate == StageType.RELEASE_APPROVAL && request.approve()) {
            return releaseReadiness(run);
        }
        return null;
    }

    /**
     * Release-time re-check (FR-RDY-02, FR-RDY-04): the release is refused while the run is not ready, or
     * when an exception readiness relied on has expired since compliance was evaluated.
     */
    private ApiException releaseReadiness(WorkflowRun run) {
        if ("NOT_READY".equals(run.getReadiness())) {
            return new ApiException(ErrorCode.RELEASE_NOT_READY, "The run is not ready for release.");
        }
        Instant now = now();
        List<String> expired = exceptions.findByRunIdOrderByRequestedAtAsc(run.getId()).stream()
                .filter(e -> PolicyExceptionRecord.APPROVED.equals(e.getStatus()) && !e.isEffectiveAt(now))
                .map(e -> e.getPolicyId() + " (expired " + e.getExpiresAt() + ")").toList();
        if (!expired.isEmpty()) {
            return new ApiException(ErrorCode.RELEASE_NOT_READY, "Policy exceptions this release relies on have expired: " + expired
                    + "; request a new exception or fix the policy failure.");
        }
        return null;
    }

    /** The gate left AWAITING_DECISION in its current generation through a recorded human decision. */
    private boolean alreadyDecided(UUID runId, StageNode node) {
        boolean decidedState = node.getStatus() == StageStatus.SUCCEEDED || node.getStatus() == StageStatus.FAILED;
        return decidedState && decisions.findByRunIdAndStageKeyAndValidTrueOrderByCreatedAtDesc(runId, node.getStageKey()).stream()
                .anyMatch(d -> d.getDecisionType() == DecisionType.GATE);
    }

    private Decision record(UUID runId, StageType gate, GateDecisionRequest request, ApiPrincipal principal) {
        Instant now = now();
        WorkflowRun run = runs.findById(runId).orElseThrow();
        StageNode node = nodes.findByRunIdAndStageKey(runId, gate).orElseThrow();
        String outcome = request.approve() ? "APPROVED" : "REJECTED";
        Decision decision = decisions.save(Decision.create(runId, gate, DecisionType.GATE, outcome, ActorType.HUMAN, principal.id(),
                gate.requiredRole().name(), request.rationale(), null, CanonicalJson.write(reviewBundle(runId, gate)), now));
        audit.record(runId, ActorType.HUMAN, principal.id(), "GATE_DECIDED", gate.name(), null, outcome, outcome,
                request.rationale(), Map.of("decisionId", decision.getId().toString(), "role", gate.requiredRole().name()));
        if (request.approve()) {
            node.succeedGate(decision, now);
            audit.transition(runId, gate.name(), StageStatus.AWAITING_DECISION, StageStatus.SUCCEEDED,
                    "approved by " + principal.id());
            nodes.saveAndFlush(node);
        } else {
            String reason = gate + " rejected by " + principal.id() + ": " + request.rationale();
            node.fail(FailureClass.PERMANENT, reason, now);
            audit.transition(runId, gate.name(), StageStatus.AWAITING_DECISION, StageStatus.FAILED, reason);
            nodes.saveAndFlush(node);
            reject(run, reason, now);
        }
        return decision;
    }

    /** FR-GOV-06: no new work, open stages cancelled, side effects compensated, outcome REJECTED. */
    private void reject(WorkflowRun run, String reason, Instant now) {
        UUID runId = run.getId();
        coordinator.markStopping(runId);
        RunStatus from = run.getStatus();
        run.transitionTo(RunStatus.COMPENSATING, now);
        audit.runTransition(runId, from, RunStatus.COMPENSATING, reason);
        for (StageNode node : nodes.findByRunIdOrderByStageKeyAsc(runId)) {
            StageStatus status = node.getStatus();
            if (!status.isTerminal() && !status.satisfiesDependency()) {
                node.transitionTo(StageStatus.CANCELLED);
                audit.transition(runId, node.getStageKey().name(), status, StageStatus.CANCELLED, "run rejected");
            }
        }
        boolean compensated = compensation.compensate(runId, reason).succeeded();
        // Compensation flushes and clears the persistence context: continue with a fresh copy of the run.
        WorkflowRun current = runs.findById(runId).orElseThrow();
        if (!compensated) {
            current.requireManualIntervention();
        }
        current.terminate(RunStatus.REJECTED, reason, now);
        meters.runTerminated(RunStatus.REJECTED.name());
        audit.runTransition(runId, RunStatus.COMPENSATING, RunStatus.REJECTED, reason);
        audit.system(runId, "RUN_TERMINATED", "RUN", "REJECTED", reason, null);
    }

    private Map<String, String> reviewBundle(UUID runId, StageType gate) {
        Map<String, Artifact> current = artifacts.current(runId);
        Map<String, String> bound = new LinkedHashMap<>();
        for (String type : ReviewBundles.of(gate, AwaitingType.APPROVAL)) {
            Artifact artifact = current.get(type);
            if (artifact != null) {
                bound.put(type, artifact.getFingerprint());
            }
        }
        return bound;
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
