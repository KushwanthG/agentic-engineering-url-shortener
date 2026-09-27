package com.agentic.urlshortener.orchestration.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.agentic.urlshortener.common.util.CanonicalJson;
import com.agentic.urlshortener.orchestration.domain.FailureClass;
import com.agentic.urlshortener.orchestration.port.DeliveryStatus;
import com.agentic.urlshortener.support.AgentChain;
import com.agentic.urlshortener.support.ArtifactSchemas;
import com.agentic.urlshortener.support.FakeApplicationPlanePort;
import com.agentic.urlshortener.support.RequirementFixtures;

import tools.jackson.databind.JsonNode;

/** T130: the change set maps tasks to components, and delivery is checked live; an absent capability fails permanently. */
@Tag("FR-ORC-15")
@Tag("SCN-A")
class ImplementationAgentTest {

    private final ImplementationAgent agent = new ImplementationAgent(AgentChain.CATALOG);

    private static AgentChain designed(FakeApplicationPlanePort port) {
        return AgentChain.analyzed(RequirementFixtures.gf001())
                .then(new DecompositionAgent(AgentChain.CATALOG))
                .then(new ThreatAssessmentAgent(AgentChain.CATALOG))
                .then(new DesignAgent(AgentChain.CATALOG))
                .withPort(port);
    }

    @Test
    void deliveredCapabilityProducesAChangeSetWithLiveChecks() {
        AtomicReference<List<String>> requested = new AtomicReference<>();
        FakeApplicationPlanePort port = new FakeApplicationPlanePort();
        port.delivery = (capability, migration, fields) -> {
            requested.set(List.of(capability, String.valueOf(migration), String.join(",", fields)));
            return new DeliveryStatus(List.of(new DeliveryStatus.Check("PROVIDER_REGISTERED", "PASS", "ok"),
                    new DeliveryStatus.Check("MIGRATION_APPLIED", "PASS", "V3 applied"),
                    new DeliveryStatus.Check("CONTRACT_DECLARES_FIELDS", "PASS", "declared"),
                    new DeliveryStatus.Check("RELEASE_STATE_KNOWN", "PASS", "known")));
        };
        String changeSet = designed(port).then(agent).artifact("CHANGE_SET");
        ArtifactSchemas.assertValid("change-set", changeSet);
        JsonNode json = CanonicalJson.parse(changeSet);

        assertThat(json.path("capability").asString()).isEqualTo("custom-alias");
        assertThat(json.path("delivered").asBoolean()).isTrue();
        assertThat(json.path("units").size()).isEqualTo(7);
        assertThat(json.path("units").findValuesAsString("taskId")).contains("WT-01", "WT-07");
        assertThat(requested.get()).containsExactly("custom-alias", "3", "alias,customAlias");
    }

    @Test
    void absentCapabilityIsAPermanentFailureNotAFabricatedSuccess() {
        FakeApplicationPlanePort port = new FakeApplicationPlanePort();
        port.delivery = (capability, migration, fields) -> new DeliveryStatus(List.of(
                new DeliveryStatus.Check("PROVIDER_REGISTERED", "PASS", "ok"),
                new DeliveryStatus.Check("MIGRATION_APPLIED", "FAIL", "V3 missing")));
        StageResult result = designed(port).run(agent);
        assertThat(result).isInstanceOfSatisfying(StageResult.Failed.class, f -> {
            assertThat(f.failureClass()).isEqualTo(FailureClass.PERMANENT);
            assertThat(f.reason()).contains("not delivered").contains("MIGRATION_APPLIED");
        });
    }

    @Test
    void declaresOnlyTheReadPermissionItNeeds() {
        assertThat(agent.permissions()).containsExactly(AgentPermission.READ_CAPABILITIES);
    }
}
