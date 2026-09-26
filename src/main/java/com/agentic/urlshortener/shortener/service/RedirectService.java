package com.agentic.urlshortener.shortener.service;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.agentic.urlshortener.shortener.domain.ShortLink;
import com.agentic.urlshortener.shortener.dto.Resolution;
import com.agentic.urlshortener.shortener.repository.ShortLinkRepository;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Resolves short codes (FR-RED-01, 03, 04). The click is counted before the redirect is returned
 * (FR-ANL-01); if recording fails, the redirect still happens and the failure is counted in
 * {@code shortener.analytics.failures} (fail-open, FR-ANL-04, ADR-016). Lookup failures are not
 * swallowed: an unavailable store surfaces as a retryable 503.
 */
@Service
public class RedirectService {

    private static final Logger log = LoggerFactory.getLogger(RedirectService.class);
    private static final Pattern CODE = Pattern.compile("[A-Za-z0-9_-]{3,32}");
    private static final int MAX_HOST_LENGTH = 255;

    private final ShortLinkRepository links;
    private final ClickRecorder clickRecorder;
    private final Clock clock;
    private final Counter analyticsFailures;

    public RedirectService(ShortLinkRepository links, ClickRecorder clickRecorder, Clock clock, MeterRegistry meters) {
        this.links = links;
        this.clickRecorder = clickRecorder;
        this.clock = clock;
        this.analyticsFailures = Counter.builder("shortener.analytics.failures")
                .description("Redirects whose click could not be recorded (redirect still served)")
                .register(meters);
    }

    public Resolution resolve(String code, String referrer) {
        if (code == null || !CODE.matcher(code).matches()) {
            return Resolution.notFound();
        }
        Optional<ShortLink> found = links.findByCode(code);
        if (found.isEmpty()) {
            return Resolution.notFound();
        }
        ShortLink link = found.get();
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        if (link.isExpiredAt(now)) {
            return Resolution.expired();
        }
        try {
            clickRecorder.record(link.getId(), now, referrerHost(referrer));
        } catch (RuntimeException e) {
            analyticsFailures.increment();
            log.warn("Click for code {} not recorded ({}); redirect served (fail-open)", code, e.getClass().getSimpleName());
        }
        return Resolution.redirect(link.getTargetUrl());
    }

    /** Reduces a Referer header to its lower-case host; anything else is discarded (FR-ANL-02). */
    public static String referrerHost(String referrer) {
        if (referrer == null || referrer.isBlank()) {
            return null;
        }
        try {
            String host = URI.create(referrer.strip()).getHost();
            if (host == null || host.length() > MAX_HOST_LENGTH) {
                return null;
            }
            return host.toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
