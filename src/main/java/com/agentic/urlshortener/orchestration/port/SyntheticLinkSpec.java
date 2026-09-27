package com.agentic.urlshortener.orchestration.port;

import java.time.Instant;

/**
 * A probe link to create through the port. It is owned by the run and removed by run; the optional
 * idempotency key lets probes verify replay behavior.
 */
public record SyntheticLinkSpec(String url, String alias, Long maxClicks, Instant expiresAt, String idempotencyKey) {

    public SyntheticLinkSpec(String url, String alias, Long maxClicks, Instant expiresAt) {
        this(url, alias, maxClicks, expiresAt, null);
    }
}
