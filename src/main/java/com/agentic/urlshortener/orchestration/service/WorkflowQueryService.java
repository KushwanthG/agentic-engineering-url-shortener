package com.agentic.urlshortener.orchestration.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.agentic.urlshortener.common.exception.ApiException;
import com.agentic.urlshortener.common.exception.ErrorCode;
import com.agentic.urlshortener.common.util.CanonicalJson;
import com.agentic.urlshortener.orchestration.domain.Artifact;
import com.agentic.urlshortener.orchestration.domain.AwaitingType;
import com.agentic.urlshortener.orchestration.domain.StageNode;
import com.agentic.urlshortener.orchestration.domain.StageStatus;
import com.agentic.urlshortener.orchestration.domain.WorkflowRun;
import com.agentic.urlshortener.orchestration.dto.DecisionView;
import com.agentic.urlshortener.orchestration.engine.LineageService;
import com.agentic.urlshortener.orchestration.dto.WorkflowViews;
import com.agentic.urlshortener.orchestration.governance.ReviewBundles;
import com.agentic.urlshortener.orchestration.policy.PolicySet;
import com.agentic.urlshortener.orchestration.policy.PolicySetLoader;
import com.agentic.urlshortener.orchestration.repository.ArtifactRepository;
import com.agentic.urlshortener.orchestration.repository.DecisionRepository;
import com.agentic.urlshortener.orchestration.repository.PlanVersionRepository;
import com.agentic.urlshortener.orchestration.repository.PolicyEvaluationRepository;
import com.agentic.urlshortener.orchestration.repository.RequirementVersionRepository;
import com.agentic.urlshortener.orchestration.repository.StageAttemptRepository;
import com.agentic.urlshortener.orchestration.repository.StageNodeRepository;
import com.agentic.urlshortener.orchestration.repository.WorkflowRunRepository;

import tools.jackson.databind.JsonNode;

/** Read side of the workflow API (FR-ORC-09, FR-AUD-03, FR-AUD-06): run state, history, and artifact lineage. */
@Service
@Transactional(readOnly = true)
public class WorkflowQueryService {

    private final WorkflowRunRepository runs;
    private final StageNodeRepository nodes;
    private final StageAttemptRepository attempts;
    private final PlanVersionRepository plans;
    private final RequirementVersionRepository requirements;
    private final ArtifactRepository artifacts;
    private final DecisionRepository decisions;
    private final PolicyEvaluationRepository evaluations;
    private final PolicySetLoader policySets;
    private final LineageService lineage;

    public WorkflowQueryService(WorkflowRunRepository runs, StageNodeRepository nodes, StageAttemptRepository attempts,
            PlanVersionRepository plans, RequirementVersionRepository requirements, ArtifactRepository artifacts,
            DecisionRepository decisions, PolicyEvaluationRepository evaluations, PolicySetLoader policySets,
            LineageService lineage) {
        this.runs = runs;
        this.nodes = nodes;
        this.attempts = attempts;
        this.plans = plans;
        this.requirements = requirements;
        this.artifacts = artifacts;
        this.decisions = decisions;
        this.evaluations = evaluations;
        this.policySets = policySets;
        this.lineage = lineage;
    }

    public List<WorkflowViews.RunSummary> list() {
        return runs.findAllByOrderByCreatedAtDesc().stream()
                .map(r -> new WorkflowViews.RunSummary(r.getId().toString(), r.getRequirementRef(), r.getTitle(), r.getStatus().name(),
                        r.getRequestedBy(), r.getTerminalOutcome(), r.getReadiness(), r.getCreatedAt(), r.getCompletedAt()))
                .toList();
    }

