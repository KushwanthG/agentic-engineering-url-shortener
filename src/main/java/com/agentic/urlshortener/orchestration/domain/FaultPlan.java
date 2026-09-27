package com.agentic.urlshortener.orchestration.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.agentic.urlshortener.common.util.CanonicalJson;

import tools.jackson.databind.JsonNode;

/**
 * The simulated faults of a demonstration run (FR-REL-11), as stored on the run. Faults exist only when
 * fault injection is enabled; everything they cause is flagged simulated.
 */
public record FaultPlan(List<Fault> faults) {

    public static final String TRANSIENT_ERROR = "TRANSIENT_ERROR";
    public static final String PERMANENT_ERROR = "PERMANENT_ERROR";
    public static final String TIMEOUT = "TIMEOUT";
    public static final String DELAY = "DELAY";
    public static final String VERIFICATION_FAILURE = "VERIFICATION_FAILURE";
    public static final String COMPENSATION_FAILURE = "COMPENSATION_FAILURE";
    public static final String POLICY_FAILURE = "POLICY_FAILURE";

    public static final FaultPlan NONE = new FaultPlan(List.of());

    public FaultPlan {
        faults = List.copyOf(faults);
    }

    /** One fault: {@code occurrences} attempts of {@code stage} are affected (default 1). */
    public record Fault(StageType stage, String type, int occurrences, Integer delayMillis, String policyId) {
    }

    public static FaultPlan parse(String json) {
        if (json == null || json.isBlank()) {
            return NONE;
        }
        List<Fault> faults = new ArrayList<>();
        for (JsonNode fault : CanonicalJson.parse(json)) {
            faults.add(new Fault(StageType.valueOf(fault.path("stage").asString()), fault.path("type").asString(),
                    fault.path("occurrences").isNumber() ? fault.path("occurrences").asInt() : 1,
                    fault.path("delayMillis").isNumber() ? fault.path("delayMillis").asInt() : null,
                    fault.path("policyId").isString() ? fault.path("policyId").asString() : null));
        }
        return new FaultPlan(faults);
    }

    public Optional<Fault> first(String type) {
        return faults.stream().filter(f -> f.type().equals(type)).findFirst();
    }

    public List<String> simulatedPolicyFailures() {
        return faults.stream().filter(f -> f.type().equals(POLICY_FAILURE)).map(Fault::policyId).toList();
    }
}
