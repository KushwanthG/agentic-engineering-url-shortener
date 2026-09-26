package com.agentic.urlshortener.orchestration.agent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.agentic.urlshortener.common.util.CanonicalJson;
import com.agentic.urlshortener.support.AgentChain;
import com.agentic.urlshortener.support.ArtifactSchemas;
import com.agentic.urlshortener.support.RequirementFixtures;

import tools.jackson.databind.JsonNode;

/** T129: design with API and schema impact, release and rollback plans, and materiality (FR-ORC-06, FR-RPL-03). */
@Tag("FR-ORC-03")
@Tag("FR-ORC-06")
@Tag("FR-RPL-03")
@Tag("SCN-A")
class DesignAgentTest {

    private static AgentChain designed(String requirement) {
        return AgentChain.analyzed(requirement)
                .then(new DecompositionAgent(AgentChain.CATALOG))
                .then(new ThreatAssessmentAgent(AgentChain.CATALOG))
                .then(new DesignAgent(AgentChain.CATALOG));
    }

    @Test
    void greenfieldDesignIsMaterialBecauseItChangesThePublicApiAndSchema() {
        String design = designed(RequirementFixtures.gf001()).artifact("DESIGN");
        ArtifactSchemas.assertValid("design", design);
        JsonNode json = CanonicalJson.parse(design);

        assertThat(json.path("materialChange").asBoolean()).isTrue();
        assertThat(json.path("materialReasons").toString()).contains("public API").contains("schema");
        assertThat(json.path("apiChanges").get(0).path("contractVersion").asString()).isEqualTo("1.1.0");
        assertThat(json.path("apiChanges").get(0).path("operation").asString()).isEqualTo("POST /api/v1/links");
        assertThat(json.path("schemaChanges").get(0).path("migration").asString()).isEqualTo("V3__custom_alias.sql");
        assertThat(json.path("schemaChanges").get(0).path("compatibility").asString()).isEqualTo("ADDITIVE");
        assertThat(json.path("releasePlan").path("capability").asString()).isEqualTo("custom-alias");
        assertThat(json.path("rollbackPlan").asString()).contains("Withdraw");
        assertThat(json.path("components").findValuesAsString("name")).contains("AliasPolicy");
        assertThat(json.path("decisions").toString()).contains("share the code namespace");
    }

    @Test
    void brownfieldDesignCarriesItsRiskAndRollback() {
        JsonNode json = CanonicalJson.parse(designed(RequirementFixtures.bf001()).artifact("DESIGN"));
        assertThat(json.path("releasePlan").path("capability").asString()).isEqualTo("click-limit");
        assertThat(json.path("schemaChanges").get(0).path("migration").asString()).isEqualTo("V4__click_limit.sql");
        assertThat(json.path("risks").size()).isPositive();
    }
}
