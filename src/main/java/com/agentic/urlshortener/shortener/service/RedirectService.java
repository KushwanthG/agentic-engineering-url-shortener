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

import com.agentic.urlshortener.shortener.ShortenerMeters;
import com.agentic.urlshortener.shortener.domain.ShortLink;
import com.agentic.urlshortener.shortener.dto.Resolution;
import com.agentic.urlshortener.shortener.repository.ShortLinkRepository;


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
    private final ShortenerMeters meters;

    public RedirectService(ShortLinkRepository links, ClickRecorder clickRecorder, Clock clock, ShortenerMeters meters) {
        this.links = links;
        this.clickRecorder = clickRecorder;
        this.clock = clock;
        this.meters = meters;
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
        if (link.isExpiredAt(now) || link.isExhausted()) {
            return Resolution.expired();
        }
        if (link.getMaxClicks() != null) {
            return resolveLimited(link, code, now, referrer);
        }
        try {
            clickRecorder.record(link.getId(), now, referrerHost(referrer));
        } catch (RuntimeException e) {
            meters.analyticsFailure();
            log.warn("Click for code {} not recorded ({}); redirect served (fail-open)", code, e.getClass().getSimpleName());
        }
        return Resolution.redirect(link.getTargetUrl());
    }

    /**
     * Click-limited links (BF-001): the click is counted atomically only while below the limit, so a
     * redirect happens only if it was counted; the resolution after the last allowed click is expired.
     * If the click cannot be recorded, the redirect is refused (fail closed, AC-6): serving it
     * uncounted could exceed the limit.
     */
    private Resolution resolveLimited(ShortLink link, String code, Instant now, String referrer) {
        boolean counted;
        try {
            counted = clickRecorder.recordWithinLimit(link.getId(), link.isSynthetic(), now, referrerHost(referrer));
        } catch (RuntimeException e) {
            meters.analyticsFailure();
            log.warn("Click for limited code {} not recorded ({}); redirect refused (fail-closed)", code, e.getClass().getSimpleName());
            return Resolution.unavailable();
        }
        return counted ? Resolution.redirect(link.getTargetUrl()) : Resolution.expired();
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
