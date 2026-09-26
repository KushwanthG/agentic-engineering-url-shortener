package com.agentic.urlshortener.orchestration.reliability;

import java.time.Duration;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.agentic.urlshortener.orchestration.domain.StageType;

/**
 * Per-stage retry and timeout settings ({@code app.orchestration.stages}). {@code defaults} apply to
 * every stage; {@code overrides} replace individual values for one stage type. Unset values fall back
 * to PVT-11 (3 attempts, 200 ms doubling, capped at 5 s) and PVT-12 (30 s timeout).
 */
@ConfigurationProperties("app.orchestration.stages")
public record StagePolicyProperties(StagePolicy defaults, Map<StageType, StagePolicy> overrides) {

    private static final StagePolicy PVT = new StagePolicy(3, Duration.ofSeconds(30), Duration.ofMillis(200), Duration.ofSeconds(5));

    public StagePolicyProperties {
        defaults = PVT.overriddenBy(defaults);
        overrides = overrides == null ? Map.of() : Map.copyOf(overrides);
    }

    public RetryPolicy policyFor(StageType stage) {
        StagePolicy effective = defaults.overriddenBy(overrides.get(stage));
        return new RetryPolicy(effective.maxAttempts(), effective.timeout(), effective.initialBackoff(), effective.maxBackoff());
    }

    /** One set of values; {@code null} means "inherit". */
    public record StagePolicy(Integer maxAttempts, Duration timeout, Duration initialBackoff, Duration maxBackoff) {

        StagePolicy overriddenBy(StagePolicy other) {
            if (other == null) {
                return this;
            }
            return new StagePolicy(other.maxAttempts != null ? other.maxAttempts : maxAttempts,
                    other.timeout != null ? other.timeout : timeout,
                    other.initialBackoff != null ? other.initialBackoff : initialBackoff,
                    other.maxBackoff != null ? other.maxBackoff : maxBackoff);
        }
    }
}
