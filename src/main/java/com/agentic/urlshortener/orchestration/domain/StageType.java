package com.agentic.urlshortener.orchestration.domain;

import java.util.List;

import com.agentic.urlshortener.common.security.Role;

/**
 * The stage catalog (FR-ORC-03, plan.md §3). Gates are decided by humans and have no agent; every other
 * stage is executed by an agent and must produce its declared output artifact types (exit criterion).
 */
public enum StageType {
    REQUIREMENT_INGESTION(List.of("REQUIREMENT")),
    REQUIREMENT_ANALYSIS(List.of("NORMALIZED_REQUIREMENT")),
    CLARIFICATION(AwaitingType.CLARIFICATION, Role.APPROVER),
    DECOMPOSITION(List.of("TASK_GRAPH")),
    IMPACT_ANALYSIS(List.of("IMPACT_ANALYSIS")),
    THREAT_ASSESSMENT(List.of("THREAT_MODEL")),
    DESIGN(List.of("DESIGN")),
    ARCHITECTURE_APPROVAL(AwaitingType.APPROVAL, Role.APPROVER),
    CHANGE_APPROVAL(AwaitingType.CHANGE_APPROVAL, Role.APPROVER),
    IMPLEMENTATION(List.of("CHANGE_SET")),
    TESTING(List.of("TEST_REPORT"), true),
    REGRESSION_TESTING(List.of("REGRESSION_REPORT"), true),
    SECURITY_VERIFICATION(List.of("SECURITY_REPORT"), true),
    DOCUMENTATION(List.of("DOCUMENTATION")),
    VALIDATION(List.of("VALIDATION_REPORT")),
    COMPLIANCE_EVALUATION(List.of("COMPLIANCE_REPORT", "READINESS_REPORT")),
    RELEASE_APPROVAL(AwaitingType.APPROVAL, Role.RELEASE_OWNER),
    RELEASE(List.of("RELEASE_RECORD"), true),
    FINAL_SUMMARY(List.of("FINAL_SUMMARY"));

    private final List<String> outputArtifactTypes;
    private final boolean sideEffects;
    private final AwaitingType gateAwaiting;
    private final Role requiredRole;

    StageType(List<String> outputArtifactTypes) {
        this(outputArtifactTypes, false);
    }

    StageType(List<String> outputArtifactTypes, boolean sideEffects) {
        this.outputArtifactTypes = outputArtifactTypes;
        this.sideEffects = sideEffects;
        this.gateAwaiting = null;
        this.requiredRole = null;
    }

    StageType(AwaitingType gateAwaiting, Role requiredRole) {
        this.outputArtifactTypes = List.of();
        this.sideEffects = false;
        this.gateAwaiting = gateAwaiting;
        this.requiredRole = requiredRole;
    }

    public boolean isGate() {
        return gateAwaiting != null;
    }

    /** What a gate waits for when it opens; null for agent stages. */
    public AwaitingType gateAwaiting() {
        return gateAwaiting;
    }

    /** The role a human needs to decide this gate; null for agent stages. */
    public Role requiredRole() {
        return requiredRole;
    }

    /** Stages that touch the live application plane (synthetic data or release state) and declare compensation. */
    public boolean hasSideEffects() {
        return sideEffects;
    }

    public List<String> outputArtifactTypes() {
        return outputArtifactTypes;
    }
}
