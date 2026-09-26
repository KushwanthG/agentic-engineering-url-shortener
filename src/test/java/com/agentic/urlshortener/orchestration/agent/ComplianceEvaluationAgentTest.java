package com.agentic.urlshortener.orchestration.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.agentic.urlshortener.common.util.CanonicalJson;
import com.agentic.urlshortener.common.util.Fingerprints;
import com.agentic.urlshortener.orchestration.config.OrchestrationProperties;
import com.agentic.urlshortener.orchestration.domain.PolicyExceptionRecord;
import com.agentic.urlshortener.orchestration.policy.PolicyContext;
import com.agentic.urlshortener.orchestration.policy.PolicyEngine;
import com.agentic.urlshortener.orchestration.policy.PolicySetLoader;
import com.agentic.urlshortener.orchestration.policy.ReadinessEvaluator;
import com.agentic.urlshortener.orchestration.policy.RunFacts;
import com.agentic.urlshortener.orchestration.policy.SbomReader;
import com.agentic.urlshortener.orchestration.policy.rules.AcceptanceVerifiedRule;
import com.agentic.urlshortener.orchestration.policy.rules.ApprovedLicensesRule;
import com.agentic.urlshortener.orchestration.policy.rules.ArchitectureApprovalBoundRule;
import com.agentic.urlshortener.orchestration.policy.rules.AuditChainIntactRule;
import com.agentic.urlshortener.orchestration.policy.rules.AuditRetentionRule;
import com.agentic.urlshortener.orchestration.policy.rules.AutonomyHeadroomRule;
import com.agentic.urlshortener.orchestration.policy.rules.DocumentationUpdatedRule;
import com.agentic.urlshortener.orchestration.policy.rules.HighThreatsVerifiedRule;
import com.agentic.urlshortener.orchestration.policy.rules.ImpactAnalysisCompleteRule;
import com.agentic.urlshortener.orchestration.policy.rules.NoPersonalDataRule;
import com.agentic.urlshortener.orchestration.policy.rules.NoSecretsRule;
import com.agentic.urlshortener.orchestration.policy.rules.RegressionPassedRule;
import com.agentic.urlshortener.orchestration.policy.rules.ReleasePlanRule;
import com.agentic.urlshortener.orchestration.policy.rules.UrlValidationProbesRule;
import com.agentic.urlshortener.support.AgentChain;
import com.agentic.urlshortener.support.ArtifactSchemas;
import com.agentic.urlshortener.support.RequirementFixtures;

import tools.jackson.databind.JsonNode;

/** T131: compliance and readiness reports from the pinned policy set; a mandatory FAIL blocks (FR-POL-03, FR-RDY-01). */
@Tag("FR-POL-02")
@Tag("FR-POL-03")
@Tag("FR-POL-06")
@Tag("FR-RDY-01")
@Tag("SCN-A")
class ComplianceEvaluationAgentTest {

    private static final String TESTS = """
            {"suite":"ACCEPTANCE","results":[{"probeId":"CA-P1","description":"d","verifies":["AC-1","AC-2","AC-3","AC-4","AC-5","AC-6"],
             "passed":true,"evidence":"e","durationMillis":1}],"unverifiedCriteria":[],"passed":1,"failed":0,
             "syntheticRecordsCreated":1,"syntheticRecordsRemoved":1}""";
    private static final String SECURITY = """
            {"suite":"SECURITY","results":[
              {"probeId":"SEC-URL-CATALOG","description":"d","verifies":["TH-URL-1"],"passed":true,"evidence":"e","durationMillis":1},
              {"probeId":"CA-SEC-RESERVED","description":"d","verifies":["TH-CA-1"],"passed":true,"evidence":"e","durationMillis":1},
              {"probeId":"CA-P2","description":"d","verifies":["TH-CA-2"],"passed":true,"evidence":"e","durationMillis":1}],
             "unverifiedCriteria":[],"passed":3,"failed":0,"syntheticRecordsCreated":2,"syntheticRecordsRemoved":2}""";

