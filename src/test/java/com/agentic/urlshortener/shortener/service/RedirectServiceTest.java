package com.agentic.urlshortener.shortener.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.willThrow;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.agentic.urlshortener.shortener.dto.CreateLinkCommand;
import com.agentic.urlshortener.shortener.dto.Resolution;
import com.agentic.urlshortener.shortener.repository.ShortLinkRepository;
import com.agentic.urlshortener.support.ControllableTestConfig;
import com.agentic.urlshortener.support.IntegrationTest;
import com.agentic.urlshortener.support.MutableClock;

import io.micrometer.core.instrument.MeterRegistry;

/** T024: redirect outcomes, exact counting, expiry at the boundary, and fail-open analytics. */
@IntegrationTest
@Import(ControllableTestConfig.class)
@Tag("FR-RED-01")
@Tag("FR-RED-03")
@Tag("FR-RED-04")
@Tag("FR-ANL-01")
@Tag("FR-ANL-02")
@Tag("FR-ANL-04")
@Tag("NFR-REL-01")
class RedirectServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-26T10:00:00Z");

    @Autowired
    private RedirectService redirects;

    @Autowired
    private LinkCreationService creation;

    @Autowired
    private ShortLinkRepository links;

    @Autowired
    private MutableClock clock;

    @Autowired
    private MeterRegistry meters;

    @MockitoSpyBean
    private ClickRecorder clickRecorder;

    @BeforeEach
    void reset() {
        clock.set(NOW);
    }

    private String create(String url, Instant expiresAt) {
        return creation.create(new CreateLinkCommand(url, expiresAt, null, null, "demo-consumer", null)).view().code();
    }

    @Test
    void activeLinkRedirectsAndIsCountedOnce() {
        String code = create("https://example.com/active", null);
        Resolution resolution = redirects.resolve(code, "https://news.example/story?id=7");
        assertThat(resolution.outcome()).isEqualTo(Resolution.Outcome.REDIRECT);
        assertThat(resolution.targetUrl()).isEqualTo("https://example.com/active");
        assertThat(links.findByCode(code).orElseThrow().getClickCount()).isEqualTo(1);
        assertThat(links.findByCode(code).orElseThrow().getLastAccessedAt()).isEqualTo(NOW);
    }

    @Test
    void unknownAndMalformedCodesAreNotFound() {
        assertThat(redirects.resolve("zzzzzzz", null).outcome()).isEqualTo(Resolution.Outcome.NOT_FOUND);
        assertThat(redirects.resolve("bad code!", null).outcome()).isEqualTo(Resolution.Outcome.NOT_FOUND);
        assertThat(redirects.resolve("a".repeat(40), null).outcome()).isEqualTo(Resolution.Outcome.NOT_FOUND);
    }

    @Test
    void expiresExactlyAtTheExpiryInstantWithoutCounting() {
        String code = create("https://example.com/expiring", NOW.plus(Duration.ofHours(1)));
        clock.set(NOW.plus(Duration.ofHours(1)));
        Resolution resolution = redirects.resolve(code, null);
        assertThat(resolution.outcome()).isEqualTo(Resolution.Outcome.EXPIRED);
        assertThat(resolution.targetUrl()).isNull();
        assertThat(links.findByCode(code).orElseThrow().getClickCount()).isZero();
    }

    @Test
    void analyticsFailureStillRedirectsAndIsCounted() {
        String code = create("https://example.com/fail-open", null);
        double before = failures();
        willThrow(new DataAccessResourceFailureException("simulated analytics write failure (test)"))
                .given(clickRecorder).record(anyLong(), any(), any());

        Resolution resolution = redirects.resolve(code, null);

        assertThat(resolution.outcome()).isEqualTo(Resolution.Outcome.REDIRECT);
        assertThat(failures()).isEqualTo(before + 1);
    }

    @Test
    void referrerIsReducedToItsHost() {
        assertThat(RedirectService.referrerHost("https://News.Example:8443/path?q=secret")).isEqualTo("news.example");
        assertThat(RedirectService.referrerHost("not a url")).isNull();
        assertThat(RedirectService.referrerHost(null)).isNull();
    }

    private double failures() {
        var counter = meters.find("shortener.analytics.failures").counter();
        return counter == null ? 0 : counter.count();
    }
}
