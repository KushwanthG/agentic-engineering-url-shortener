package com.agentic.urlshortener.orchestration.reliability;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.agentic.urlshortener.orchestration.domain.StageType;

/** T067 (FR-REL-01, NFR-REL-02, PVT-11): 3 attempts; backoff 200 ms doubling, capped at 5 s. */
@Tag("FR-REL-01")
@Tag("NFR-REL-02")
class RetryPolicyTest {

    private final RetryPolicy policy = new RetryPolicy(3, Duration.ofSeconds(30), Duration.ofMillis(200), Duration.ofSeconds(5));

    @Test
    void backoffDoublesFrom200MillisecondsAndIsCappedAtFiveSeconds() {
        assertThat(policy.backoffAfter(1)).isEqualTo(Duration.ofMillis(200));
        assertThat(policy.backoffAfter(2)).isEqualTo(Duration.ofMillis(400));
        assertThat(policy.backoffAfter(3)).isEqualTo(Duration.ofMillis(800));
        assertThat(policy.backoffAfter(5)).isEqualTo(Duration.ofMillis(3200));
        assertThat(policy.backoffAfter(6)).isEqualTo(Duration.ofSeconds(5));
        assertThat(policy.backoffAfter(40)).isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    void retriesAreBoundedByTheMaximumAttempts() {
        assertThat(policy.allowsRetryAfter(1)).isTrue();
        assertThat(policy.allowsRetryAfter(2)).isTrue();
        assertThat(policy.allowsRetryAfter(3)).isFalse();
        assertThat(policy.allowsRetryAfter(7)).isFalse();
    }

    @Test
    void stagePoliciesDefaultToPvt11AndPvt12AndAcceptPerStageOverrides() {
        StagePolicyProperties properties = new StagePolicyProperties(null,
                java.util.Map.of(StageType.TESTING, new StagePolicyProperties.StagePolicy(5, null, Duration.ofMillis(10), null)));

        RetryPolicy defaults = properties.policyFor(StageType.DESIGN);
        assertThat(defaults.maxAttempts()).isEqualTo(3);
        assertThat(defaults.timeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(defaults.initialBackoff()).isEqualTo(Duration.ofMillis(200));
        assertThat(defaults.maxBackoff()).isEqualTo(Duration.ofSeconds(5));

        RetryPolicy testing = properties.policyFor(StageType.TESTING);
        assertThat(testing.maxAttempts()).isEqualTo(5);
        assertThat(testing.timeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(testing.initialBackoff()).isEqualTo(Duration.ofMillis(10));
    }
}
