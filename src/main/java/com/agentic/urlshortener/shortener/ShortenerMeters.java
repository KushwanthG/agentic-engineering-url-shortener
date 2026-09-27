package com.agentic.urlshortener.shortener;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Application-plane meters of plan.md §7 (NFR-OBS-02). Tags are bounded enumerations (redirect
 * outcome, limiter name); codes, URLs, and client addresses are never tags.
 */
@Component
public class ShortenerMeters {

    public static final String CREATION_LIMITER = "creation";
    public static final String NOT_FOUND_LIMITER = "not-found";

    private final MeterRegistry registry;
    private final Counter linksCreated;
    private final Counter analyticsFailures;

    public ShortenerMeters(MeterRegistry registry) {
        this.registry = registry;
        this.linksCreated = Counter.builder("shortener.links.created").description("Short links created (idempotent replays excluded)")
                .register(registry);
        this.analyticsFailures = Counter.builder("shortener.analytics.failures")
                .description("Redirects whose click could not be recorded (redirect still served)").register(registry);
        rejected(CREATION_LIMITER);
        rejected(NOT_FOUND_LIMITER);
    }

    public void linkCreated() {
        linksCreated.increment();
    }

    /** {@code outcome}: REDIRECT, NOT_FOUND, EXPIRED, or UNAVAILABLE. */
    public void redirect(String outcome) {
        Counter.builder("shortener.redirects").tag("outcome", outcome).register(registry).increment();
    }

    public void analyticsFailure() {
        analyticsFailures.increment();
    }

    public void rateLimited(String limiter) {
        rejected(limiter).increment();
    }

    private Counter rejected(String limiter) {
        return Counter.builder("shortener.ratelimit.rejected").tag("limiter", limiter).description("Requests rejected by a rate limiter")
                .register(registry);
    }
}
