package com.agentic.urlshortener.orchestration.port;

import java.util.Map;
import java.util.UUID;

/**
 * Release state of one capability, with the run that last changed it ({@code null} if none): a
 * rollback restores the previous state only while the flag still holds this run's value (ADR-010).
 */
public record CapabilityState(String capabilityId, boolean released, Map<String, Object> parameters, UUID changedByRun) {

    public CapabilityState {
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
    }

    public CapabilityState(String capabilityId, boolean released, Map<String, Object> parameters) {
        this(capabilityId, released, parameters, null);
    }
}
