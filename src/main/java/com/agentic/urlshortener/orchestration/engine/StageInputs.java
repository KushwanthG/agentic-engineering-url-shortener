package com.agentic.urlshortener.orchestration.engine;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.agentic.urlshortener.orchestration.domain.StageType;

/**
 * Declared inputs per stage (plan.md §3, "Stage specifications"): required artifact types are an
 * entry criterion; optional ones are passed when present (for example the impact analysis of a
 * brownfield plan). {@code COMPLIANCE_EVALUATION} and {@code FINAL_SUMMARY} read every current artifact.
 */
public final class StageInputs {

    /** Required and optional input artifact types of one stage. */
    public record Declared(List<String> required, List<String> optional, boolean all) {
    }

    private static final Map<StageType, Declared> DECLARED = new EnumMap<>(StageType.class);

    static {
        put(StageType.REQUIREMENT_INGESTION, List.of(), List.of());
        put(StageType.REQUIREMENT_ANALYSIS, List.of("REQUIREMENT"), List.of());
        put(StageType.DECOMPOSITION, List.of("NORMALIZED_REQUIREMENT"), List.of());
        put(StageType.THREAT_ASSESSMENT, List.of("NORMALIZED_REQUIREMENT"), List.of());
        put(StageType.IMPACT_ANALYSIS, List.of("NORMALIZED_REQUIREMENT"), List.of());
        put(StageType.DESIGN, List.of("NORMALIZED_REQUIREMENT", "TASK_GRAPH", "THREAT_MODEL"), List.of("IMPACT_ANALYSIS"));
        put(StageType.IMPLEMENTATION, List.of("NORMALIZED_REQUIREMENT", "TASK_GRAPH", "DESIGN"), List.of());
        put(StageType.DOCUMENTATION, List.of("NORMALIZED_REQUIREMENT", "DESIGN"), List.of("IMPACT_ANALYSIS"));
        put(StageType.TESTING, List.of("NORMALIZED_REQUIREMENT", "DESIGN", "CHANGE_SET"), List.of());
        put(StageType.REGRESSION_TESTING, List.of("IMPACT_ANALYSIS", "CHANGE_SET"), List.of("DESIGN"));
        put(StageType.SECURITY_VERIFICATION, List.of("THREAT_MODEL", "DESIGN", "CHANGE_SET"), List.of());
        put(StageType.VALIDATION, List.of("NORMALIZED_REQUIREMENT", "TEST_REPORT", "SECURITY_REPORT", "DOCUMENTATION"),
                List.of("REGRESSION_REPORT"));
        put(StageType.RELEASE, List.of("DESIGN", "READINESS_REPORT"), List.of());
        DECLARED.put(StageType.COMPLIANCE_EVALUATION, new Declared(List.of("VALIDATION_REPORT"), List.of(), true));
        DECLARED.put(StageType.FINAL_SUMMARY, new Declared(List.of(), List.of(), true));
    }

    private StageInputs() {
    }

    private static void put(StageType type, List<String> required, List<String> optional) {
        DECLARED.put(type, new Declared(required, optional, false));
    }

    public static Declared of(StageType type) {
        return DECLARED.getOrDefault(type, new Declared(List.of(), List.of(), false));
    }
}
