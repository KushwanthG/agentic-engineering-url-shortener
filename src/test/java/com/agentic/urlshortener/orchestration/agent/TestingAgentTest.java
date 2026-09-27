package com.agentic.urlshortener.orchestration.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.agentic.urlshortener.common.util.CanonicalJson;
import com.agentic.urlshortener.orchestration.agent.probes.ProbeRegistry;
import com.agentic.urlshortener.orchestration.domain.FailureClass;
import com.agentic.urlshortener.orchestration.port.ApplicationPlanePort;
import com.agentic.urlshortener.orchestration.port.DeliveryStatus;
import com.agentic.urlshortener.shortener.domain.Capability;
import com.agentic.urlshortener.shortener.repository.ShortLinkRepository;
import com.agentic.urlshortener.shortener.service.CapabilityService;
import com.agentic.urlshortener.support.AgentChain;
import com.agentic.urlshortener.support.ArtifactSchemas;
import com.agentic.urlshortener.support.FakeApplicationPlanePort;
import com.agentic.urlshortener.support.IntegrationTest;
import com.agentic.urlshortener.support.RequirementFixtures;

import tools.jackson.databind.JsonNode;

/** T049: acceptance probes run against the live service in preview mode; synthetic data is always removed (FR-ORC-16). */
@IntegrationTest
@Tag("FR-ORC-16")
@Tag("FR-REL-05")
@Tag("SCN-A")
class TestingAgentTest {

    @Autowired
    private ApplicationPlanePort port;

    @Autowired
    private ShortLinkRepository links;

    @Autowired
    private CapabilityService capabilities;

    private final TestingAgent agent = new TestingAgent(new ProbeRegistry());

    private AgentChain implemented(String requirement) {
        FakeApplicationPlanePort delivered = new FakeApplicationPlanePort();
        delivered.delivery = (c, m, f) -> new DeliveryStatus(List.of());
        return AgentChain.analyzed(requirement)
                .then(new DecompositionAgent(AgentChain.CATALOG))
                .then(new ThreatAssessmentAgent(AgentChain.CATALOG))
                .then(new DesignAgent(AgentChain.CATALOG))
                .withPort(delivered)
                .then(new ImplementationAgent(AgentChain.CATALOG))
                .withPort(port);
    }

    @Test
    void everyAcceptanceCriterionIsVerifiedAndSyntheticDataIsRemoved() {
        AgentChain chain = implemented(RequirementFixtures.gf001()).then(agent);
        String report = chain.artifact("TEST_REPORT");
        ArtifactSchemas.assertValid("verification-report", report);
        JsonNode json = CanonicalJson.parse(report);

        assertThat(json.path("suite").asString()).isEqualTo("ACCEPTANCE");
        assertThat(json.path("unverifiedCriteria").size()).isZero();
        assertThat(json.path("failed").asInt()).isZero();
        assertThat(json.path("results").findValuesAsString("probeId")).containsExactly("CA-P1", "CA-P2", "CA-P3", "CA-P4", "CA-P5", "CA-P6");
        List<String> verified = new ArrayList<>();
        json.path("results").forEach(r -> r.path("verifies").forEach(v -> verified.add(v.asString())));
        assertThat(verified).contains("AC-1", "AC-2", "AC-3", "AC-4", "AC-5", "AC-6");
        assertThat(json.path("syntheticRecordsCreated").asInt()).isPositive()
                .isEqualTo(json.path("syntheticRecordsRemoved").asInt());
        assertThat(links.findAll()).noneMatch(l -> chain.runId().equals(l.getSyntheticRunId()));
        assertThat(capabilities.isReleased(Capability.CUSTOM_ALIAS)).as("preview must not leak").isFalse();
    }

    @Test
    void aCriterionWithoutAPassingProbeFailsTheStage() {
        List<String> criteria = new ArrayList<>(RequirementFixtures.GF_001_CRITERIA);
        criteria.add("Given an alias, when a link is created, then the moon turns blue.");
        AgentChain chain = implemented(RequirementFixtures.document("GF-001", "Custom aliases for short links.", "NEW_CAPABILITY",
                "As an API consumer, I want to optionally choose a custom alias when I create a short link.", criteria, List.of()));
        StageResult result = chain.run(agent);
        assertThat(result).isInstanceOfSatisfying(StageResult.Failed.class, f -> {
            assertThat(f.failureClass()).isEqualTo(FailureClass.PERMANENT);
            assertThat(f.reason()).contains("AC-7");
        });
        assertThat(links.findAll()).noneMatch(l -> chain.runId().equals(l.getSyntheticRunId()));
    }
}
