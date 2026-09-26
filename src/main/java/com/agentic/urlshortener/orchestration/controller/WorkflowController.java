package com.agentic.urlshortener.orchestration.controller;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.agentic.urlshortener.common.exception.ApiException;
import com.agentic.urlshortener.common.exception.ErrorCode;
import com.agentic.urlshortener.common.security.ApiPrincipal;
import com.agentic.urlshortener.orchestration.dto.DecisionView;
import com.agentic.urlshortener.orchestration.dto.RequirementSubmission;
import com.agentic.urlshortener.orchestration.dto.WorkflowViews;
import com.agentic.urlshortener.orchestration.service.WorkflowQueryService;
import com.agentic.urlshortener.orchestration.service.WorkflowService;

/** Workflow API (FR-ORC-01, FR-ORC-09): submit requirements and inspect runs. Roles are enforced by SecurityConfig. */
@RestController
@RequestMapping(path = "/api/v1/workflows", produces = MediaType.APPLICATION_JSON_VALUE)
public class WorkflowController {

    private final WorkflowService workflows;
    private final WorkflowQueryService queries;

    public WorkflowController(WorkflowService workflows, WorkflowQueryService queries) {
        this.workflows = workflows;
        this.queries = queries;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<WorkflowViews.Run> submit(@Valid @RequestBody RequirementSubmission submission,
            @AuthenticationPrincipal ApiPrincipal principal) {
        UUID runId = workflows.submit(submission, principal);
        return ResponseEntity.created(URI.create("/api/v1/workflows/" + runId)).body(queries.run(runId));
    }

    @GetMapping
    public List<WorkflowViews.RunSummary> list() {
        return queries.list();
    }

    @GetMapping("/{runId}")
    public WorkflowViews.Run run(@PathVariable String runId) {
        return queries.run(PathIds.runId(runId));
    }

    @GetMapping("/{runId}/plan-versions")
    public List<WorkflowViews.PlanVersion> planVersions(@PathVariable String runId) {
        return queries.planVersions(PathIds.runId(runId));
    }

    @GetMapping("/{runId}/requirement-versions")
    public List<WorkflowViews.RequirementVersion> requirementVersions(@PathVariable String runId) {
        return queries.requirementVersions(PathIds.runId(runId));
    }

    @GetMapping("/{runId}/artifacts")
    public List<WorkflowViews.ArtifactSummary> artifacts(@PathVariable String runId) {
        return queries.artifacts(PathIds.runId(runId));
    }

    @GetMapping("/{runId}/artifacts/{artifactId}")
    public WorkflowViews.ArtifactDetail artifact(@PathVariable String runId, @PathVariable String artifactId) {
        UUID id;
        try {
            id = UUID.fromString(artifactId);
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.ARTIFACT_NOT_FOUND, "No artifact " + artifactId + ".");
        }
        return queries.artifact(PathIds.runId(runId), id);
    }

    @GetMapping("/{runId}/decisions")
    public List<DecisionView> decisions(@PathVariable String runId) {
        return queries.decisions(PathIds.runId(runId));
    }

    @GetMapping("/{runId}/timeline")
    public List<WorkflowViews.TimelineEntry> timeline(@PathVariable String runId) {
        return queries.timeline(PathIds.runId(runId));
    }

    @GetMapping("/{runId}/policy-evaluations")
    public List<WorkflowViews.PolicyEvaluation> policyEvaluations(@PathVariable String runId) {
        return queries.policyEvaluations(PathIds.runId(runId));
    }
}
