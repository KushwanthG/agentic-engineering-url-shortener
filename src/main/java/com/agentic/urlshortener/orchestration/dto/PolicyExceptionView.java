package com.agentic.urlshortener.orchestration.dto;

import java.time.Instant;

import com.agentic.urlshortener.orchestration.domain.PolicyExceptionRecord;
import com.fasterxml.jackson.annotation.JsonInclude;

/** A policy exception (contract schema {@code PolicyException}); undecided fields are omitted. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PolicyExceptionView(String exceptionId, String policyId, String status, String reason, String scope,
        String compensatingControl, String requestedBy, Instant requestedAt, Instant expiresAt, String decidedBy, Instant decidedAt,
        String decisionRationale) {

    public static PolicyExceptionView of(PolicyExceptionRecord record) {
        return new PolicyExceptionView(record.getId().toString(), record.getPolicyId(), record.getStatus(), record.getReason(),
                record.getScope(), record.getCompensatingControl(), record.getRequestedBy(), record.getRequestedAt(),
                record.getExpiresAt(), record.getDecidedBy(), record.getDecidedAt(), record.getDecisionRationale());
    }
}
