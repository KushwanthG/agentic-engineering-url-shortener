package com.agentic.urlshortener.orchestration.port;

/** Read-only view of a link as seen by the control plane; {@code maxClicks} is null for unlimited links. */
public record LinkSnapshot(String code, String targetUrl, String status, long clickCount, boolean customAlias,
        boolean synthetic, Long maxClicks) {

    public LinkSnapshot(String code, String targetUrl, String status, long clickCount, boolean customAlias, boolean synthetic) {
        this(code, targetUrl, status, clickCount, customAlias, synthetic, null);
    }
}
