package com.agentic.urlshortener.orchestration.agent;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import com.agentic.urlshortener.common.util.CanonicalJson;
import com.agentic.urlshortener.orchestration.domain.StageType;
import com.agentic.urlshortener.orchestration.port.ApplicationPlanePort;

import tools.jackson.databind.JsonNode;

/**
 * Read-only view an agent executes against: the current requirement version, the run's current
 * artifacts by type, the pinned policy-set version, a cancellation signal, and a permission-scoped
 * application-plane port.
 */
public record StageContext(
        UUID runId,
        StageType stageType,
        int generation,
        int attemptNo,
        int requirementVersion,
        String requirement,
        Map<String, ArtifactInput> inputs,
        String policySetVersion,
        ApplicationPlanePort port,
        BooleanSupplier cancellation) {

    public StageContext {
        inputs = Map.copyOf(inputs);
    }

    /** A stored artifact made available to an agent. */
    public record ArtifactInput(UUID id, String type, int version, String fingerprint, String mediaType, String content) {

        public JsonNode json() {
            return CanonicalJson.parse(content);
        }
    }

    public Optional<ArtifactInput> input(String type) {
        return Optional.ofNullable(inputs.get(type));
    }

    /** The parsed JSON of a required input artifact. */
    public JsonNode inputJson(String type) {
        return input(type).orElseThrow(() -> new IllegalStateException("missing input artifact " + type)).json();
    }

    public JsonNode requirementJson() {
        return CanonicalJson.parse(requirement);
    }

    public boolean isCancelled() {
        return cancellation.getAsBoolean() || Thread.currentThread().isInterrupted();
    }
}
