package com.agentic.urlshortener.orchestration.controller;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.agentic.urlshortener.common.security.ApiPrincipal;
import com.agentic.urlshortener.orchestration.dto.OperatorActionRequest;
import com.agentic.urlshortener.orchestration.dto.WorkflowViews;
import com.agentic.urlshortener.orchestration.service.OperationsService;
import com.agentic.urlshortener.orchestration.service.WorkflowQueryService;

/** Operator controls (FR-REL-06, FR-REL-07; contract tag {@code operations}); {@code RELEASE_OWNER} only (SecurityConfig). */
@RestController
@RequestMapping(path = "/api/v1/workflows/{runId}", produces = MediaType.APPLICATION_JSON_VALUE,
        consumes = MediaType.APPLICATION_JSON_VALUE)
public class OperationsController {

    private final OperationsService operations;
    private final WorkflowQueryService queries;

    public OperationsController(OperationsService operations, WorkflowQueryService queries) {
        this.operations = operations;
        this.queries = queries;
    }

    @PostMapping("/pause")
    public WorkflowViews.Run pause(@PathVariable String runId, @Valid @RequestBody OperatorActionRequest request,
            @AuthenticationPrincipal ApiPrincipal principal) {
        UUID id = PathIds.runId(runId);
        operations.pause(id, request.reason(), principal);
        return queries.run(id);
    }

    @PostMapping("/resume")
    public WorkflowViews.Run resume(@PathVariable String runId, @Valid @RequestBody OperatorActionRequest request,
            @AuthenticationPrincipal ApiPrincipal principal) {
        UUID id = PathIds.runId(runId);
        operations.resume(id, request.reason(), principal);
        return queries.run(id);
    }

    @PostMapping("/safe-stop")
    public WorkflowViews.Run safeStop(@PathVariable String runId, @Valid @RequestBody OperatorActionRequest request,
            @AuthenticationPrincipal ApiPrincipal principal) {
        UUID id = PathIds.runId(runId);
        operations.safeStop(id, request.reason(), principal);
        return queries.run(id);
    }
}
