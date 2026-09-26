package com.agentic.urlshortener.orchestration.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.agentic.urlshortener.common.util.CanonicalJson;
import com.agentic.urlshortener.orchestration.agent.probes.ProbeRegistry;
import com.agentic.urlshortener.orchestration.port.ApplicationPlanePort;
import com.agentic.urlshortener.orchestration.port.DeliveryStatus;
import com.agentic.urlshortener.shortener.repository.ShortLinkRepository;
import com.agentic.urlshortener.support.AgentChain;
import com.agentic.urlshortener.support.ArtifactSchemas;
import com.agentic.urlshortener.support.FakeApplicationPlanePort;
import com.agentic.urlshortener.support.IntegrationTest;
import com.agentic.urlshortener.support.RequirementFixtures;

import tools.jackson.databind.JsonNode;

/** T049: every HIGH threat is verified by a passing security probe; the malicious-URL catalog is rejected (NFR-SEC-01). */
@IntegrationTest
@Tag("NFR-SEC-01")
@Tag("FR-ORC-16")
@Tag("SCN-A")
class SecurityVerificationAgentTest {

    @Autowired
    private ApplicationPlanePort port;

    @Autowired
    private ShortLinkRepository links;

    @Test
    void highThreatsAreVerifiedAndSyntheticDataIsRemoved() {
        FakeApplicationPlanePort delivered = new FakeApplicationPlanePort();
        delivered.delivery = (c, m, f) -> new DeliveryStatus(List.of());
        AgentChain chain = AgentChain.analyzed(RequirementFixtures.gf001())
                .then(new DecompositionAgent(AgentChain.CATALOG))
                .then(new ThreatAssessmentAgent(AgentChain.CATALOG))
                .then(new DesignAgent(AgentChain.CATALOG))
                .withPort(delivered)
                .then(new ImplementationAgent(AgentChain.CATALOG))
                .withPort(port)
                .then(new SecurityVerificationAgent(new ProbeRegistry()));

        String report = chain.artifact("SECURITY_REPORT");
        ArtifactSchemas.assertValid("verification-report", report);
        JsonNode json = CanonicalJson.parse(report);
        assertThat(json.path("suite").asString()).isEqualTo("SECURITY");
        assertThat(json.path("unverifiedCriteria").size()).isZero();
        assertThat(json.path("failed").asInt()).isZero();
        List<String> verified = new ArrayList<>();
        json.path("results").forEach(r -> r.path("verifies").forEach(v -> verified.add(v.asString())));
        assertThat(verified).contains("TH-CA-1", "TH-CA-2", "TH-URL-1");
        assertThat(json.path("results").findValuesAsString("probeId")).contains("SEC-URL-CATALOG", "CA-SEC-RESERVED", "CA-SEC-ROUTE");
        assertThat(links.findAll()).noneMatch(l -> chain.runId().equals(l.getSyntheticRunId()));
    }
}
