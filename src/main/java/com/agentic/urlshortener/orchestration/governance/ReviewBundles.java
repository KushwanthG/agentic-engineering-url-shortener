package com.agentic.urlshortener.orchestration.governance;

import java.util.List;
import java.util.Map;

import com.agentic.urlshortener.orchestration.domain.AwaitingType;
import com.agentic.urlshortener.orchestration.domain.StageType;

/** The artifacts a human reviews for a pending decision; approval decisions are bound to their fingerprints (FR-GOV-05). */
public final class ReviewBundles {

    private static final Map<StageType, List<String>> GATES = Map.of(
            StageType.ARCHITECTURE_APPROVAL, List.of("DESIGN", "THREAT_MODEL", "IMPACT_ANALYSIS"),
            StageType.RELEASE_APPROVAL, List.of("READINESS_REPORT", "VALIDATION_REPORT", "COMPLIANCE_REPORT"));

    private ReviewBundles() {
    }

    /** Artifact types to review for a stage awaiting {@code awaiting}. */
    public static List<String> of(StageType stage, AwaitingType awaiting) {
        if (awaiting == AwaitingType.CLARIFICATION) {
            return List.of("CLARIFICATION_REQUEST", "NORMALIZED_REQUIREMENT");
        }
        if (awaiting == AwaitingType.POLICY_EXCEPTION) {
            return List.of("COMPLIANCE_REPORT", "READINESS_REPORT");
        }
        return GATES.getOrDefault(stage, List.of());
    }

    /** Whether {@code stage} is an approval gate decided through the gate-decision endpoint. */
    public static boolean isApprovalGate(StageType stage) {
        return GATES.containsKey(stage);
    }
}
