package com.agentic.urlshortener.orchestration.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.agentic.urlshortener.common.util.CanonicalJson;
import com.agentic.urlshortener.orchestration.agent.probes.ProbeRegistry;
import com.agentic.urlshortener.orchestration.domain.FailureClass;
import com.agentic.urlshortener.orchestration.port.ApplicationPlanePort;
import com.agentic.urlshortener.orchestration.port.CapabilityState;
import com.agentic.urlshortener.orchestration.port.DeliveryStatus;
import com.agentic.urlshortener.orchestration.port.LinkSnapshot;
import com.agentic.urlshortener.orchestration.port.ProbeResponse;
import com.agentic.urlshortener.orchestration.port.SyntheticLinkSpec;
import com.agentic.urlshortener.shortener.repository.ShortLinkRepository;
import com.agentic.urlshortener.support.AgentChain;
import com.agentic.urlshortener.support.ArtifactSchemas;
import com.agentic.urlshortener.support.IntegrationTest;
import com.agentic.urlshortener.support.RequirementFixtures;

import tools.jackson.databind.JsonNode;

/**
 * T085 (FR-ORC-14, NFR-CHG-02): REGRESSION_TESTING runs the baseline probes R-P1..R-P6 against the
 * live service with the new capability enabled for the calls only (preview): creation, redirect,
 * expiry, links without a limit stay unlimited, click statistics, idempotent replay. Any failure fails
 * the stage (verification never degrades), and synthetic data is removed.
 */
@IntegrationTest
@Tag("FR-ORC-14")
@Tag("NFR-CHG-02")
@Tag("SCN-B")
class RegressionTestingAgentTest {

    @Autowired private ApplicationPlanePort port;
    @Autowired private ShortLinkRepository links;

    private final RegressionTestingAgent agent = new RegressionTestingAgent(new ProbeRegistry(), AgentChain.CATALOG);

    private static AgentChain designed() {
        return AgentChain.analyzed(RequirementFixtures.bf001())
                .then(new DecompositionAgent(AgentChain.CATALOG))
                .then(new ThreatAssessmentAgent(AgentChain.CATALOG))
                .then(new DesignAgent(AgentChain.CATALOG));
    }

    @Test
    void theBaselineStillBehavesWithTheNewCapabilityEnabled() {
        AgentChain chain = designed().withPort(port).then(agent);
        String report = chain.artifact("REGRESSION_REPORT");
        ArtifactSchemas.assertValid("verification-report", report);
        JsonNode json = CanonicalJson.parse(report);

        assertThat(json.path("suite").asString()).isEqualTo("REGRESSION");
        List<String> probes = new ArrayList<>();
        json.path("results").forEach(r -> {
            probes.add(r.path("probeId").asString());
            assertThat(r.path("passed").asBoolean()).as("%s: %s", r.path("probeId"), r.path("evidence")).isTrue();
        });
        assertThat(probes).containsExactly("R-P1", "R-P2", "R-P3", "R-P4", "R-P5", "R-P6");
        assertThat(json.path("failed").asInt()).isZero();
        assertThat(json.path("syntheticRecordsCreated").asInt()).isPositive();
        assertThat(links.findAll()).noneMatch(l -> chain.runId().equals(l.getSyntheticRunId()));
    }

    @Test
    void aRegressionFailsTheStageWithoutFallback() {
        StageResult result = designed().withPort(new BrokenRedirects(port)).run(agent);

        assertThat(result).isInstanceOfSatisfying(StageResult.Failed.class, f -> {
            assertThat(f.failureClass()).isEqualTo(FailureClass.PERMANENT);
            assertThat(f.reason()).contains("regression").contains("R-P2");
        });
        assertThat(agent.fallback()).isFalse();
    }

    /** The real port, except that every redirect is observed as not found. */
    private record BrokenRedirects(ApplicationPlanePort real) implements ApplicationPlanePort {
        @Override public Optional<LinkSnapshot> findLink(String code) { return real.findLink(code); }
        @Override public ProbeResponse resolve(String code) { return ProbeResponse.notFound(); }
        @Override public ProbeResponse createSyntheticLink(UUID runId, SyntheticLinkSpec spec) { return real.createSyntheticLink(runId, spec); }
        @Override public int deleteSyntheticLinks(UUID runId) { return real.deleteSyntheticLinks(runId); }
        @Override public CapabilityState capability(String capabilityId) { return real.capability(capabilityId); }
        @Override public DeliveryStatus deliveryStatus(String capabilityId, String migration, List<String> fields) {
            return real.deliveryStatus(capabilityId, migration, fields);
        }
        @Override public <T> T withPreview(String capabilityId, Map<String, Object> parameters, Supplier<T> action) {
            return real.withPreview(capabilityId, parameters, action);
        }
        @Override public CapabilityState setRelease(String capabilityId, boolean released, Map<String, Object> parameters, UUID runId,
                String reason) {
            return real.setRelease(capabilityId, released, parameters, runId, reason);
        }
    }
}
