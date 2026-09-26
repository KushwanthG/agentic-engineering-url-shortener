package com.agentic.urlshortener.orchestration.policy;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.agentic.urlshortener.common.exception.ApiException;
import com.agentic.urlshortener.common.exception.ErrorCode;
import com.agentic.urlshortener.common.security.ApiPrincipal;
import com.agentic.urlshortener.common.security.Role;
import com.agentic.urlshortener.common.util.CanonicalJson;
import com.agentic.urlshortener.orchestration.domain.ActorType;
import com.agentic.urlshortener.orchestration.domain.Artifact;
import com.agentic.urlshortener.orchestration.domain.AwaitingType;
import com.agentic.urlshortener.orchestration.domain.Decision;
import com.agentic.urlshortener.orchestration.domain.DecisionType;
import com.agentic.urlshortener.orchestration.domain.PolicyExceptionRecord;
import com.agentic.urlshortener.orchestration.domain.StageNode;
import com.agentic.urlshortener.orchestration.domain.StageStatus;
import com.agentic.urlshortener.orchestration.domain.StageType;
import com.agentic.urlshortener.orchestration.domain.WorkflowRun;
import com.agentic.urlshortener.orchestration.dto.GateDecisionRequest;
import com.agentic.urlshortener.orchestration.dto.PolicyExceptionRequest;
import com.agentic.urlshortener.orchestration.engine.ArtifactStore;
import com.agentic.urlshortener.orchestration.engine.RunAudit;
import com.agentic.urlshortener.orchestration.engine.RunCoordinator;
import com.agentic.urlshortener.orchestration.engine.RunLocks;
import com.agentic.urlshortener.orchestration.reliability.SafeStopService;
import com.agentic.urlshortener.orchestration.repository.DecisionRepository;
import com.agentic.urlshortener.orchestration.repository.PolicyExceptionRepository;
import com.agentic.urlshortener.orchestration.repository.StageNodeRepository;
import com.agentic.urlshortener.orchestration.repository.WorkflowRunRepository;

/**
 * Time-bound exceptions for failed mandatory policies (FR-POL-04, ADR-019). A requester asks for an
 * exception for a policy that currently blocks the run, with a reason, scope, compensating control, and
 * expiry; a pending exception unblocks nothing. An approver other than the exception's requester
 * decides it. When every blocking policy is covered by an approved exception, compliance is evaluated
 * again (the failure is then reported as {@code EXCEPTION_REQUESTED} and readiness lists the
 * limitation); a rejection safe-stops the run.
 */
@Service
public class PolicyExceptionService {

    private final WorkflowRunRepository runs;
    private final StageNodeRepository nodes;
    private final PolicyExceptionRepository exceptions;
    private final DecisionRepository decisions;
    private final ArtifactStore artifacts;
    private final RunCoordinator coordinator;
    private final SafeStopService safeStops;
    private final RunLocks locks;
    private final RunAudit audit;
    private final TransactionTemplate tx;
    private final Clock clock;

