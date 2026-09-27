package com.agentic.urlshortener.orchestration.port;

import java.time.Instant;

/**
 * Read-only view of a link as seen by the control plane; {@code maxClicks} is null for unlimited links
 * and {@code expiresAt} is null for links that do not expire.
 */
public record LinkSnapshot(String code, String targetUrl, String status, long clickCount, boolean customAlias,
        boolean synthetic, Long maxClicks, Instant expiresAt) {

    public LinkSnapshot(String code, String targetUrl, String status, long clickCount, boolean customAlias, boolean synthetic) {
        this(code, targetUrl, status, clickCount, customAlias, synthetic, null, null);
    }
}
