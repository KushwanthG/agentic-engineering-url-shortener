package com.agentic.urlshortener.orchestration.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * T077 (FR-RDY-01, FR-RDY-02): the readiness matrix. NOT_READY when validation failed or a mandatory
 * policy failed without an approved exception; READY_WITH_ACCEPTED_LIMITATIONS when an advisory policy
 * failed, an exception was used, or a stage ran degraded; otherwise READY. The release-time re-check of
 * expired exceptions is in PolicyExceptionFlowTest.
 */
@Tag("FR-RDY-01")
@Tag("FR-RDY-02")
class ReadinessEvaluatorTest {

    private static final Instant NOW = Instant.parse("2026-09-26T10:00:00Z");

    private final ReadinessEvaluator evaluator = new ReadinessEvaluator();

    private static PolicyResult policies(List<String> blocking, List<String> advisory, List<String> accepted) {
        return new PolicyResult("1.0.0", List.of(), !blocking.isEmpty(), blocking, advisory, accepted);
    }

    private static final PolicyResult CLEAN = policies(List.of(), List.of(), List.of());

    @Test
    void cleanValidationAndPoliciesAreReady() {
        ReadinessEvaluator.Readiness readiness = evaluator.evaluate(true, CLEAN, List.of(), NOW);
        assertThat(readiness.outcome()).isEqualTo("READY");
        assertThat(readiness.limitations()).isEmpty();
        assertThat(readiness.reasons()).singleElement().asString().contains("1.0.0");
    }

    @Test
    void failedValidationOrAnUnexceptedMandatoryFailureIsNotReady() {
        assertThat(evaluator.evaluate(false, CLEAN, List.of(), NOW).outcome()).isEqualTo("NOT_READY");
        ReadinessEvaluator.Readiness blocked = evaluator.evaluate(true, policies(List.of("DOC-001"), List.of(), List.of()), List.of(), NOW);
        assertThat(blocked.outcome()).isEqualTo("NOT_READY");
        assertThat(blocked.reasons()).anyMatch(r -> r.contains("DOC-001"));
    }

    @Test
    void anyLimitationRequiresExplicitAcceptance() {
        assertThat(evaluator.evaluate(true, policies(List.of(), List.of("PRV-002"), List.of()), List.of(), NOW).outcome())
                .isEqualTo("READY_WITH_ACCEPTED_LIMITATIONS");
        assertThat(evaluator.evaluate(true, policies(List.of(), List.of(), List.of("DOC-001")), List.of(), NOW).limitations())
                .singleElement().asString().contains("DOC-001").contains("exception");
        assertThat(evaluator.evaluate(true, CLEAN, List.of("DOCUMENTATION"), NOW).limitations())
                .singleElement().asString().contains("DOCUMENTATION").contains("degraded");
    }

    @Test
    void notReadyWinsOverLimitations() {
        assertThat(evaluator.evaluate(false, policies(List.of(), List.of("PRV-002"), List.of("DOC-001")), List.of("DOCUMENTATION"), NOW)
                .outcome()).isEqualTo("NOT_READY");
    }
}
