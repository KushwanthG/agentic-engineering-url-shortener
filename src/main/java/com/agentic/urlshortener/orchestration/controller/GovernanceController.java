package com.agentic.urlshortener.orchestration.controller;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.agentic.urlshortener.common.exception.ApiException;
import com.agentic.urlshortener.common.exception.ErrorCode;
import com.agentic.urlshortener.common.security.ApiPrincipal;
import com.agentic.urlshortener.orchestration.dto.ChangeRequestSubmission;
import com.agentic.urlshortener.orchestration.dto.ChangeRequestView;
import com.agentic.urlshortener.orchestration.dto.ClarificationAnswers;
import com.agentic.urlshortener.orchestration.dto.ClarificationResult;
import com.agentic.urlshortener.orchestration.dto.DecisionView;
import com.agentic.urlshortener.orchestration.dto.GateDecisionRequest;
import com.agentic.urlshortener.orchestration.dto.PolicyExceptionRequest;
import com.agentic.urlshortener.orchestration.dto.PolicyExceptionView;
import com.agentic.urlshortener.orchestration.governance.ChangeRequestService;
import com.agentic.urlshortener.orchestration.governance.ClarificationService;
import com.agentic.urlshortener.orchestration.governance.GateService;
import com.agentic.urlshortener.orchestration.policy.PolicyExceptionService;

/**
 * Human decisions. Only authenticated human principals reach these endpoints (SecurityConfig); the
 * decision-specific role and separation of duties are checked by the services, and refusals are audited.
 */
@RestController
@RequestMapping(path = "/api/v1/workflows/{runId}", produces = MediaType.APPLICATION_JSON_VALUE)
public class GovernanceController {

    private final GateService gates;
    private final PolicyExceptionService exceptions;
    private final ClarificationService clarifications;
    private final ChangeRequestService changes;

    public GovernanceController(GateService gates, PolicyExceptionService exceptions, ClarificationService clarifications,
            ChangeRequestService changes) {
        this.gates = gates;
        this.exceptions = exceptions;
        this.clarifications = clarifications;
        this.changes = changes;
    }

    @PostMapping(path = "/change-requests", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ChangeRequestView submitChange(@PathVariable String runId, @Valid @RequestBody ChangeRequestSubmission request,
            @AuthenticationPrincipal ApiPrincipal principal) {
        return ChangeRequestView.of(changes.submit(PathIds.runId(runId), request, principal));
    }

    @PostMapping(path = "/change-requests/{changeRequestId}/decision", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ChangeRequestView decideChange(@PathVariable String runId, @PathVariable String changeRequestId,
            @Valid @RequestBody GateDecisionRequest request, @AuthenticationPrincipal ApiPrincipal principal) {
        UUID id;
        try {
            id = UUID.fromString(changeRequestId);
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.CHANGE_REQUEST_NOT_FOUND, "No change request " + changeRequestId + ".");
        }
        return ChangeRequestView.of(changes.decide(PathIds.runId(runId), id, request, principal));
    }

    @PostMapping(path = "/clarifications", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ClarificationResult answerClarification(@PathVariable String runId, @Valid @RequestBody ClarificationAnswers request,
            @AuthenticationPrincipal ApiPrincipal principal) {
        ClarificationService.Answered answered = clarifications.answer(PathIds.runId(runId), request, principal);
        return new ClarificationResult(answered.requirementVersion(), answered.planVersion(),
                answered.decisions().stream().map(DecisionView::of).toList());
    }

    @PostMapping(path = "/gates/{stageKey}/decision", consumes = MediaType.APPLICATION_JSON_VALUE)
    public DecisionView decideGate(@PathVariable String runId, @PathVariable String stageKey,
            @Valid @RequestBody GateDecisionRequest request, @AuthenticationPrincipal ApiPrincipal principal) {
        return DecisionView.of(gates.decide(PathIds.runId(runId), PathIds.stage(stageKey), request, principal));
    }

    @PostMapping(path = "/policy-exceptions", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public PolicyExceptionView requestException(@PathVariable String runId, @Valid @RequestBody PolicyExceptionRequest request,
            @AuthenticationPrincipal ApiPrincipal principal) {
        return PolicyExceptionView.of(exceptions.request(PathIds.runId(runId), request, principal));
    }

    @PostMapping(path = "/policy-exceptions/{exceptionId}/decision", consumes = MediaType.APPLICATION_JSON_VALUE)
    public PolicyExceptionView decideException(@PathVariable String runId, @PathVariable String exceptionId,
            @Valid @RequestBody GateDecisionRequest request, @AuthenticationPrincipal ApiPrincipal principal) {
        UUID id;
        try {
            id = UUID.fromString(exceptionId);
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.EXCEPTION_NOT_FOUND, "No policy exception " + exceptionId + ".");
        }
        return PolicyExceptionView.of(exceptions.decide(PathIds.runId(runId), id, request, principal));
    }
}
