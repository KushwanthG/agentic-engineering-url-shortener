package com.agentic.urlshortener.orchestration.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.agentic.urlshortener.orchestration.audit.AuditService;
import com.agentic.urlshortener.orchestration.domain.AuditEvent;
import com.agentic.urlshortener.orchestration.domain.StageType;
import com.agentic.urlshortener.orchestration.port.ApplicationPlanePort;
import com.agentic.urlshortener.orchestration.port.CapabilityState;
import com.agentic.urlshortener.orchestration.port.DeliveryStatus;
import com.agentic.urlshortener.orchestration.port.LinkSnapshot;
import com.agentic.urlshortener.orchestration.port.PermissionScopedPort;
import com.agentic.urlshortener.orchestration.port.ProbeResponse;
import com.agentic.urlshortener.orchestration.port.SyntheticLinkSpec;
import com.agentic.urlshortener.support.IntegrationTest;

/** T039: agents reach the application plane only through a permission-scoped port (FR-ORC-12, NFR-AUT-01). */
@IntegrationTest
@Tag("FR-ORC-12")
@Tag("NFR-AUT-01")
class AgentPermissionTest {

    @Autowired
    private PermissionAudit permissionAudit;

    @Autowired
    private AuditService audit;

    @Autowired
    private AgentRegistry registry;

    /** Counts delegate calls: a denied call must never reach the application plane. */
    private static final class CountingPort implements ApplicationPlanePort {
        final AtomicInteger calls = new AtomicInteger();

        @Override public Optional<LinkSnapshot> findLink(String code) { calls.incrementAndGet(); return Optional.empty(); }
        @Override public ProbeResponse resolve(String code) { calls.incrementAndGet(); return ProbeResponse.notFound(); }
        @Override public ProbeResponse createSyntheticLink(UUID runId, SyntheticLinkSpec spec) { calls.incrementAndGet(); return ProbeResponse.notFound(); }
        @Override public int deleteSyntheticLinks(UUID runId) { calls.incrementAndGet(); return 0; }
        @Override public CapabilityState capability(String capabilityId) { calls.incrementAndGet(); return new CapabilityState(capabilityId, false, Map.of()); }
        @Override public DeliveryStatus deliveryStatus(String capabilityId, String migration, List<String> fields) { calls.incrementAndGet(); return null; }
        @Override public <T> T withPreview(String capabilityId, Map<String, Object> parameters, Supplier<T> action) { calls.incrementAndGet(); return action.get(); }
        @Override public CapabilityState setRelease(String capabilityId, boolean released, Map<String, Object> parameters, UUID runId, String reason) {
            calls.incrementAndGet(); return new CapabilityState(capabilityId, released, parameters);
        }
    }

    @Test
    void undeclaredCallIsDeniedAuditedAndNeverDelegated() {
        UUID runId = UUID.randomUUID();
        CountingPort delegate = new CountingPort();
        ApplicationPlanePort scoped = new PermissionScopedPort(delegate, Set.of(AgentPermission.READ_LINKS), runId,
                StageType.TESTING, "tester@1.0", permissionAudit);

        scoped.findLink("abc1234");
        assertThat(delegate.calls).hasValue(1);

        assertThatThrownBy(() -> scoped.setRelease("custom-alias", true, Map.of(), runId, "sneaky"))
                .isInstanceOf(AgentPermissionDeniedException.class)
                .hasMessageContaining("CHANGE_CAPABILITY_RELEASE").hasMessageContaining("tester@1.0");
        assertThatThrownBy(() -> scoped.createSyntheticLink(runId, new SyntheticLinkSpec("https://example.com", null, null, null)))
                .isInstanceOf(AgentPermissionDeniedException.class);
        assertThat(delegate.calls).hasValue(1);

        List<AuditEvent> events = audit.chain(runId.toString());
        assertThat(events).extracting(AuditEvent::getAction).containsExactly("AGENT_PERMISSION_DENIED", "AGENT_PERMISSION_DENIED");
        assertThat(events.getFirst().getActorId()).isEqualTo("tester@1.0");
        assertThat(events.getFirst().getReason()).contains("CHANGE_CAPABILITY_RELEASE");
        assertThat(audit.verify(runId.toString()).valid()).isTrue();
    }

    @Test
    void onlyTheReleaseAgentMayChangeReleaseState() {
        for (StageAgent agent : registry.all()) {
            boolean mayRelease = agent.permissions().contains(AgentPermission.CHANGE_CAPABILITY_RELEASE);
            assertThat(mayRelease).as(agent.agentId()).isEqualTo(agent.stageType() == StageType.RELEASE);
        }
    }

    @Test
    void gatesHaveNoAgent() {
        for (StageType type : StageType.values()) {
            if (type.isGate()) {
                assertThat(registry.primary(type)).as(type.name()).isEmpty();
            }
        }
    }
}
