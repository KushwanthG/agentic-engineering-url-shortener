package com.agentic.urlshortener.orchestration.port;

import java.util.Map;

/** Release state of one capability. */
public record CapabilityState(String capabilityId, boolean released, Map<String, Object> parameters) {

    public CapabilityState {
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
    }
}
