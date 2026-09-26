package com.agentic.urlshortener.orchestration.port;

import java.time.Instant;

/** Input of a synthetic probe link; mirrors the public creation request. */
public record SyntheticLinkSpec(String url, String alias, Long maxClicks, Instant expiresAt) {
}
