package com.agentic.urlshortener.orchestration.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.agentic.urlshortener.common.util.Fingerprints;
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

/** T051: each rule of policy set 1.0.0 yields PASS, FAIL, or NOT_APPLICABLE with evidence (FR-POL-02, NFR-SEC-05). */
@Tag("FR-POL-02")
@Tag("FR-POL-06")
@Tag("NFR-SEC-05")
@Tag("NFR-AUD-01")
class PolicyRulesTest {

    private static PolicyContext.Builder context() {
        return PolicyContext.builder(UUID.randomUUID());
    }

    private static String report(String suite, String probeId, List<String> verifies, boolean passed, List<String> unverified) {
        return "{\"suite\":\"" + suite + "\",\"results\":[{\"probeId\":\"" + probeId + "\",\"description\":\"d\",\"verifies\":"
                + verifies.stream().map(v -> "\"" + v + "\"").toList() + ",\"passed\":" + passed + ",\"evidence\":\"e\",\"durationMillis\":1}],"
                + "\"unverifiedCriteria\":" + unverified.stream().map(v -> "\"" + v + "\"").toList() + ",\"passed\":" + (passed ? 1 : 0)
                + ",\"failed\":" + (passed ? 0 : 1) + ",\"syntheticRecordsCreated\":0,\"syntheticRecordsRemoved\":0}";
    }

    private static void assertOutcome(PolicyRule rule, PolicyContext context, String expected) {
        RuleOutcome outcome = rule.evaluate(context);
        assertThat(outcome.outcome()).as(rule.policyId() + ": " + outcome.evidence()).isEqualTo(expected);
        assertThat(outcome.evidence()).isNotBlank();
    }

    @Test
    void sec001RequiresTheUrlCatalogProbeToPass() {
        UrlValidationProbesRule rule = new UrlValidationProbesRule();
        assertOutcome(rule, context().artifact("SECURITY_REPORT", report("SECURITY", "SEC-URL-CATALOG", List.of(), true, List.of())).build(), "PASS");
        assertOutcome(rule, context().artifact("SECURITY_REPORT", report("SECURITY", "SEC-URL-CATALOG", List.of(), false, List.of())).build(), "FAIL");
        assertOutcome(rule, context().build(), "FAIL");
    }

    @Test
    void sec002FindsSecretPatternsInArtifacts() {
        NoSecretsRule rule = new NoSecretsRule();
        assertOutcome(rule, context().artifact("DOCUMENTATION", "# Doc without secrets").build(), "PASS");
        assertOutcome(rule, context().artifact("DOCUMENTATION", "-----BEGIN RSA PRIVATE KEY-----\nabc").build(), "FAIL");
        assertOutcome(rule, context().artifact("DESIGN", "{\"note\":\"password=hunter2\"}").build(), "FAIL");
        assertOutcome(rule, context().artifact("DESIGN", "{\"key\":\"AKIAABCDEFGHIJKLMNOP\"}").build(), "FAIL");
    }

    @Test
    void sec003RequiresEveryHighThreatToBeVerified() {
        HighThreatsVerifiedRule rule = new HighThreatsVerifiedRule();
        String threats = "{\"capabilities\":[],\"threats\":[{\"id\":\"TH-1\",\"category\":\"SPOOFING\",\"description\":\"d\",\"severity\":\"HIGH\","
                + "\"mitigation\":\"m\",\"verifiedBy\":[\"P-1\"]}]}";
        assertOutcome(rule, context().artifact("THREAT_MODEL", threats)
                .artifact("SECURITY_REPORT", report("SECURITY", "P-1", List.of("TH-1"), true, List.of())).build(), "PASS");
        assertOutcome(rule, context().artifact("THREAT_MODEL", threats)
                .artifact("SECURITY_REPORT", report("SECURITY", "P-1", List.of("TH-1"), false, List.of("TH-1"))).build(), "FAIL");
        assertOutcome(rule, context().artifact("THREAT_MODEL", "{\"capabilities\":[],\"threats\":[]}").build(), "NOT_APPLICABLE");
    }

    @Test
    void prv001RejectsPersonalDataColumns() {
        NoPersonalDataRule rule = new NoPersonalDataRule();
        assertOutcome(rule, context().artifact("DESIGN", "{\"schemaChanges\":[{\"migration\":\"V3\",\"change\":\"short_link.custom_alias BOOLEAN\","
                + "\"compatibility\":\"ADDITIVE\"}]}").build(), "PASS");
        assertOutcome(rule, context().artifact("DESIGN", "{\"schemaChanges\":[{\"migration\":\"V9\",\"change\":\"click_event.client_ip VARCHAR\","
                + "\"compatibility\":\"ADDITIVE\"}]}").build(), "FAIL");
        assertOutcome(rule, context().artifact("DESIGN", "{\"schemaChanges\":[]}").build(), "NOT_APPLICABLE");
    }

    @Test
    void aud001AndAud002CheckTheAuditTrail() {
        assertOutcome(new AuditChainIntactRule(), context().audit(true, "Chain intact: 40 events verified.").build(), "PASS");
        assertOutcome(new AuditChainIntactRule(), context().audit(false, "link broken at 7").build(), "FAIL");
        assertOutcome(new AuditRetentionRule(), context().auditRetentionDays(365).build(), "PASS");
        assertOutcome(new AuditRetentionRule(), context().auditRetentionDays(30).build(), "FAIL");
    }

