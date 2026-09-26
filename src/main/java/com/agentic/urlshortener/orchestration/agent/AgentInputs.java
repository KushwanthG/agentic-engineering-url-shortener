package com.agentic.urlshortener.orchestration.agent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.agentic.urlshortener.orchestration.knowledge.CapabilityCatalog;
import com.agentic.urlshortener.orchestration.knowledge.CapabilityEntry;

import tools.jackson.databind.JsonNode;

/** Helpers shared by agents to read the normalized requirement. */
final class AgentInputs {

    private AgentInputs() {
    }

    /** The non-baseline (deliverable) catalog capabilities matched by requirement analysis. */
    static List<CapabilityEntry> scenarioCapabilities(JsonNode normalized, CapabilityCatalog catalog) {
        List<CapabilityEntry> result = new ArrayList<>();
        for (JsonNode capability : normalized.path("capabilities")) {
            if (!capability.path("baseline").asBoolean(true)) {
                catalog.get(capability.path("id").asString()).ifPresent(result::add);
            }
        }
        return result;
    }

    /** Acceptance criteria id to text, in order. */
    static Map<String, String> criteria(JsonNode normalized) {
        Map<String, String> criteria = new LinkedHashMap<>();
        for (JsonNode criterion : normalized.path("acceptanceCriteria")) {
            criteria.put(criterion.path("id").asString(), criterion.path("text").asString());
        }
        return criteria;
    }

    static List<String> strings(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(v -> values.add(v.asString()));
        return values;
    }
}
