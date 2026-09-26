package com.agentic.urlshortener.orchestration.audit;

import java.time.Instant;

/** Result of recomputing an audit chain (contract schema {@code AuditVerification}). */
public record AuditVerification(
        String chainId,
        boolean valid,
        int eventsChecked,
        Long firstBrokenSeq,
        Instant verifiedAt,
        String message) {
}
