package com.agentic.urlshortener.orchestration.port;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Read-only view of every capability's release state with its provenance, for the evidence API
 * (FR-AUD-03). It is not part of {@link ApplicationPlanePort}, so no agent can reach it.
 */
public interface CapabilityReleaseReader {

    List<CapabilityRecord> capabilities();

    /** One capability's release state and who last changed it (contract schema {@code Capability}). */
    record CapabilityRecord(String capabilityId, boolean released, Map<String, Object> parameters, String changedBy,
            UUID changedByRun, Instant changedAt, String reason) {

        public CapabilityRecord {
            parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
        }
    }
}
