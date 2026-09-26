package com.agentic.urlshortener.orchestration.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.agentic.urlshortener.orchestration.audit.AuditService;
import com.agentic.urlshortener.orchestration.domain.AuditEvent;
import com.agentic.urlshortener.orchestration.domain.AuditRecord;
import com.agentic.urlshortener.orchestration.port.ApplicationPlanePort;
import com.agentic.urlshortener.orchestration.port.CapabilityState;
import com.agentic.urlshortener.orchestration.port.DeliveryStatus;
import com.agentic.urlshortener.orchestration.port.ProbeResponse;
import com.agentic.urlshortener.orchestration.port.SyntheticLinkSpec;
import com.agentic.urlshortener.shortener.repository.ShortLinkRepository;
import com.agentic.urlshortener.support.IntegrationTest;

/** T043: the in-process adapter is the control plane's only route into the shortener (ADR-001, ADR-018). */
@IntegrationTest
@Tag("FR-CAP-01")
@Tag("FR-ORC-16")
@Tag("FR-RDY-03")
class ApplicationPlaneAdapterTest {

    @Autowired
    private ApplicationPlanePort port;

    @Autowired
    private ShortLinkRepository links;

    @Autowired
    private AuditService audit;

    @Test
    void syntheticLinksAreLabeledOwnedByTheRunAndRemovedOnlyForThatRun() {
        UUID run = UUID.randomUUID();
        UUID otherRun = UUID.randomUUID();
        ProbeResponse mine = port.createSyntheticLink(run, new SyntheticLinkSpec("https://example.com/probe", null, null, null));
        ProbeResponse theirs = port.createSyntheticLink(otherRun, new SyntheticLinkSpec("https://example.com/other", null, null, null));
        assertThat(mine.outcome()).isEqualTo(ProbeResponse.Outcome.CREATED);

        var stored = links.findByCode(mine.code()).orElseThrow();
        assertThat(stored.isSynthetic()).isTrue();
        assertThat(stored.getSyntheticRunId()).isEqualTo(run);

        assertThat(port.resolve(mine.code())).isEqualTo(ProbeResponse.redirect("https://example.com/probe"));
        assertThat(port.findLink(mine.code()).orElseThrow().clickCount()).isEqualTo(1);

        assertThat(port.deleteSyntheticLinks(run)).isEqualTo(1);
        assertThat(links.findByCode(mine.code())).isEmpty();
        assertThat(links.findByCode(theirs.code())).isPresent();
        port.deleteSyntheticLinks(otherRun);
    }

    @Test
    void creationRulesStillApplyToSyntheticLinks() {
        ProbeResponse rejected = port.createSyntheticLink(UUID.randomUUID(), new SyntheticLinkSpec("http://127.0.0.1/", null, null, null));
        assertThat(rejected.outcome()).isEqualTo(ProbeResponse.Outcome.REJECTED);
        assertThat(rejected.errorCode()).isEqualTo("URL_HOST_NOT_ALLOWED");
        assertThat(port.resolve("nosuch99").outcome()).isEqualTo(ProbeResponse.Outcome.NOT_FOUND);
    }

    @Test
    void releaseIsASetOperationAuditedOnTheGlobalChain() {
        UUID run = UUID.randomUUID();
        int before = audit.chain(AuditRecord.GLOBAL_CHAIN).size();
        try {
            CapabilityState released = port.setRelease("click-limit", true, Map.of(), run, "test release");
            CapabilityState again = port.setRelease("click-limit", true, Map.of(), run, "test release repeated");
            assertThat(released.released()).isTrue();
            assertThat(again.released()).isTrue();
            assertThat(port.capability("click-limit").released()).isTrue();

            List<AuditEvent> global = audit.chain(AuditRecord.GLOBAL_CHAIN);
            List<AuditEvent> added = global.subList(before, global.size());
            assertThat(added).extracting(AuditEvent::getAction).containsExactly("CAPABILITY_CHANGED", "CAPABILITY_CHANGED");
            assertThat(added.get(0).getResult()).isEqualTo("CHANGED");
            assertThat(added.get(1).getResult()).isEqualTo("UNCHANGED");
            assertThat(added.get(0).getDetails()).contains(run.toString());
            assertThat(audit.verify(AuditRecord.GLOBAL_CHAIN).valid()).isTrue();
        } finally {
            port.setRelease("click-limit", false, Map.of(), run, "test cleanup: withdraw");
        }
        assertThat(port.capability("click-limit").released()).isFalse();
    }

    @Test
    void previewThroughThePortIsScoped() {
        boolean inside = port.withPreview("custom-alias", Map.of(), () -> port.capability("custom-alias").released()
                || port.createSyntheticLink(UUID.randomUUID(), new SyntheticLinkSpec("https://example.com", null, null, null)) != null);
        assertThat(inside).isTrue();
        assertThat(port.capability("custom-alias").released()).isFalse();
    }

    @Test
    void deliveryChecksAreLive() {
        DeliveryStatus delivered = port.deliveryStatus("custom-alias", "1", List.of("alias"));
        assertThat(delivered.checks()).extracting(DeliveryStatus.Check::check)
                .contains("PROVIDER_REGISTERED", "MIGRATION_APPLIED", "CONTRACT_DECLARES_FIELDS", "RELEASE_STATE_KNOWN");
        assertThat(delivered.delivered()).isTrue();

        DeliveryStatus missingMigration = port.deliveryStatus("custom-alias", "99", List.of("alias"));
        assertThat(missingMigration.delivered()).isFalse();
        DeliveryStatus missingField = port.deliveryStatus("custom-alias", null, List.of("noSuchField"));
        assertThat(missingField.delivered()).isFalse();
        DeliveryStatus unknown = port.deliveryStatus("teleportation", null, List.of());
        assertThat(unknown.delivered()).isFalse();
    }
}