    public PolicyExceptionService(WorkflowRunRepository runs, StageNodeRepository nodes, PolicyExceptionRepository exceptions,
            DecisionRepository decisions, ArtifactStore artifacts, RunCoordinator coordinator, SafeStopService safeStops, RunLocks locks,
            RunAudit audit, PlatformTransactionManager transactionManager, Clock clock) {
        this.runs = runs;
        this.nodes = nodes;
        this.exceptions = exceptions;
        this.decisions = decisions;
        this.artifacts = artifacts;
        this.coordinator = coordinator;
        this.safeStops = safeStops;
        this.locks = locks;
        this.audit = audit;
        this.tx = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    public PolicyExceptionRecord request(UUID runId, PolicyExceptionRequest request, ApiPrincipal requester) {
        return locks.withLock(runId, () -> tx.execute(status -> {
            WorkflowRun run = activeRun(runId);
            awaitingException(runId);
            List<String> blocking = blockingPolicies(runId);
            if (!blocking.contains(request.policyId())) {
                throw new ApiException(ErrorCode.ILLEGAL_STATE, "Policy " + request.policyId()
                        + " does not block this run; exceptions are possible only for " + blocking + ".");
            }
            Instant now = now();
            PolicyExceptionRecord record = exceptions.save(PolicyExceptionRecord.create(runId, request.policyId(), request.reason(),
                    request.scope(), request.compensatingControl(), requester.id(), now, request.expiresAt()));
            decisions.save(Decision.create(run.getId(), StageType.COMPLIANCE_EVALUATION, DecisionType.EXCEPTION_REQUEST, "REQUESTED",
                    ActorType.HUMAN, requester.id(), Role.REQUESTER.name(), request.reason(), payload(record), null, now));
            audit.record(runId, ActorType.HUMAN, requester.id(), "EXCEPTION_REQUESTED", request.policyId(), null, "PENDING", "OK",
                    request.reason(), Map.of("exceptionId", record.getId().toString(), "expiresAt", request.expiresAt().toString()));
            return record;
        }));
    }

    public PolicyExceptionRecord decide(UUID runId, UUID exceptionId, GateDecisionRequest request, ApiPrincipal approver) {
        PolicyExceptionRecord decided = locks.withLock(runId, () -> tx.execute(status -> {
            activeRun(runId);
            PolicyExceptionRecord record = exceptions.findById(exceptionId).filter(e -> e.getRunId().equals(runId))
                    .orElseThrow(() -> new ApiException(ErrorCode.EXCEPTION_NOT_FOUND, "No policy exception " + exceptionId + "."));
            if (!PolicyExceptionRecord.PENDING.equals(record.getStatus())) {
                throw new ApiException(ErrorCode.CONCURRENT_DECISION, "The exception was already " + record.getStatus() + ".");
            }
            if (approver.id().equals(record.getRequestedBy())) {
                audit.record(runId, ActorType.HUMAN, approver.id(), "DECISION_REFUSED", record.getPolicyId(), null, null, "REFUSED",
                        "the requester of an exception cannot decide it", Map.of("code", ErrorCode.SEPARATION_OF_DUTIES.name()));
                throw new ApiException(ErrorCode.SEPARATION_OF_DUTIES, approver.id() + " requested this exception and cannot decide it.");
            }
            Instant now = now();
            String outcome = request.approve() ? PolicyExceptionRecord.APPROVED : PolicyExceptionRecord.REJECTED;
            record.decide(outcome, approver.id(), now, request.rationale());
            decisions.save(Decision.create(runId, StageType.COMPLIANCE_EVALUATION, DecisionType.EXCEPTION_DECISION, outcome,
                    ActorType.HUMAN, approver.id(), Role.APPROVER.name(), request.rationale(), payload(record), null, now));
            audit.record(runId, ActorType.HUMAN, approver.id(), "EXCEPTION_DECIDED", record.getPolicyId(), "PENDING", outcome, outcome,
                    request.rationale(), Map.of("exceptionId", record.getId().toString()));
            if (request.approve()) {
                reEvaluateWhenCovered(runId, now);
            }
            return record;
        }));
        if (PolicyExceptionRecord.REJECTED.equals(decided.getStatus())) {
            safeStops.stop(runId, SafeStopService.Trigger.POLICY_EXCEPTION_REJECTED, "policy exception for " + decided.getPolicyId()
                    + " rejected by " + approver.id() + ": " + request.rationale(), ActorType.HUMAN, approver.id());
        } else {
            coordinator.advance(runId);
        }
        return decided;
    }

    /** Compliance runs again once every blocking policy has an approved, unexpired exception. */
    private void reEvaluateWhenCovered(UUID runId, Instant now) {
        StageNode compliance = nodes.findByRunIdAndStageKey(runId, StageType.COMPLIANCE_EVALUATION).orElseThrow();
        List<PolicyExceptionRecord> effective = exceptions.findByRunIdOrderByRequestedAtAsc(runId).stream()
                .filter(e -> e.isEffectiveAt(now)).toList();
        List<String> uncovered = blockingPolicies(runId).stream()
                .filter(p -> effective.stream().noneMatch(e -> e.getPolicyId().equals(p))).toList();
        if (compliance.getStatus() == StageStatus.AWAITING_DECISION && uncovered.isEmpty()) {
            compliance.transitionTo(StageStatus.READY);
            audit.transition(runId, StageType.COMPLIANCE_EVALUATION.name(), StageStatus.AWAITING_DECISION, StageStatus.READY,
                    "every blocking policy is covered by an approved exception; compliance is evaluated again");
        }
    }

    private WorkflowRun activeRun(UUID runId) {
        WorkflowRun run = runs.findById(runId)
                .orElseThrow(() -> new ApiException(ErrorCode.RUN_NOT_FOUND, "No workflow run " + runId + "."));
        if (run.getStatus().isTerminal()) {
            throw new ApiException(ErrorCode.RUN_TERMINAL, "The run is " + run.getStatus() + ".");
        }
        return run;
    }

    private void awaitingException(UUID runId) {
        StageNode compliance = nodes.findByRunIdAndStageKey(runId, StageType.COMPLIANCE_EVALUATION)
                .orElseThrow(() -> new ApiException(ErrorCode.ILLEGAL_STATE, "The run has no compliance stage."));
        if (compliance.getStatus() != StageStatus.AWAITING_DECISION || compliance.getAwaiting() != AwaitingType.POLICY_EXCEPTION) {
            throw new ApiException(ErrorCode.ILLEGAL_STATE, "Compliance is not waiting for a policy exception (status "
                    + compliance.getStatus() + ").");
        }
    }

    private List<String> blockingPolicies(UUID runId) {
        Artifact report = artifacts.current(runId).get("COMPLIANCE_REPORT");
        if (report == null) {
            return List.of();
        }
        List<String> blocking = new ArrayList<>();
        CanonicalJson.parse(report.getContent()).path("blockingPolicies").forEach(p -> blocking.add(p.asString()));
        return blocking;
    }

    private static String payload(PolicyExceptionRecord record) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("exceptionId", record.getId().toString());
        payload.put("policyId", record.getPolicyId());
        payload.put("scope", record.getScope());
        payload.put("compensatingControl", record.getCompensatingControl());
        payload.put("expiresAt", record.getExpiresAt().toString());
        return CanonicalJson.write(payload);
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
