package com.agentic.urlshortener.orchestration.policy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.agentic.urlshortener.common.util.CanonicalJson;
import com.agentic.urlshortener.common.util.Fingerprints;

import tools.jackson.databind.JsonNode;

/**
 * The facts a policy evaluation reads: the run's current artifacts, its classification, valid
 * decisions, audit-chain verification, configured retention, autonomy usage, and the SBOM
 * ({@code null} when missing).
 */
public record PolicyContext(
        UUID runId,
        String classification,
        Map<String, String> artifacts,
        List<DecisionFact> decisions,
        boolean auditValid,
        String auditEvidence,
        int auditRetentionDays,
        int attemptsUsed,
        int maxAttempts,
        List<SbomReader.Component> sbom,
        Set<String> simulatedPolicyFailures) {

    /** A valid decision with the fingerprints it is bound to. */
    public record DecisionFact(String stageKey, String outcome, Map<String, String> boundFingerprints) {
    }

    public Optional<String> artifact(String type) {
        return Optional.ofNullable(artifacts.get(type));
    }

    public Optional<JsonNode> json(String type) {
        return artifact(type).map(CanonicalJson::parse);
    }

    public Optional<String> fingerprint(String type) {
        return artifact(type).map(Fingerprints::sha256);
    }

    public boolean changesExistingBehavior() {
        return "CHANGE_TO_EXISTING".equals(classification);
    }

    public static Builder builder(UUID runId) {
        return new Builder(runId);
    }

    /** Assembles a context; defaults describe an empty run. */
    public static final class Builder {

        private final UUID runId;
        private String classification = "NEW_CAPABILITY";
        private final Map<String, String> artifacts = new LinkedHashMap<>();
        private final List<DecisionFact> decisions = new ArrayList<>();
        private boolean auditValid = true;
        private String auditEvidence = "not verified";
        private int auditRetentionDays = 365;
        private int attemptsUsed;
        private int maxAttempts = 60;
        private List<SbomReader.Component> sbom = List.of();
        private final Set<String> simulatedPolicyFailures = new LinkedHashSet<>();

        private Builder(UUID runId) {
            this.runId = runId;
        }

        public Builder classification(String value) {
            classification = value;
            return this;
        }

        public Builder artifact(String type, String content) {
            artifacts.put(type, content);
            return this;
        }

        /** Adds a decision; invalid decisions are not facts and are ignored. */
        public Builder decision(String stageKey, String outcome, Map<String, String> boundFingerprints, boolean valid) {
            if (valid) {
                decisions.add(new DecisionFact(stageKey, outcome, Map.copyOf(boundFingerprints)));
            }
            return this;
        }

        public Builder audit(boolean valid, String evidence) {
            auditValid = valid;
            auditEvidence = evidence;
            return this;
        }

        public Builder auditRetentionDays(int days) {
            auditRetentionDays = days;
            return this;
        }

        public Builder attempts(int used, int max) {
            attemptsUsed = used;
            maxAttempts = max;
            return this;
        }

        public Builder sbom(List<SbomReader.Component> components) {
            sbom = components;
            return this;
        }

        /** A policy forced to FAIL by fault injection (FR-REL-11); its evaluation is flagged simulated. */
        public Builder simulatedPolicyFailure(String policyId) {
            simulatedPolicyFailures.add(policyId);
            return this;
        }

        public PolicyContext build() {
            return new PolicyContext(runId, classification, Map.copyOf(artifacts), List.copyOf(decisions), auditValid, auditEvidence,
                    auditRetentionDays, attemptsUsed, maxAttempts, sbom, Set.copyOf(simulatedPolicyFailures));
        }
    }
}
