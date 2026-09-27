package com.agentic.urlshortener.orchestration.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.agentic.urlshortener.common.util.CanonicalJson;
import com.agentic.urlshortener.orchestration.config.OrchestrationProperties;
import com.agentic.urlshortener.orchestration.domain.FailureClass;
import com.agentic.urlshortener.support.AgentChain;
import com.agentic.urlshortener.support.ArtifactSchemas;
import com.agentic.urlshortener.support.RequirementFixtures;

import tools.jackson.databind.JsonNode;

/**
 * T084 (FR-ORC-14, FR-REL-03): for BF-001 the impact analysis scans this repository. Catalog seeds
 * are expanded through the reverse-dependency closure, and every component says whether it is a seed
 * or derived, with its dependency chain (review RC-3). Tests, documentation, interfaces, data flows,
 * regression risks, rollout, rollback, and impacted requirements are filled. Without a source tree
 * the agent fails and the catalog-only fallback produces a degraded analysis.
 */
@Tag("FR-ORC-14")
@Tag("FR-REL-03")
@Tag("SCN-B")
class ImpactAnalysisAgentTest {

    private static OrchestrationProperties root(String codebaseRoot) {
        return new OrchestrationProperties(codebaseRoot, 365, Duration.ofHours(24), 3, 8, null, null);
    }

    private static List<String> values(JsonNode array, String field) {
        List<String> values = new ArrayList<>();
        array.forEach(n -> values.add(n.path(field).asString()));
        return values;
    }

    @Test
    void aSourceScanExpandsTheCatalogSeedsThroughTheReverseDependencyClosure() {
        String impact = AgentChain.analyzed(RequirementFixtures.bf001())
                .then(new ImpactAnalysisAgent(AgentChain.CATALOG, root("."))).artifact("IMPACT_ANALYSIS");
        ArtifactSchemas.assertValid("impact-analysis", impact);
        JsonNode json = CanonicalJson.parse(impact);

        assertThat(json.path("method").asString()).isEqualTo("SOURCE_SCAN");
        assertThat(json.path("degraded").asBoolean()).isFalse();
        assertThat(json.path("seeds").toString()).contains("RedirectService", "ClickRecorder", "ShortLink", "LinkCreationService");

        JsonNode redirectService = find(json.path("components"), "RedirectService");
        assertThat(redirectService.path("reason").asString()).startsWith("catalog seed");
        assertThat(redirectService.path("path").asString()).endsWith("shortener/service/RedirectService.java");

        List<JsonNode> derived = new ArrayList<>();
        json.path("components").forEach(c -> {
            if (c.path("reason").asString().startsWith("derived")) {
                derived.add(c);
            }
        });
        assertThat(derived).as("components found by the scan, not listed in the catalog").isNotEmpty();
        JsonNode controller = find(json.path("components"), "RedirectController");
        assertThat(controller.path("reason").asString()).startsWith("derived through the reverse-dependency closure")
                .contains("RedirectController → RedirectService");
        assertThat(json.path("seeds").toString()).doesNotContain("RedirectController");

        assertThat(values(json.path("tests"), "name")).contains("RedirectServiceTest", "LinkCreationServiceTest");
        assertThat(values(json.path("documentation"), "path")).contains("docs/api/links.md");
        assertThat(json.path("interfaces").toString()).contains("POST /api/v1/links").contains("GET /{code}");
        assertThat(json.path("dataFlows").toString()).contains("RedirectController → RedirectService");
        assertThat(values(json.path("regressionRisks"), "id")).contains("RR-CL-2", "RR-CL-4");
        assertThat(json.path("rollout").asString()).contains("release flag");
        assertThat(json.path("rollback").asString()).contains("Withdraw");
        assertThat(json.path("impactedRequirements").toString()).contains("FR-ANL-04", "FR-RED-04", "FR-LNK-11");
    }

    @Test
    void withoutASourceTreeThePrimaryFailsAndTheCatalogFallbackIsDegraded() {
        AgentChain chain = AgentChain.analyzed(RequirementFixtures.bf001());

        StageResult primary = chain.run(new ImpactAnalysisAgent(AgentChain.CATALOG, root("does/not/exist")));
        assertThat(primary).isInstanceOfSatisfying(StageResult.Failed.class, f -> {
            assertThat(f.failureClass()).isEqualTo(FailureClass.PERMANENT);
            assertThat(f.reason()).contains("source tree");
        });

        CatalogImpactAnalysisAgent fallback = new CatalogImpactAnalysisAgent(AgentChain.CATALOG);
        assertThat(fallback.fallback()).isTrue();
        String impact = chain.then(fallback).artifact("IMPACT_ANALYSIS");
        ArtifactSchemas.assertValid("impact-analysis", impact);
        JsonNode json = CanonicalJson.parse(impact);
        assertThat(json.path("method").asString()).isEqualTo("CATALOG_ONLY");
        assertThat(json.path("degraded").asBoolean()).isTrue();
        assertThat(json.path("components")).allSatisfy(c -> assertThat(c.path("reason").asString()).startsWith("catalog component"));
        assertThat(values(json.path("regressionRisks"), "id")).contains("RR-CL-1");
    }

    private static JsonNode find(JsonNode components, String name) {
        for (JsonNode component : components) {
            if (component.path("name").asString().equals(name)) {
                return component;
            }
        }
        throw new AssertionError("no component " + name + " in " + components);
    }
}
