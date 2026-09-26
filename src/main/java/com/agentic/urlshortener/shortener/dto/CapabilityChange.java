package com.agentic.urlshortener.shortener.dto;

import java.util.Map;

/** Result of setting a capability's release state: before, after, and whether anything changed. */
public record CapabilityChange(
        String capabilityId,
        boolean previouslyReleased,
        Map<String, Object> previousParameters,
        boolean released,
        Map<String, Object> parameters,
        boolean changed) {
}
