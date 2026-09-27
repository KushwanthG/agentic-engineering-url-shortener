package com.agentic.urlshortener.orchestration.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
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

/** T053: release sets the capability, verifies it after release, and rolls the release state back on failure (FR-RDY-03). */
@IntegrationTest
@Tag("FR-RDY-03")
@Tag("FR-REL-05")
@Tag("SCN-A")
class ReleaseAgentTest {

    @Autowired
    private ApplicationPlanePort port;

    @Autowired
    private ShortLinkRepository links;

    private final ReleaseAgent agent = new ReleaseAgent(new ProbeRegistry());

    @AfterEach
    void withdraw() {
        port.setRelease("custom-alias", false, Map.of(), UUID.randomUUID(), "test cleanup: withdraw");
    }

    private static AgentChain designed() {
        return AgentChain.analyzed(RequirementFixtures.gf001())
                .then(new DecompositionAgent(AgentChain.CATALOG))
                .then(new ThreatAssessmentAgent(AgentChain.CATALOG))
                .then(new DesignAgent(AgentChain.CATALOG))
                .put("READINESS_REPORT", "{\"outcome\":\"READY\",\"reasons\":[],\"limitations\":[],\"evaluatedAt\":\"2026-09-26T10:00:00Z\"}");
    }

    @Test
    void releasesTheCapabilityAndVerifiesItWithoutPreview() {
        AgentChain chain = designed().withPort(port).then(agent);
        String record = chain.artifact("RELEASE_RECORD");
        ArtifactSchemas.assertValid("release-record", record);
        JsonNode json = CanonicalJson.parse(record);
        assertThat(json.path("capability").asString()).isEqualTo("custom-alias");
        assertThat(json.path("previouslyReleased").asBoolean()).isFalse();
        assertThat(json.path("released").asBoolean()).isTrue();
        assertThat(json.path("verification").path("passed").asBoolean()).isTrue();
        assertThat(json.path("rolledBack").asBoolean()).isFalse();
        assertThat(json.path("mechanism").asString()).isEqualTo("NONE");
        assertThat(port.capability("custom-alias").released()).isTrue();
        assertThat(links.findAll()).noneMatch(l -> chain.runId().equals(l.getSyntheticRunId()));
    }

    @Test
    void failedPostReleaseVerificationRollsBackTheReleaseState() {
        ApplicationPlanePort brokenRedirects = new BrokenRedirects(port);
        AgentChain chain = designed().withPort(brokenRedirects);
        StageResult result = chain.run(agent);
        assertThat(result).isInstanceOfSatisfying(StageResult.Failed.class, f -> {
            assertThat(f.failureClass()).isEqualTo(FailureClass.PERMANENT);
            assertThat(f.reason()).contains("post-release verification failed").contains("rolled back");
        });
        assertThat(port.capability("custom-alias").released()).isFalse();
        assertThat(links.findAll()).noneMatch(l -> chain.runId().equals(l.getSyntheticRunId()));
    }

    @Test
    @Tag("FR-REL-10")
    void theRollbackIsSkippedWithARecordedConflictWhenAnotherRunChangedTheFlag() {
        UUID otherRun = UUID.randomUUID();
        AgentChain chain = designed().withPort(new ConcurrentChange(new BrokenRedirects(port), port, otherRun));
        StageResult result = chain.run(agent);

        assertThat(result).isInstanceOfSatisfying(StageResult.Failed.class, f -> assertThat(f.reason())
                .contains("post-release verification failed").contains("rollback skipped").contains(otherRun.toString()));
        assertThat(port.capability("custom-alias").released()).isTrue();
        assertThat(port.capability("custom-alias").changedByRun()).isEqualTo(otherRun);
    }

    @Test
    void onlyTheReleaseAgentMayChangeReleaseState() {
        assertThat(agent.permissions()).contains(AgentPermission.CHANGE_CAPABILITY_RELEASE);
    }

    /**
     * Verification fails (through {@code broken}) after another run has withdrawn and re-released the
     * capability, so the flag no longer holds the value this run set.
     */
    private record ConcurrentChange(ApplicationPlanePort broken, ApplicationPlanePort real, UUID otherRun)
            implements ApplicationPlanePort {
        @Override public Optional<LinkSnapshot> findLink(String code) { return broken.findLink(code); }
        @Override public ProbeResponse resolve(String code) {
            real.setRelease("custom-alias", false, Map.of(), otherRun, "test: another run withdrew the capability");
            real.setRelease("custom-alias", true, Map.of(), otherRun, "test: another run re-released it");
            return broken.resolve(code);
        }
        @Override public ProbeResponse createSyntheticLink(UUID runId, SyntheticLinkSpec spec) { return broken.createSyntheticLink(runId, spec); }
        @Override public int deleteSyntheticLinks(UUID runId) { return broken.deleteSyntheticLinks(runId); }
        @Override public CapabilityState capability(String capabilityId) { return broken.capability(capabilityId); }
        @Override public DeliveryStatus deliveryStatus(String capabilityId, String migration, List<String> fields) {
            return broken.deliveryStatus(capabilityId, migration, fields);
        }
        @Override public <T> T withPreview(String capabilityId, Map<String, Object> parameters, Supplier<T> action) {
            return broken.withPreview(capabilityId, parameters, action);
        }
        @Override public CapabilityState setRelease(String capabilityId, boolean released, Map<String, Object> parameters, UUID runId,
                String reason) {
            return broken.setRelease(capabilityId, released, parameters, runId, reason);
        }
    }

    /** The real port, except that every resolution is reported as not found (simulated verification failure). */
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
