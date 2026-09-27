package com.agentic.urlshortener.orchestration.planning;

import java.util.List;

import com.agentic.urlshortener.orchestration.domain.StageType;

/**
 * One node of a plan: the stage, its dependencies, whether it is a human gate, and the recorded
 * condition under which it runs (null = always).
 */
public record StageSpec(StageType key, List<StageType> dependsOn, boolean gate, String condition) {

    /** CLARIFICATION runs only when requirement analysis reports a blocking ambiguity. */
    public static final String CONDITION_BLOCKING_AMBIGUITY = "requirement analysis reports a blocking ambiguity";

    /** ARCHITECTURE_APPROVAL runs only when the design declares a material change. */
    public static final String CONDITION_MATERIAL_CHANGE = "design declares a material change (API, schema, or security control)";

    public StageSpec {
        dependsOn = List.copyOf(dependsOn);
    }

    public static StageSpec of(StageType key, List<StageType> dependsOn) {
        return new StageSpec(key, dependsOn, key.isGate(), null);
    }

    public static StageSpec conditional(StageType key, List<StageType> dependsOn, String condition) {
        return new StageSpec(key, dependsOn, key.isGate(), condition);
    }
}
