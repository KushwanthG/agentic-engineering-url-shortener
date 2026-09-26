package com.agentic.urlshortener.shortener.service;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-key token buckets with continuous refill: {@code perMinute} tokens of capacity, refilled at
 * {@code perMinute} tokens per minute. The number of tracked keys is bounded; the least recently
 * used key is evicted first, so memory cannot grow with the number of clients (NFR-SEC-04).
 * In-process only: a multi-instance deployment needs a shared limiter (backlog BL-05).
 */
public class TokenBucketRateLimiter {

    /** Result of an acquisition attempt; {@code retryAfterSeconds} is 0 when allowed. */
    public record Decision(boolean allowed, long retryAfterSeconds) {
    }

    private static final class Bucket {
        private double tokens;
        private long updatedNanos;

        private Bucket(double tokens, long updatedNanos) {
            this.tokens = tokens;
            this.updatedNanos = updatedNanos;
        }
    }

    private final double capacity;
    private final double tokensPerNano;
    private final Clock clock;
    private final Map<String, Bucket> buckets;

    public TokenBucketRateLimiter(int perMinute, int maxKeys, Clock clock) {
        if (perMinute < 1 || maxKeys < 1) {
            throw new IllegalArgumentException("perMinute and maxKeys must be positive");
        }
        this.capacity = perMinute;
        this.tokensPerNano = perMinute / 60_000_000_000.0;
        this.clock = clock;
        this.buckets = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Bucket> eldest) {
                return size() > maxKeys;
            }
        };
    }

    public synchronized Decision tryAcquire(String key) {
        Bucket bucket = refill(key);
        if (bucket.tokens >= 1) {
            bucket.tokens -= 1;
            return new Decision(true, 0);
        }
        double missingNanos = (1 - bucket.tokens) / tokensPerNano;
        return new Decision(false, Math.max(1, (long) Math.ceil(missingNanos / 1_000_000_000.0)));
    }

    /** Whether a token is available now, without consuming it. */
    public synchronized boolean hasCapacity(String key) {
        return refill(key).tokens >= 1;
    }

    /** Seconds until one token is available for {@code key} (at least 1). */
    public synchronized long retryAfterSeconds(String key) {
        Bucket bucket = refill(key);
        double missingNanos = Math.max(0, 1 - bucket.tokens) / tokensPerNano;
        return Math.max(1, (long) Math.ceil(missingNanos / 1_000_000_000.0));
    }

    public synchronized int trackedKeys() {
        return buckets.size();
    }

    private Bucket refill(String key) {
        long now = nowNanos();
        Bucket bucket = buckets.get(key);
        if (bucket == null) {
            bucket = new Bucket(capacity, now);
            buckets.put(key, bucket);
            return bucket;
        }
        long elapsed = Math.max(0, now - bucket.updatedNanos);
        bucket.tokens = Math.min(capacity, bucket.tokens + elapsed * tokensPerNano);
        bucket.updatedNanos = now;
        return bucket;
    }

    private long nowNanos() {
        var instant = clock.instant();
        return instant.getEpochSecond() * 1_000_000_000L + instant.getNano();
    }
}
