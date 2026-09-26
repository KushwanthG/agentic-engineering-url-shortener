package com.agentic.urlshortener.orchestration.port;

/** Read-only view of a link as seen by the control plane. */
public record LinkSnapshot(String code, String targetUrl, String status, long clickCount, boolean customAlias,
        boolean synthetic) {
}
