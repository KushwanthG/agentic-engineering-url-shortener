package com.agentic.urlshortener.orchestration.agent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.agentic.urlshortener.common.util.CanonicalJson;
import com.agentic.urlshortener.orchestration.config.OrchestrationProperties;
import com.agentic.urlshortener.orchestration.domain.FailureClass;
import com.agentic.urlshortener.support.AgentChain;
import com.agentic.urlshortener.support.ArtifactSchemas;
import com.agentic.urlshortener.support.RequirementFixtures;

import tools.jackson.databind.JsonNode;

/** T050: the validation join consolidates every verification branch and the documentation (FR-ORC-05). */
@Tag("FR-ORC-05")
@Tag("SCN-A")
class ValidationAgentTest {

    private static final String PASSING_TESTS = """
            {"suite":"ACCEPTANCE","results":[
              {"probeId":"CA-P1","description":"d","verifies":["AC-1"],"passed":true,"evidence":"e","durationMillis":1},
              {"probeId":"CA-P2","description":"d","verifies":["AC-2"],"passed":true,"evidence":"e","durationMillis":1},
              {"probeId":"CA-P3","description":"d","verifies":["AC-3"],"passed":true,"evidence":"e","durationMillis":1},
              {"probeId":"CA-P4","description":"d","verifies":["AC-4"],"passed":true,"evidence":"e","durationMillis":1},
              {"probeId":"CA-P5","description":"d","verifies":["AC-5"],"passed":true,"evidence":"e","durationMillis":1},
              {"probeId":"CA-P6","description":"d","verifies":["AC-6"],"passed":true,"evidence":"e","durationMillis":1}],
             "unverifiedCriteria":[],"passed":6,"failed":0,"syntheticRecordsCreated":5,"syntheticRecordsRemoved":5}""";

    private static final String PASSING_SECURITY = """
            {"suite":"SECURITY","results":[
              {"probeId":"SEC-URL-CATALOG","description":"d","verifies":["TH-URL-1"],"passed":true,"evidence":"e","durationMillis":1}],
             "unverifiedCriteria":[],"passed":1,"failed":0,"syntheticRecordsCreated":0,"syntheticRecordsRemoved":0}""";

    private static AgentChain verified(String testReport) {
        return AgentChain.analyzed(RequirementFixtures.gf001())
                .then(new DecompositionAgent(AgentChain.CATALOG))
                .then(new ThreatAssessmentAgent(AgentChain.CATALOG))
                .then(new DesignAgent(AgentChain.CATALOG))
                .then(new DocumentationAgent(AgentChain.CATALOG, new OrchestrationProperties(".", 365, null, 3, 8, null, null)))
                .put("TEST_REPORT", testReport)
                .put("SECURITY_REPORT", PASSING_SECURITY);
    }

    @Test
    void allBranchesPassingYieldsAPassedValidationReport() {
        String report = verified(PASSING_TESTS).then(new ValidationAgent()).artifact("VALIDATION_REPORT");
        ArtifactSchemas.assertValid("validation-report", report);
        JsonNode json = CanonicalJson.parse(report);
        assertThat(json.path("passed").asBoolean()).isTrue();
        assertThat(json.path("criteria").size()).isEqualTo(6);
        assertThat(json.path("criteria").get(0).path("verifiedBy").get(0).asString()).isEqualTo("CA-P1");
        assertThat(json.path("suites").findValuesAsString("suite")).containsExactly("ACCEPTANCE", "SECURITY");
        assertThat(json.path("documentation").path("generated").asBoolean()).isTrue();
        assertThat(json.path("documentation").path("repositoryDocsUpdated").asBoolean()).isTrue();
        assertThat(json.path("degradedStages").size()).isZero();
    }

    @Test
    void aFailedBranchFailsValidation() {
        String failing = PASSING_TESTS.replace("\"failed\":0", "\"failed\":1").replace("\"verifies\":[\"AC-6\"],\"passed\":true",
                "\"verifies\":[\"AC-6\"],\"passed\":false");
        StageResult result = verified(failing).run(new ValidationAgent());
        assertThat(result).isInstanceOfSatisfying(StageResult.Failed.class, f -> {
            assertThat(f.failureClass()).isEqualTo(FailureClass.PERMANENT);
            assertThat(f.reason()).contains("AC-6");
        });
    }
}
