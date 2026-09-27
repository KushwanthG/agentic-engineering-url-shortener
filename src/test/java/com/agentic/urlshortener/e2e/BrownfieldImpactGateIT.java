package com.agentic.urlshortener.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.agentic.urlshortener.common.util.CanonicalJson;
import com.agentic.urlshortener.orchestration.agent.DecompositionAgent;
import com.agentic.urlshortener.orchestration.agent.ImpactAnalysisAgent;
import com.agentic.urlshortener.orchestration.agent.ThreatAssessmentAgent;
import com.agentic.urlshortener.orchestration.config.OrchestrationProperties;
import com.agentic.urlshortener.support.AgentChain;
import com.agentic.urlshortener.support.ArtifactSchemas;
import com.agentic.urlshortener.support.EvidenceExporter;
import com.agentic.urlshortener.support.RequirementFixtures;

import tools.jackson.databind.JsonNode;

/**
 * T086 — brownfield impact analysis gate (repository process, guide §19): the implemented
 * IMPACT_ANALYSIS runs for BF-001 against this repository. The committed analysis
 * ({@code docs/scenarios/scn-b-impact-analysis.md}) was written from this output before any click-limit
 * code existed; afterwards the test keeps checking that the analysis still covers the brownfield
 * essentials, and exports the output as SCN-B evidence E-B1.
 */
@Tag("FR-ORC-14")
@Tag("SCN-B")
class BrownfieldImpactGateIT {

    @Test
    void theClickLimitImpactAnalysisOfThisRepositoryCoversTheBrownfieldEssentials() {
        OrchestrationProperties repository = new OrchestrationProperties(".", 365, Duration.ofHours(24), 3, 8, null, null);
        AgentChain chain = AgentChain.analyzed(RequirementFixtures.bf001())
                .then(new DecompositionAgent(AgentChain.CATALOG))
                .then(new ThreatAssessmentAgent(AgentChain.CATALOG))
                .then(new ImpactAnalysisAgent(AgentChain.CATALOG, repository));
        String impact = chain.artifact("IMPACT_ANALYSIS");
        ArtifactSchemas.assertValid("impact-analysis", impact);
        JsonNode json = CanonicalJson.parse(impact);

        assertThat(json.path("method").asString()).isEqualTo("SOURCE_SCAN");
        assertThat(json.path("components").toString())
                .contains("RedirectService", "ClickRecorder", "ShortLinkRepository", "LinkCreationService", "RedirectController",
                        "LinkController");
        assertThat(json.path("tests").size()).isPositive();
        assertThat(json.path("documentation").toString()).contains("docs/api/links.md");
        assertThat(json.path("regressionRisks").toString()).contains("lost updates", "fail closed");
        assertThat(json.path("rollback").asString()).contains("stored limits stay enforced");

        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("purpose", "IMPACT_ANALYSIS of BF-001 against this repository (brownfield gate, guide section 19)");
        evidence.put("taskGraph", CanonicalJson.parse(chain.artifact("TASK_GRAPH")));
        evidence.put("threatModel", CanonicalJson.parse(chain.artifact("THREAT_MODEL")));
        evidence.put("impactAnalysis", json);
        new EvidenceExporter("scn-b", BrownfieldImpactGateIT.class, false).json("E-B1-impact-analysis-gate", evidence);
    }
}
