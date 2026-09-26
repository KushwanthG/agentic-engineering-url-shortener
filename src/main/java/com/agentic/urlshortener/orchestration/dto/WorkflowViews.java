package com.agentic.urlshortener.orchestration.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Response bodies of the workflow API ({@code openapi.yaml}, tag {@code workflows}); absent optional fields are omitted. */
public final class WorkflowViews {

    private WorkflowViews() {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RunSummary(String runId, String requirementRef, String title, String status, String requestedBy,
            String terminalOutcome, String readiness, Instant createdAt, Instant completedAt) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Run(String runId, String requirementRef, String title, String status, String classification, String requestedBy,
            int planVersion, int requirementVersion, String policySetVersion, String readiness, String terminalOutcome,
            String terminalReason, boolean manualInterventionRequired, boolean simulated, Instant createdAt, Instant startedAt,
            Instant completedAt, List<Stage> stages, List<PendingAction> pendingActions) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Stage(String key, String type, String status, String awaiting, List<String> dependsOn, int generation, int attempts,
            boolean reused, boolean degraded, String skipReason, Instant decisionDeadline, String lastFailureClass,
            String lastFailureReason, Instant startedAt, Instant finishedAt) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PendingAction(String stageKey, String actionType, String requiredRole, Instant deadline,
            List<ArtifactRef> reviewArtifacts, String referenceId) {
    }

    public record ArtifactRef(String artifactId, String type, int version, String fingerprint) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PlanVersion(int version, String trigger, String reason, String createdBy, Instant createdAt,
            List<Map<String, Object>> stages, Map<String, Object> diff) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RequirementVersion(int version, String source, String fingerprint, String createdBy, Instant createdAt,
            String decisionId, String content) {
    }

    public record ArtifactSummary(String artifactId, String type, int version, String stageKey, int generation, String mediaType,
            String fingerprint, String producedBy, boolean superseded, Instant createdAt) {
    }

    public record ArtifactDetail(String artifactId, String type, int version, String stageKey, int generation, String mediaType,
            String fingerprint, String producedBy, boolean superseded, Instant createdAt, String content, List<ArtifactRef> inputs,
            List<DecisionView> lineage) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TimelineEntry(String stageKey, int generation, int attemptNo, String agentId, boolean fallback, String simulatedFault,
            Instant startedAt, Instant finishedAt, Long durationMillis, String outcome, String failureClass, String error) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PolicyEvaluation(String policyId, String title, String domain, String policySetVersion, String severity,
            String outcome, String evidence, String stageKey, int generation, String exceptionId, boolean simulated,
            Instant evaluatedAt) {
    }
}
