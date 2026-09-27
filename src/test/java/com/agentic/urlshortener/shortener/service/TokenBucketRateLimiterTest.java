package com.agentic.urlshortener.shortener.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.agentic.urlshortener.support.MutableClock;

/** T026: per-key token buckets with continuous refill and bounded memory (FR-LNK-10, FR-RED-05). */
@Tag("FR-LNK-10")
@Tag("FR-RED-05")
@Tag("NFR-SEC-04")
class TokenBucketRateLimiterTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-09-26T10:00:00Z"));

    @Test
    void allowsTheCapacityThenRejectsWithRetryAfter() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(3, 100, clock);
        for (int i = 0; i < 3; i++) {
            assertThat(limiter.tryAcquire("consumer-a").allowed()).isTrue();
        }
        TokenBucketRateLimiter.Decision rejected = limiter.tryAcquire("consumer-a");
        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.retryAfterSeconds()).isBetween(1L, 20L);
    }

    @Test
    void refillsOverTime() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(3, 100, clock);
        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire("k");
        }
        assertThat(limiter.hasCapacity("k")).isFalse();
        clock.advance(Duration.ofSeconds(20));
        assertThat(limiter.hasCapacity("k")).isTrue();
        assertThat(limiter.tryAcquire("k").allowed()).isTrue();
    }

    @Test
    void isolatesKeys() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(1, 100, clock);
        assertThat(limiter.tryAcquire("a").allowed()).isTrue();
        assertThat(limiter.tryAcquire("a").allowed()).isFalse();
        assertThat(limiter.tryAcquire("b").allowed()).isTrue();
    }

    @Test
    void boundsTheNumberOfTrackedKeys() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(5, 50, clock);
        for (int i = 0; i < 1_000; i++) {
            limiter.tryAcquire("client-" + i);
            clock.advance(Duration.ofSeconds(1));
        }
        assertThat(limiter.trackedKeys()).isLessThanOrEqualTo(50);
    }
}
