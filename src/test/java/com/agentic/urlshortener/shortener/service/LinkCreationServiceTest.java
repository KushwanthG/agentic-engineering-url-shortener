package com.agentic.urlshortener.shortener.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import com.agentic.urlshortener.common.exception.ApiException;
import com.agentic.urlshortener.common.exception.ErrorCode;
import com.agentic.urlshortener.shortener.domain.LinkStatus;
import com.agentic.urlshortener.shortener.dto.CreateLinkCommand;
import com.agentic.urlshortener.shortener.dto.CreatedLink;
import com.agentic.urlshortener.shortener.dto.LinkView;
import com.agentic.urlshortener.shortener.repository.ShortLinkRepository;
import com.agentic.urlshortener.support.ControllableTestConfig;
import com.agentic.urlshortener.support.IntegrationTest;
import com.agentic.urlshortener.support.MutableClock;

/** T021: validated creation, expiry rules, bounded collision retry, no de-duplication, capability gating. */
@IntegrationTest
@Import(ControllableTestConfig.class)
@Tag("FR-LNK-01")
@Tag("FR-LNK-05")
@Tag("FR-LNK-07")
@Tag("FR-LNK-12")
@Tag("FR-CAP-01")
class LinkCreationServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-26T10:00:00Z");

    @Autowired
    private LinkCreationService service;

    @Autowired
    private ShortLinkRepository links;

    @Autowired
    private MutableClock clock;

    @Autowired
    private ControllableTestConfig.ScriptedShortCodeGenerator generator;

    @BeforeEach
    void reset() {
        clock.set(NOW);
        generator.clear();
    }

    private static CreateLinkCommand command(String url, Instant expiresAt) {
        return new CreateLinkCommand(url, expiresAt, null, null, "demo-consumer", null);
    }

    @Test
    void createsANormalizedActiveLinkAndRecordsTheCreator() {
        CreatedLink created = service.create(command("HTTPS://Example.COM/a?b=1", null));
        LinkView view = created.view();
        assertThat(created.replayed()).isFalse();
        assertThat(view.code()).matches("[0-9A-Za-z]{7}");
        assertThat(view.targetUrl()).isEqualTo("https://example.com/a?b=1");
        assertThat(view.createdAt()).isEqualTo(NOW);
        assertThat(view.status()).isEqualTo(LinkStatus.ACTIVE);
        assertThat(links.findByCode(view.code()).orElseThrow().getCreatedBy()).isEqualTo("demo-consumer");
    }

    @Test
    void rejectsExpiryInThePastAtNowOrBeyondTheHorizon() {
        for (Instant bad : new Instant[] {NOW.minusSeconds(1), NOW, NOW.plus(Duration.ofDays(1827))}) {
            assertThatThrownBy(() -> service.create(command("https://example.com/", bad)))
                    .extracting(e -> ((ApiException) e).code()).isEqualTo(ErrorCode.INVALID_EXPIRY);
        }
        assertThat(service.create(command("https://example.com/", NOW.plus(Duration.ofDays(1826)))).view().expiresAt())
                .isEqualTo(NOW.plus(Duration.ofDays(1826)));
    }

    @Test
    void regeneratesOnCollisionWithoutCreatingAPartialLink() {
        String taken = service.create(command("https://example.com/first", null)).view().code();
        generator.enqueue(taken, taken, "fresh01");
        CreatedLink second = service.create(command("https://example.com/second", null));
        assertThat(second.view().code()).isEqualTo("fresh01");
        assertThat(links.findByCode(taken).orElseThrow().getTargetUrl()).isEqualTo("https://example.com/first");
    }

    @Test
    void failsRetryablyWhenCollisionsExhaustTheBound() {
        String taken = service.create(command("https://example.com/only", null)).view().code();
        long before = links.count();
        generator.enqueue(taken, taken, taken, taken, taken);
        assertThatThrownBy(() -> service.create(command("https://example.com/never", null)))
                .extracting(e -> ((ApiException) e).code()).isEqualTo(ErrorCode.CODE_GENERATION_EXHAUSTED);
        assertThat(links.count()).isEqualTo(before);
    }

    @Test
    void sameTargetTwiceCreatesTwoDistinctLinks() {
        String a = service.create(command("https://example.com/same", null)).view().code();
        String b = service.create(command("https://example.com/same", null)).view().code();
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void unreleasedCapabilitiesAreRejectedNotIgnored() {
        assertThatThrownBy(() -> service.create(new CreateLinkCommand("https://example.com/", null, "spring-sale", null, "demo-consumer", null)))
                .extracting(e -> ((ApiException) e).code()).isEqualTo(ErrorCode.CAPABILITY_NOT_AVAILABLE);
        assertThatThrownBy(() -> service.create(new CreateLinkCommand("https://example.com/", null, null, 5L, "demo-consumer", null)))
                .extracting(e -> ((ApiException) e).code()).isEqualTo(ErrorCode.CAPABILITY_NOT_AVAILABLE);
    }

    @Test
    void invalidUrlsAreRejectedWithTheirSpecificCode() {
        assertThatThrownBy(() -> service.create(command("javascript:alert(1)", null)))
                .extracting(e -> ((ApiException) e).code()).isEqualTo(ErrorCode.URL_SCHEME_NOT_ALLOWED);
    }
}
