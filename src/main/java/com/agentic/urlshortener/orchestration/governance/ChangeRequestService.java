package com.agentic.urlshortener.orchestration.governance;

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
import com.agentic.urlshortener.common.util.Fingerprints;
import com.agentic.urlshortener.orchestration.agent.ArtifactDraft;
import com.agentic.urlshortener.orchestration.domain.ActorType;
import com.agentic.urlshortener.orchestration.domain.ChangeRequest;
import com.agentic.urlshortener.orchestration.domain.Classification;
import com.agentic.urlshortener.orchestration.domain.Decision;
import com.agentic.urlshortener.orchestration.domain.DecisionType;
import com.agentic.urlshortener.orchestration.domain.RequirementVersion;
import com.agentic.urlshortener.orchestration.domain.StageNode;
import com.agentic.urlshortener.orchestration.domain.StageStatus;
import com.agentic.urlshortener.orchestration.domain.StageType;
import com.agentic.urlshortener.orchestration.domain.WorkflowRun;
import com.agentic.urlshortener.orchestration.dto.ChangeRequestSubmission;
import com.agentic.urlshortener.orchestration.dto.GateDecisionRequest;
import com.agentic.urlshortener.orchestration.engine.ArtifactStore;
import com.agentic.urlshortener.orchestration.engine.RunAudit;
import com.agentic.urlshortener.orchestration.engine.RunCoordinator;
import com.agentic.urlshortener.orchestration.engine.RunLocks;
import com.agentic.urlshortener.orchestration.planning.ReplanningService;
import com.agentic.urlshortener.orchestration.repository.ChangeRequestRepository;
import com.agentic.urlshortener.orchestration.repository.DecisionRepository;
import com.agentic.urlshortener.orchestration.repository.RequirementVersionRepository;
import com.agentic.urlshortener.orchestration.repository.StageNodeRepository;
import com.agentic.urlshortener.orchestration.repository.WorkflowRunRepository;
import com.agentic.urlshortener.orchestration.service.WorkflowService;

import tools.jackson.databind.JsonNode;

/**
 * Change control for active runs (FR-RPL-03, FR-RPL-04, ADR-011). An amended requirement is material
 * when it changes the acceptance criteria, the constraints, or the change type; wording changes are not.
 * A non-material change, or any change before an approval exists, is applied at once: a new
 * requirement version, re-planning from requirement analysis, and reuse of whatever is unchanged. A
 * material change after an approval inserts a CHANGE_APPROVAL gate before every stage not yet started.
 * An approver other than the change's requester and the run's requester decides it: approving
 * applies the change; rejecting removes the gate, and the run continues on its current requirement.
 */
@Service
public class ChangeRequestService {

    private final WorkflowRunRepository runs;
    private final StageNodeRepository nodes;
    private final ChangeRequestRepository changes;
    private final RequirementVersionRepository requirements;
    private final DecisionRepository decisions;
    private final ArtifactStore artifacts;
    private final ReplanningService replanning;
    private final RunCoordinator coordinator;
    private final RunLocks locks;
    private final RunAudit audit;
    private final TransactionTemplate tx;
    private final Clock clock;