    private static final PolicyEngine ENGINE = new PolicyEngine(List.of(new UrlValidationProbesRule(), new NoSecretsRule(),
            new HighThreatsVerifiedRule(), new NoPersonalDataRule(), new AuditChainIntactRule(), new AuditRetentionRule(),
            new ApprovedLicensesRule(), new AcceptanceVerifiedRule(), new RegressionPassedRule(), new DocumentationUpdatedRule(),
            new ArchitectureApprovalBoundRule(), new ImpactAnalysisCompleteRule(), new ReleasePlanRule(), new AutonomyHeadroomRule()));

    /** Facts of a run whose architecture approval is bound to {@code designFingerprint}. */
    private static RunFacts facts(String designFingerprint) {
        return new RunFacts() {
            @Override
            public PolicyContext.Builder forRun(UUID runId) {
                return PolicyContext.builder(runId).classification("NEW_CAPABILITY").audit(true, "Chain intact: 50 events verified.")
                        .auditRetentionDays(365).attempts(16, 60).sbom(SbomReader.read().orElse(null))
                        .decision("ARCHITECTURE_APPROVAL", "APPROVED", Map.of("DESIGN", designFingerprint), true);
            }

            @Override
            public List<PolicyExceptionRecord> exceptions(UUID runId) {
                return List.of();
            }
        };
    }

    private static AgentChain validated() {
        return AgentChain.analyzed(RequirementFixtures.gf001())
                .then(new DecompositionAgent(AgentChain.CATALOG))
                .then(new ThreatAssessmentAgent(AgentChain.CATALOG))
                .then(new DesignAgent(AgentChain.CATALOG))
                .then(new DocumentationAgent(AgentChain.CATALOG,
                        new OrchestrationProperties(".", 365, null, 3, 8, null, null)))
                .put("TEST_REPORT", TESTS)
                .put("SECURITY_REPORT", SECURITY)
                .then(new ValidationAgent());
    }

    private static ComplianceEvaluationAgent agent(RunFacts facts) {
        return new ComplianceEvaluationAgent(ENGINE, new PolicySetLoader(), new ReadinessEvaluator(), facts,
                Clock.fixed(Instant.parse("2026-09-26T10:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void aCompliantRunIsReady() {
        AgentChain chain = validated();
        String design = chain.artifact("DESIGN");
        chain.then(agent(facts(Fingerprints.sha256(design))));

        ArtifactSchemas.assertValid("compliance-report", chain.artifact("COMPLIANCE_REPORT"));
        ArtifactSchemas.assertValid("readiness-report", chain.artifact("READINESS_REPORT"));
        JsonNode compliance = CanonicalJson.parse(chain.artifact("COMPLIANCE_REPORT"));
        assertThat(compliance.path("policySetVersion").asString()).isEqualTo("1.0.0");
        assertThat(compliance.path("blocked").asBoolean()).isFalse();
        assertThat(compliance.path("evaluations").size()).isEqualTo(14);
        assertThat(CanonicalJson.parse(chain.artifact("READINESS_REPORT")).path("outcome").asString()).isEqualTo("READY");
    }

    @Test
    void aMandatoryFailureBlocksWithTheFailingPolicies() {
        AgentChain chain = validated();
        StageResult result = chain.run(agent(facts("0".repeat(64))));
        assertThat(result).isInstanceOfSatisfying(StageResult.PolicyBlocked.class, blocked -> {
            assertThat(blocked.blockingPolicies()).containsExactly("CHG-001");
            assertThat(blocked.artifacts()).extracting(ArtifactDraft::type).containsExactly("COMPLIANCE_REPORT", "READINESS_REPORT");
            ArtifactSchemas.assertValid("compliance-report", blocked.artifacts().get(0).content());
            assertThat(CanonicalJson.parse(blocked.artifacts().get(1).content()).path("outcome").asString()).isEqualTo("NOT_READY");
        });
    }
}
