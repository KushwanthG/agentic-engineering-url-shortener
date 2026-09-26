package com.agentic.urlshortener.orchestration.reliability;

import java.time.Duration;

/**
 * Retry and timeout policy of one stage (FR-REL-01, PVT-11, PVT-12): at most {@code maxAttempts}
 * attempts per stage generation; the wait after failed attempt n is {@code initialBackoff × 2^(n-1)},
 * capped at {@code maxBackoff}; each attempt is cancelled after {@code timeout}.
 */
public record RetryPolicy(int maxAttempts, Duration timeout, Duration initialBackoff, Duration maxBackoff) {

    public RetryPolicy {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be at least 1");
        }
    }

    /** Wait before the attempt that follows failed attempt {@code failedAttemptNo} (1-based). */
    public Duration backoffAfter(int failedAttemptNo) {
        int exponent = Math.min(Math.max(failedAttemptNo - 1, 0), 30);
        Duration backoff = initialBackoff.multipliedBy(1L << exponent);
        return backoff.compareTo(maxBackoff) > 0 ? maxBackoff : backoff;
    }

    /** Whether another attempt may follow failed attempt {@code failedAttemptNo}. */
    public boolean allowsRetryAfter(int failedAttemptNo) {
        return failedAttemptNo < maxAttempts;
    }
}