    @Test
    void lic001AcceptsDualLicensedComponentsAndFailsWithoutAnSbom() {
        ApprovedLicensesRule rule = new ApprovedLicensesRule();
        assertOutcome(rule, context().sbom(List.of(new SbomReader.Component("ch.qos.logback:logback-core", List.of("EPL-2.0", "LGPL-2.1-only")),
                new SbomReader.Component("com.h2database:h2", List.of("MPL-2.0", "EPL 1.0")),
                new SbomReader.Component("org.hdrhistogram:HdrHistogram", List.of("CC0-1.0", "BSD-2-Clause")))).build(), "PASS");
        assertOutcome(rule, context().sbom(List.of(new SbomReader.Component("x:gpl-only", List.of("GPL-3.0-only")))).build(), "FAIL");
        assertOutcome(rule, context().sbom(null).build(), "FAIL");
    }

    @Test
    void lic001PassesForTheBuildsRealSbom() {
        List<SbomReader.Component> sbom = SbomReader.read().orElseThrow();
        assertThat(sbom).hasSizeGreaterThan(50);
        assertOutcome(new ApprovedLicensesRule(), context().sbom(sbom).build(), "PASS");
    }

    @Test
    void tst001AndTst002CheckTheVerificationReports() {
        assertOutcome(new AcceptanceVerifiedRule(), context().artifact("TEST_REPORT", report("ACCEPTANCE", "P", List.of("AC-1"), true, List.of())).build(), "PASS");
        assertOutcome(new AcceptanceVerifiedRule(), context().artifact("TEST_REPORT", report("ACCEPTANCE", "P", List.of("AC-1"), false, List.of("AC-1"))).build(), "FAIL");
        assertOutcome(new RegressionPassedRule(), context().classification("NEW_CAPABILITY").build(), "NOT_APPLICABLE");
        assertOutcome(new RegressionPassedRule(), context().classification("CHANGE_TO_EXISTING")
                .artifact("REGRESSION_REPORT", report("REGRESSION", "R-P1", List.of(), true, List.of())).build(), "PASS");
        assertOutcome(new RegressionPassedRule(), context().classification("CHANGE_TO_EXISTING").build(), "FAIL");
    }

    @Test
    void doc001RequiresGeneratedAndRepositoryDocumentationForApiChanges() {
        String apiDesign = "{\"apiChanges\":[{\"operation\":\"POST /api/v1/links\",\"change\":\"alias\",\"compatibility\":\"BACKWARD_COMPATIBLE\","
                + "\"contractVersion\":\"1.1.0\"}]}";
        assertOutcome(new DocumentationUpdatedRule(), context().artifact("DESIGN", apiDesign)
                .artifact("DOCUMENTATION", "# Doc\n<!-- documentation-check: repositoryDocsUpdated=true; degraded=false -->").build(), "PASS");
        assertOutcome(new DocumentationUpdatedRule(), context().artifact("DESIGN", apiDesign)
                .artifact("DOCUMENTATION", "# Doc\n<!-- documentation-check: repositoryDocsUpdated=false; degraded=false -->").build(), "FAIL");
        assertOutcome(new DocumentationUpdatedRule(), context().artifact("DESIGN", "{\"apiChanges\":[]}").build(), "NOT_APPLICABLE");
    }

    @Test
    void chg001RequiresAValidApprovalBoundToTheCurrentDesign() {
        String design = "{\"materialChange\":true}";
        String fingerprint = Fingerprints.sha256(design);
        ArchitectureApprovalBoundRule rule = new ArchitectureApprovalBoundRule();
        assertOutcome(rule, context().artifact("DESIGN", design)
                .decision("ARCHITECTURE_APPROVAL", "APPROVED", Map.of("DESIGN", fingerprint), true).build(), "PASS");
        assertOutcome(rule, context().artifact("DESIGN", design)
                .decision("ARCHITECTURE_APPROVAL", "APPROVED", Map.of("DESIGN", "0".repeat(64)), true).build(), "FAIL");
        assertOutcome(rule, context().artifact("DESIGN", design).build(), "FAIL");
        assertOutcome(rule, context().artifact("DESIGN", "{\"materialChange\":false}").build(), "NOT_APPLICABLE");
    }

    @Test
    void chg002RequiresACompleteNonDegradedImpactAnalysisForBrownfield() {
        ImpactAnalysisCompleteRule rule = new ImpactAnalysisCompleteRule();
        assertOutcome(rule, context().classification("NEW_CAPABILITY").build(), "NOT_APPLICABLE");
        assertOutcome(rule, context().classification("CHANGE_TO_EXISTING").artifact("IMPACT_ANALYSIS", "{\"degraded\":false}").build(), "PASS");
        assertOutcome(rule, context().classification("CHANGE_TO_EXISTING").artifact("IMPACT_ANALYSIS", "{\"degraded\":true}").build(), "FAIL");
    }

    @Test
    void rel001AndAut001() {
        assertOutcome(new ReleasePlanRule(), context().artifact("DESIGN",
                "{\"releasePlan\":{\"capability\":\"custom-alias\",\"strategy\":\"flag\",\"parameters\":{}},\"rollbackPlan\":\"withdraw\"}").build(), "PASS");
        assertOutcome(new ReleasePlanRule(), context().artifact("DESIGN", "{\"rollbackPlan\":\"\"}").build(), "FAIL");
        assertOutcome(new AutonomyHeadroomRule(), context().attempts(20, 60).build(), "PASS");
        assertOutcome(new AutonomyHeadroomRule(), context().attempts(55, 60).build(), "FAIL");
    }
}