    public ChangeRequestService(WorkflowRunRepository runs, StageNodeRepository nodes, ChangeRequestRepository changes,
            RequirementVersionRepository requirements, DecisionRepository decisions, ArtifactStore artifacts, ReplanningService replanning,
            RunCoordinator coordinator, RunLocks locks, RunAudit audit, PlatformTransactionManager transactionManager, Clock clock) {
        this.runs = runs;
        this.nodes = nodes;
        this.changes = changes;
        this.requirements = requirements;
        this.decisions = decisions;
        this.artifacts = artifacts;
        this.replanning = replanning;
        this.coordinator = coordinator;
        this.locks = locks;
        this.audit = audit;
        this.tx = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    public ChangeRequest submit(UUID runId, ChangeRequestSubmission submission, ApiPrincipal requester) {
        ChangeRequest change = locks.withLock(runId, () -> tx.execute(status -> {
            WorkflowRun run = active(runId);
            boolean pending = changes.findByRunIdOrderByRequestedAtAsc(runId).stream()
                    .anyMatch(c -> ChangeRequest.PENDING_APPROVAL.equals(c.getStatus()));
            if (pending) {
                throw new ApiException(ErrorCode.ILLEGAL_STATE, "Another change request of this run is waiting for a decision.");
            }
            Instant now = now();
            RequirementVersion current = latest(runId);
            String amended = amendedDocument(CanonicalJson.parse(current.getContent()), submission);
            List<String> materialReasons = materialReasons(CanonicalJson.parse(current.getContent()), CanonicalJson.parse(amended));
            boolean approvalExists = decisions.findByRunIdOrderByCreatedAtAsc(runId).stream()
                    .anyMatch(d -> d.isValid() && d.getDecisionType() == DecisionType.GATE && "APPROVED".equals(d.getOutcome()));
            ReplanningService.Closure closure = replanning.closureOf(runId, StageType.REQUIREMENT_ANALYSIS);
            Map<String, Object> impact = new LinkedHashMap<>();
            impact.put("affectedStages", closure.stages());
            impact.put("invalidatedApprovals", closure.approvedGates());
            impact.put("materialReasons", materialReasons);
            ChangeRequest request = changes.save(ChangeRequest.create(runId, requester.id(), now, submission.reason(), amended,
                    !materialReasons.isEmpty(), CanonicalJson.write(impact)));
            decisions.save(Decision.create(runId, null, DecisionType.CHANGE_REQUEST, "SUBMITTED", ActorType.HUMAN, requester.id(),
                    Role.REQUESTER.name(), submission.reason(), CanonicalJson.write(Map.of("changeRequestId", request.getId().toString(),
                            "material", !materialReasons.isEmpty(), "materialReasons", materialReasons)), null, now));
            audit.record(runId, ActorType.HUMAN, requester.id(), "CHANGE_REQUESTED", "RUN", null, null, "OK", submission.reason(),
                    Map.of("changeRequestId", request.getId().toString(), "material", !materialReasons.isEmpty()));
            if (!materialReasons.isEmpty() && approvalExists) {
                replanning.insertChangeGate(runId, "material change " + request.getId() + " after approval: " + materialReasons,
                        requester.id(), now);
            } else {
                apply(run, request, "CHANGE_REQUEST", requester.id(), now);
            }
            return request;
        }));
        coordinator.advance(runId);
        return change;
    }

    public ChangeRequest decide(UUID runId, UUID changeRequestId, GateDecisionRequest request, ApiPrincipal approver) {
        ChangeRequest decided = locks.withLock(runId, () -> tx.execute(status -> {
            WorkflowRun run = active(runId);
            ChangeRequest change = changes.findById(changeRequestId).filter(c -> c.getRunId().equals(runId))
                    .orElseThrow(() -> new ApiException(ErrorCode.CHANGE_REQUEST_NOT_FOUND, "No change request " + changeRequestId + "."));
            if (!ChangeRequest.PENDING_APPROVAL.equals(change.getStatus())) {
                throw new ApiException(ErrorCode.CONCURRENT_DECISION, "The change request was already " + change.getStatus() + ".");
            }
            StageNode gate = nodes.findByRunIdAndStageKey(runId, StageType.CHANGE_APPROVAL).orElseThrow();
            if (gate.getStatus() != StageStatus.AWAITING_DECISION) {
                throw new ApiException(ErrorCode.ILLEGAL_STATE,
                        "The change gate is not waiting for a decision (status " + gate.getStatus() + ").");
            }
            if (approver.id().equals(change.getRequestedBy()) || approver.id().equals(run.getRequestedBy())) {
                audit.record(runId, ActorType.HUMAN, approver.id(), "DECISION_REFUSED", StageType.CHANGE_APPROVAL.name(), null, null,
                        "REFUSED", "the requester of the change or of the run cannot decide it",
                        Map.of("code", ErrorCode.SEPARATION_OF_DUTIES.name()));
                throw new ApiException(ErrorCode.SEPARATION_OF_DUTIES,
                        approver.id() + " requested this change or this run and cannot decide it.");
            }
            Instant now = now();
            String outcome = request.approve() ? "APPROVED" : "REJECTED";
            Decision decision = decisions.save(Decision.create(runId, StageType.CHANGE_APPROVAL, DecisionType.CHANGE_DECISION, outcome,
                    ActorType.HUMAN, approver.id(), Role.APPROVER.name(), request.rationale(),
                    CanonicalJson.write(Map.of("changeRequestId", change.getId().toString())), null, now));
            audit.record(runId, ActorType.HUMAN, approver.id(), "CHANGE_DECIDED", StageType.CHANGE_APPROVAL.name(), null, outcome, outcome,
                    request.rationale(), Map.of("changeRequestId", change.getId().toString()));
            if (request.approve()) {
                gate.succeedGate(decision, now);
                audit.transition(runId, StageType.CHANGE_APPROVAL.name(), StageStatus.AWAITING_DECISION, StageStatus.SUCCEEDED,
                        "change approved by " + approver.id());
                change.decide(ChangeRequest.APPLIED, approver.id(), now, request.rationale());
                apply(run, change, "CHANGE_DECISION", approver.id(), now);
            } else {
                change.decide(ChangeRequest.REJECTED, approver.id(), now, request.rationale());
                replanning.removeChangeGate(runId, "change request " + change.getId() + " rejected by " + approver.id(),
                        approver.id(), now);
            }
            return change;
        }));
        coordinator.advance(runId);
        return decided;
    }

    /** New requirement version, re-plan from requirement analysis, and the amended requirement recorded as its input. */
    private void apply(WorkflowRun run, ChangeRequest change, String trigger, String actor, Instant now) {
        UUID runId = run.getId();
        String document = CanonicalJson.canonicalize(change.getAmendedRequirement());
        int version = latest(runId).getVersion() + 1;
        requirements.save(RequirementVersion.create(runId, version, "CHANGE_REQUEST", document, Fingerprints.sha256(document), actor, now,
                null));
        run.requirementVersion(version);
        if (!ChangeRequest.APPLIED.equals(change.getStatus())) {
            change.decide(ChangeRequest.APPLIED, actor, now, "applied without change approval (not material, or before any approval)");
        }
        replanning.replan(runId, classification(CanonicalJson.parse(document).path("type").asString(), run), StageType.REQUIREMENT_ANALYSIS,
                trigger, "change request " + change.getId() + " applied; requirement version " + version, actor, now);
        artifacts.store(runId, StageType.REQUIREMENT_INGESTION, 1, 0, "change-request:" + change.getId(),
                List.of(ArtifactDraft.json("REQUIREMENT", document)), Map.of(), now);
    }

    private static String amendedDocument(JsonNode current, ChangeRequestSubmission submission) {
        Map<String, Object> document = WorkflowService.requirementDocument(submission.amendment());
        document.put("clarifications", CanonicalJson.read(CanonicalJson.write(current.path("clarifications")), List.class));
        document.put("scopeExclusions", CanonicalJson.read(CanonicalJson.write(current.path("scopeExclusions")), List.class));
        document.put("parameters", CanonicalJson.read(CanonicalJson.write(current.path("parameters")), Map.class));
        return CanonicalJson.write(document);
    }

    /** Materiality rules: acceptance criteria, constraints, or change type changed; wording alone is not material. */
    static List<String> materialReasons(JsonNode current, JsonNode amended) {
        List<String> reasons = new ArrayList<>();
        if (!texts(current.path("acceptanceCriteria")).equals(texts(amended.path("acceptanceCriteria")))) {
            reasons.add("acceptance criteria changed");
        }
        if (!current.path("constraints").equals(amended.path("constraints"))) {
            reasons.add("constraints changed");
        }
        if (!current.path("type").asString("").equals(amended.path("type").asString(""))) {
            reasons.add("change type changed from " + current.path("type").asString() + " to " + amended.path("type").asString());
        }
        return reasons;
    }

    private static List<String> texts(JsonNode criteria) {
        List<String> texts = new ArrayList<>();
        criteria.forEach(c -> texts.add(c.path("text").asString()));
        return texts;
    }

    private WorkflowRun active(UUID runId) {
        WorkflowRun run = runs.findById(runId)
                .orElseThrow(() -> new ApiException(ErrorCode.RUN_NOT_FOUND, "No workflow run " + runId + "."));
        if (run.getStatus().isTerminal()) {
            throw new ApiException(ErrorCode.RUN_TERMINAL, "The run is " + run.getStatus() + ".");
        }
        return run;
    }

    private RequirementVersion latest(UUID runId) {
        List<RequirementVersion> versions = requirements.findByRunIdOrderByVersionAsc(runId);
        return versions.get(versions.size() - 1);
    }

    private static Classification classification(String type, WorkflowRun run) {
        return switch (type) {
            case "CHANGE_TO_EXISTING" -> Classification.CHANGE_TO_EXISTING;
            case "NEW_CAPABILITY" -> Classification.NEW_CAPABILITY;
            default -> run.getClassification();
        };
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
