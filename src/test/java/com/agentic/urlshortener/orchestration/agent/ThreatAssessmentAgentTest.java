package com.agentic.urlshortener.orchestration.agent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.agentic.urlshortener.common.util.CanonicalJson;
import com.agentic.urlshortener.support.AgentChain;
import com.agentic.urlshortener.support.ArtifactSchemas;
import com.agentic.urlshortener.support.RequirementFixtures;

import tools.jackson.databind.JsonNode;

/** T048: STRIDE threats with mitigations; every HIGH threat names at least one verification probe (NFR-SEC-01). */
@Tag("FR-ORC-03")
@Tag("NFR-SEC-01")
@Tag("SCN-A")
class ThreatAssessmentAgentTest {

    private final ThreatAssessmentAgent agent = new ThreatAssessmentAgent(AgentChain.CATALOG);

    @Test
    void greenfieldThreatModelCoversAliasSpoofingAndTakeover() {
        String model = AgentChain.analyzed(RequirementFixtures.gf001()).then(agent).artifact("THREAT_MODEL");
        ArtifactSchemas.assertValid("threat-model", model);
        JsonNode threats = CanonicalJson.parse(model).path("threats");

        assertThat(threats.findValuesAsString("id")).contains("TH-CA-1", "TH-CA-2", "TH-URL-1");
        for (JsonNode threat : threats) {
            if (threat.path("severity").asString().equals("HIGH")) {
                assertThat(threat.path("verifiedBy").size()).as(threat.path("id").asString()).isPositive();
            }
        }
    }

    @Test
    void baselineThreatsApplyEvenWithoutScenarioThreats() {
        String model = AgentChain.analyzed(RequirementFixtures.bf001()).then(agent).artifact("THREAT_MODEL");
        ArtifactSchemas.assertValid("threat-model", model);
        assertThat(CanonicalJson.parse(model).path("threats").findValuesAsString("id")).contains("TH-CL-1", "TH-URL-1");
    }
}
