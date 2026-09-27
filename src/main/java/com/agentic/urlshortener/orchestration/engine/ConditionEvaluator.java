package com.agentic.urlshortener.orchestration.engine;

import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.agentic.urlshortener.common.util.CanonicalJson;
import com.agentic.urlshortener.orchestration.domain.Artifact;
import com.agentic.urlshortener.orchestration.domain.StageType;

import tools.jackson.databind.JsonNode;

/**
 * Evaluates the recorded conditions of conditional stages (FR-ORC-06) from the run's artifacts:
 * clarification runs only for a blocking ambiguity, architecture approval only for a material
 * design. A skipped stage always carries its reason.
 */
@Component
public class ConditionEvaluator {

    /** Empty: the stage runs; present: the stage is skipped with this reason. */
    public Optional<String> skipReason(StageType stage, Map<String, Artifact> current) {
        return switch (stage) {
            case CLARIFICATION -> clarification(current.get("NORMALIZED_REQUIREMENT"));
            case ARCHITECTURE_APPROVAL -> architecture(current.get("DESIGN"));
            default -> Optional.empty();
        };
    }

    private static Optional<String> clarification(Artifact normalized) {
        if (normalized == null) {
            return Optional.empty();
        }
        JsonNode json = CanonicalJson.parse(normalized.getContent());
        if (json.path("clarificationRequired").asBoolean(true)) {
            return Optional.empty();
        }
        return Optional.of("Clarification not required: " + json.path("clarificationRationale").asString(
                "requirement analysis found no blocking ambiguity"));
    }

    private static Optional<String> architecture(Artifact design) {
        if (design == null) {
            return Optional.empty();
        }
        JsonNode json = CanonicalJson.parse(design.getContent());
        if (json.path("materialChange").asBoolean(true)) {
            return Optional.empty();
        }
        return Optional.of("Architecture approval not required: the design declares no API, schema, or security-control change");
    }
}
