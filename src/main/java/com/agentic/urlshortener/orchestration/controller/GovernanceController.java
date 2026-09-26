package com.agentic.urlshortener.orchestration.controller;

import jakarta.validation.Valid;

import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.agentic.urlshortener.common.security.ApiPrincipal;
import com.agentic.urlshortener.orchestration.dto.DecisionView;
import com.agentic.urlshortener.orchestration.dto.GateDecisionRequest;
import com.agentic.urlshortener.orchestration.governance.GateService;

/**
 * Human decisions. Only authenticated human principals reach these endpoints (SecurityConfig); the
 * gate-specific role is checked by {@link GateService} and a refusal is audited.
 */
@RestController
@RequestMapping(path = "/api/v1/workflows/{runId}", produces = MediaType.APPLICATION_JSON_VALUE)
public class GovernanceController {

    private final GateService gates;

    public GovernanceController(GateService gates) {
        this.gates = gates;
    }

    @PostMapping(path = "/gates/{stageKey}/decision", consumes = MediaType.APPLICATION_JSON_VALUE)
    public DecisionView decideGate(@PathVariable String runId, @PathVariable String stageKey,
            @Valid @RequestBody GateDecisionRequest request, @AuthenticationPrincipal ApiPrincipal principal) {
        return DecisionView.of(gates.decide(PathIds.runId(runId), PathIds.stage(stageKey), request, principal));
    }
}
