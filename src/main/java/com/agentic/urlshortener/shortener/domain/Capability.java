package com.agentic.urlshortener.shortener.domain;

import java.util.Arrays;
import java.util.Optional;

/**
 * Capabilities introduced by the scenarios. Each is delivered unreleased and becomes usable only
 * when an orchestration run releases it (FR-CAP-01, ADR-018).
 */
public enum Capability {
    CUSTOM_ALIAS("custom-alias"),
    CLICK_LIMIT("click-limit"),
    DEFAULT_EXPIRY("default-expiry");

    private final String id;

    Capability(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static Optional<Capability> fromId(String id) {
        return Arrays.stream(values()).filter(c -> c.id.equals(id)).findFirst();
    }
}