    public WorkflowViews.Run run(UUID runId) {
        WorkflowRun run = find(runId);
        List<StageNode> stages = nodes.findByRunIdOrderByStageKeyAsc(runId).stream()
                .sorted(Comparator.comparing(StageNode::getStageKey)).toList();
        Map<String, Artifact> current = currentArtifacts(runId);
        List<WorkflowViews.PendingAction> pending = new ArrayList<>();
        for (StageNode node : stages) {
            if (node.getStatus() == StageStatus.AWAITING_DECISION) {
                List<WorkflowViews.ArtifactRef> review = ReviewBundles.of(node.getStageKey(), node.getAwaiting()).stream()
                        .filter(current::containsKey).map(t -> ref(current.get(t))).toList();
                String role = node.getAwaiting() == AwaitingType.POLICY_EXCEPTION ? "REQUESTER" : node.getStageKey().requiredRole() == null
                        ? "APPROVER" : node.getStageKey().requiredRole().name();
                pending.add(new WorkflowViews.PendingAction(node.getStageKey().name(), node.getAwaiting().name(), role,
                        node.getDecisionDeadline(), review, null));
            }
        }
        return new WorkflowViews.Run(run.getId().toString(), run.getRequirementRef(), run.getTitle(), run.getStatus().name(),
                run.getClassification().name(), run.getRequestedBy(), run.getCurrentPlanVersion(), run.getCurrentRequirementVersion(),
                run.getPolicySetVersion(), run.getReadiness(), run.getTerminalOutcome(), run.getTerminalReason(),
                run.isManualInterventionRequired(), run.getFaultPlan() != null, run.getCreatedAt(), run.getStartedAt(),
                run.getCompletedAt(), stages.stream().map(WorkflowQueryService::stage).toList(), pending);
    }

    @SuppressWarnings("unchecked")
    public List<WorkflowViews.PlanVersion> planVersions(UUID runId) {
        find(runId);
        return plans.findByRunIdOrderByVersionAsc(runId).stream().map(p -> new WorkflowViews.PlanVersion(p.getVersion(),
                p.getTriggerType(), p.getReason(), p.getCreatedBy(), p.getCreatedAt(),
                (List<Map<String, Object>>) CanonicalJson.read(p.getGraph(), Map.class).get("stages"),
                p.getDiff() == null ? null : CanonicalJson.read(p.getDiff(), Map.class))).toList();
    }

    public List<WorkflowViews.RequirementVersion> requirementVersions(UUID runId) {
        find(runId);
        return requirements.findByRunIdOrderByVersionAsc(runId).stream().map(r -> new WorkflowViews.RequirementVersion(r.getVersion(),
                r.getSource(), r.getFingerprint(), r.getCreatedBy(), r.getCreatedAt(),
                r.getDecisionId() == null ? null : r.getDecisionId().toString(), r.getContent())).toList();
    }

    public List<WorkflowViews.ArtifactSummary> artifacts(UUID runId) {
        find(runId);
        return artifacts.findByRunIdOrderByCreatedAtAsc(runId).stream().map(WorkflowQueryService::summary).toList();
    }

    public WorkflowViews.ArtifactDetail artifact(UUID runId, UUID artifactId) {
        find(runId);
        Artifact artifact = artifacts.findByIdAndRunId(artifactId, runId)
                .orElseThrow(() -> new ApiException(ErrorCode.ARTIFACT_NOT_FOUND, "No artifact " + artifactId + " in run " + runId + "."));
        return new WorkflowViews.ArtifactDetail(artifact.getId().toString(), artifact.getArtifactType(), artifact.getVersion(),
                artifact.getStageKey().name(), artifact.getGeneration(), artifact.getMediaType(), artifact.getFingerprint(),
                artifact.getProducedBy(), artifact.isSuperseded(), artifact.getCreatedAt(), artifact.getContent(), inputs(artifact),
                lineage.of(runId, artifact).stream().map(DecisionView::of).toList());
    }

    public List<DecisionView> decisions(UUID runId) {
        find(runId);
        return decisions.findByRunIdOrderByCreatedAtAsc(runId).stream().map(DecisionView::of).toList();
    }

