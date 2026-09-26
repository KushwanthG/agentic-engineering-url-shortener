package com.agentic.urlshortener.shortener.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * A link-creation request as the service sees it: the raw client input plus the authenticated
 * consumer and the optional idempotency key. {@code syntheticRunId} is set only for verification
 * links created by an orchestration run; such links are labeled synthetic and owned by that run.
 */
public record CreateLinkCommand(
        String url,
        Instant expiresAt,
        String alias,
        Long maxClicks,
        String consumerId,
        String idempotencyKey,
        UUID syntheticRunId) {

    public CreateLinkCommand(String url, Instant expiresAt, String alias, Long maxClicks, String consumerId, String idempotencyKey) {
        this(url, expiresAt, alias, maxClicks, consumerId, idempotencyKey, null);
    }
}
