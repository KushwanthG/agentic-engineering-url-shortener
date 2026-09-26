package com.agentic.urlshortener.orchestration.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.agentic.urlshortener.orchestration.domain.PolicyExceptionRecord;
import com.agentic.urlshortener.orchestration.policy.PolicyContext;
import com.agentic.urlshortener.orchestration.policy.RunFacts;
import com.agentic.urlshortener.support.AgentChain;
import com.agentic.urlshortener.support.RequirementFixtures;

/** T053: the final engineering summary covers plan, artifacts, decisions, policies, validation, risks, and metrics (FR-ORC-17). */
@Tag("FR-ORC-17")
@Tag("SCN-A")
class FinalSummaryAgentTest {

    private static final RunFacts FACTS = new RunFacts() {
        @Override
        public PolicyContext.Builder forRun(UUID runId) {
            return PolicyContext.builder(runId).attempts(16, 60);
        }

        @Override
        public List<PolicyExceptionRecord> exceptions(UUID runId) {
            return List.of();
        }

        @Override
        public List<DecisionSummary> decisions(UUID runId) {
            return List.of(new DecisionSummary("ARCHITECTURE_APPROVAL", "GATE", "APPROVED", "bob", "APPROVER",
                    "design reviewed (simulated human input)", Instant.parse("2026-09-26T10:00:00Z")));
        }
    };

    @Test
    void summaryCoversEveryRequiredSection() {
        String summary = AgentChain.analyzed(RequirementFixtures.gf001())
                .then(new DecompositionAgent(AgentChain.CATALOG))
                .then(new ThreatAssessmentAgent(AgentChain.CATALOG))
                .then(new DesignAgent(AgentChain.CATALOG))
                .put("COMPLIANCE_REPORT", "{\"policySetVersion\":\"1.0.0\",\"evaluations\":[{\"policyId\":\"SEC-001\",\"severity\":\"MANDATORY\","
                        + "\"outcome\":\"PASS\",\"evidence\":\"catalog rejected\",\"exceptionId\":null,\"simulated\":false}],"
                        + "\"blocked\":false,\"blockingPolicies\":[]}")
                .put("VALIDATION_REPORT", "{\"passed\":true,\"criteria\":[{\"id\":\"AC-1\",\"verifiedBy\":[\"CA-P1\"],\"passed\":true}],"
                        + "\"suites\":[],\"documentation\":{\"generated\":true,\"repositoryDocsUpdated\":true,\"evidence\":\"e\"},\"degradedStages\":[]}")
                .put("READINESS_REPORT", "{\"outcome\":\"READY\",\"reasons\":[\"all good\"],\"limitations\":[],\"evaluatedAt\":\"x\"}")
                .then(new FinalSummaryAgent(FACTS))
                .artifact("FINAL_SUMMARY");

        assertThat(summary).contains("# Final engineering summary", "## Plan and rationale", "## Artifacts",
                "## Decisions and approvals", "## Policy outcomes", "## Validation", "## Risks", "## Assumptions",
                "## Limitations", "## Metrics", "## Outcome");
        assertThat(summary).contains("custom-alias").contains("bob").contains("SEC-001").contains("READY").contains("16 of 60");
        assertThat(summary).contains("simulated human input");
    }
}