    public List<WorkflowViews.TimelineEntry> timeline(UUID runId) {
        find(runId);
        return attempts.findByRunIdOrderByStartedAtAsc(runId).stream().map(a -> new WorkflowViews.TimelineEntry(a.getStageKey().name(),
                a.getGeneration(), a.getAttemptNo(), a.getAgentId(), a.isFallback(), a.getSimulatedFault(), a.getStartedAt(),
                a.getFinishedAt(), a.getFinishedAt() == null ? null : Duration.between(a.getStartedAt(), a.getFinishedAt()).toMillis(),
                a.getOutcome() == null ? null : a.getOutcome().name(), a.getFailureClass() == null ? null : a.getFailureClass().name(),
                a.getError())).toList();
    }

    public List<WorkflowViews.PolicyEvaluation> policyEvaluations(UUID runId) {
        find(runId);
        PolicySet set = policySets.current();
        return evaluations.findByRunIdOrderByEvaluatedAtAsc(runId).stream().map(e -> {
            PolicySet.PolicyDefinition policy = set.policy(e.getPolicyId()).orElse(null);
            return new WorkflowViews.PolicyEvaluation(e.getPolicyId(), policy == null ? null : policy.title(),
                    policy == null ? null : policy.domain(), e.getPolicySetVersion(), e.getSeverity(), e.getOutcome(), e.getEvidence(),
                    e.getStageKey().name(), e.getGeneration(), e.getExceptionId() == null ? null : e.getExceptionId().toString(),
                    e.isSimulated(), e.getEvaluatedAt());
        }).toList();
    }

    private static List<WorkflowViews.ArtifactRef> inputs(Artifact artifact) {
        List<WorkflowViews.ArtifactRef> refs = new ArrayList<>();
        for (JsonNode ref : CanonicalJson.parse(artifact.getInputRefs())) {
            refs.add(new WorkflowViews.ArtifactRef(ref.path("artifactId").asString(), ref.path("type").asString(),
                    ref.path("version").asInt(), ref.path("fingerprint").asString()));
        }
        return refs;
    }

    private Map<String, Artifact> currentArtifacts(UUID runId) {
        Map<String, Artifact> current = new LinkedHashMap<>();
        for (Artifact artifact : artifacts.findByRunIdAndSupersededFalseOrderByCreatedAtAsc(runId)) {
            current.merge(artifact.getArtifactType(), artifact, (a, b) -> a.getVersion() >= b.getVersion() ? a : b);
        }
        return current;
    }

    private WorkflowRun find(UUID runId) {
        return runs.findById(runId).orElseThrow(() -> new ApiException(ErrorCode.RUN_NOT_FOUND, "No workflow run " + runId + "."));
    }

    private static WorkflowViews.Stage stage(StageNode node) {
        return new WorkflowViews.Stage(node.getStageKey().name(), node.getStageType().name(), node.getStatus().name(),
                node.getAwaiting() == null ? null : node.getAwaiting().name(), node.getDependsOn().stream().map(Enum::name).toList(),
                node.getGeneration(), node.getAttempts(), node.isReused(), node.isDegraded(), node.getSkipReason(),
                node.getDecisionDeadline(), node.getLastFailureClass() == null ? null : node.getLastFailureClass().name(),
                node.getLastFailureReason(), node.getStartedAt(), node.getFinishedAt());
    }

    private static WorkflowViews.ArtifactRef ref(Artifact artifact) {
        return new WorkflowViews.ArtifactRef(artifact.getId().toString(), artifact.getArtifactType(), artifact.getVersion(),
                artifact.getFingerprint());
    }

    private static WorkflowViews.ArtifactSummary summary(Artifact a) {
        return new WorkflowViews.ArtifactSummary(a.getId().toString(), a.getArtifactType(), a.getVersion(), a.getStageKey().name(),
                a.getGeneration(), a.getMediaType(), a.getFingerprint(), a.getProducedBy(), a.isSuperseded(), a.getCreatedAt());
    }
}
