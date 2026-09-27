package com.agentic.urlshortener.orchestration.audit;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Result of recomputing an audit chain (contract schema {@code AuditVerification}). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuditVerification(
        String chainId,
        boolean valid,
        int eventsChecked,
        Long firstBrokenSeq,
        Instant verifiedAt,
        String message) {
}
