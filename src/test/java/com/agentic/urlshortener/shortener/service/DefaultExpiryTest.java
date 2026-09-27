package com.agentic.urlshortener.shortener.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.agentic.urlshortener.shortener.domain.Capability;
import com.agentic.urlshortener.shortener.dto.CreateLinkCommand;
import com.agentic.urlshortener.shortener.dto.LinkView;
import com.agentic.urlshortener.shortener.repository.ShortLinkRepository;
import com.agentic.urlshortener.support.IntegrationTest;

/**
 * T097 (FR-CAP-04, SCN-C decisions D1 and D4): with default expiry released for N days, a link created
 * without an expiry expires N days after creation; an explicit expiry is kept; links created before the
 * release keep their expiry; without the capability (never released, or withdrawn) no default applies,
 * and expiries already assigned remain.
 */
@IntegrationTest
@Tag("FR-CAP-04")
@Tag("SCN-C")
class DefaultExpiryTest {

    @Autowired private LinkCreationService creation;
    @Autowired private CapabilityService capabilities;
    @Autowired private ShortLinkRepository links;

    @AfterEach
    void withdraw() {
        capabilities.setRelease(Capability.DEFAULT_EXPIRY, false, Map.of(), "test", null, "test cleanup");
    }

    private void release(int days) {
        capabilities.setRelease(Capability.DEFAULT_EXPIRY, true, Map.of("defaultExpiryDays", days), "test", null, "test release");
    }

    private LinkView create(Instant expiresAt) {
        return creation.create(new CreateLinkCommand("https://example.com/expiry", expiresAt, null, null, "demo-consumer", null)).view();
    }

    @Test
    void withoutTheCapabilityNoDefaultApplies() {
        assertThat(create(null).expiresAt()).isNull();
    }

    @Test
    void aLinkWithoutAnExpiryGetsTheReleasedDefault() {
        release(10);
        LinkView view = create(null);
        assertThat(view.expiresAt()).isNotNull();
        assertThat(Duration.between(view.createdAt(), view.expiresAt())).isEqualTo(Duration.ofDays(10));
    }

    @Test
    void anExplicitExpiryIsKept() {
        release(10);
        Instant explicit = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        assertThat(create(explicit).expiresAt()).isEqualTo(explicit);
    }

    @Test
    void existingLinksAreUnchangedAndAssignedExpiriesRemainAfterWithdrawal() {
        String before = create(null).code();
        release(10);
        String during = create(null).code();
        capabilities.setRelease(Capability.DEFAULT_EXPIRY, false, Map.of(), "test", null, "withdrawn");
        String after = create(null).code();

        assertThat(links.findByCode(before).orElseThrow().getExpiresAt()).as("created before the release").isNull();
        assertThat(links.findByCode(during).orElseThrow().getExpiresAt()).as("assigned while released").isNotNull();
        assertThat(links.findByCode(after).orElseThrow().getExpiresAt()).as("created after withdrawal").isNull();
    }
}
