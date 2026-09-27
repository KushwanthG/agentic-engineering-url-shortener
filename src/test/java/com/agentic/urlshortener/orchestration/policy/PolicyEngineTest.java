package com.agentic.urlshortener.orchestration.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.agentic.urlshortener.orchestration.domain.PolicyExceptionRecord;

/** T051: engine semantics — version pinning, one outcome per policy with evidence, blocking, exceptions, readiness. */
@Tag("FR-POL-01")
@Tag("FR-POL-02")
@Tag("FR-POL-03")
@Tag("FR-POL-05")
@Tag("FR-RDY-01")
class PolicyEngineTest {

    private static final Instant NOW = Instant.parse("2026-09-26T10:00:00Z");
    private final PolicySet policySet = new PolicySetLoader().current();

    /** Every policy passes except the listed ones, which fail. */
    private static PolicyEngine engine(PolicySet set, List<String> failing) {
        List<PolicyRule> rules = new ArrayList<>();
        for (PolicySet.PolicyDefinition policy : set.policies()) {
            rules.add(new PolicyRule() {
                @Override
                public String policyId() {
                    return policy.id();
                }

                @Override
                public RuleOutcome evaluate(PolicyContext context) {
                    return failing.contains(policy.id()) ? RuleOutcome.fail("scripted failure") : RuleOutcome.pass("scripted pass");
                }
            });
        }
        return new PolicyEngine(rules);
    }

    private static PolicyContext context() {
        return PolicyContext.builder(UUID.randomUUID()).build();
    }

    @Test
    void everyPolicyHasExactlyOneOutcomeWithEvidenceAndThePinnedVersion() {
        PolicyResult result = engine(policySet, List.of()).evaluate(policySet, "1.0.0", context(), List.of(), NOW);
        assertThat(result.policySetVersion()).isEqualTo("1.0.0");
        assertThat(result.evaluations()).hasSize(14)
                .extracting(PolicyResult.Evaluation::policyId).doesNotHaveDuplicates();
        assertThat(result.evaluations()).allSatisfy(e -> {
            assertThat(e.outcome()).isIn("PASS", "FAIL", "EXCEPTION_REQUESTED", "NOT_APPLICABLE");
            assertThat(e.evidence()).isNotBlank();
        });
        assertThat(result.blocked()).isFalse();
    }

    @Test
    void aMandatoryFailureBlocks() {
        PolicyResult result = engine(policySet, List.of("SEC-001")).evaluate(policySet, "1.0.0", context(), List.of(), NOW);
        assertThat(result.blocked()).isTrue();
        assertThat(result.blockingPolicies()).containsExactly("SEC-001");
    }

    @Test
    void anAdvisoryFailureIsRecordedButDoesNotBlock() {
        PolicyResult result = engine(policySet, List.of("AUT-001")).evaluate(policySet, "1.0.0", context(), List.of(), NOW);
        assertThat(result.blocked()).isFalse();
        assertThat(result.advisoryFailures()).containsExactly("AUT-001");
    }

    @Test
    void aPolicyWithoutARuleFailsSafely() {
        PolicyResult result = new PolicyEngine(List.of()).evaluate(policySet, "1.0.0", context(), List.of(), NOW);
        assertThat(result.evaluations()).allSatisfy(e -> assertThat(e.outcome()).isEqualTo("FAIL"));
        assertThat(result.evaluations().getFirst().evidence()).contains("no rule");
        assertThat(result.blocked()).isTrue();
    }

    @Test
    void anApprovedUnexpiredExceptionCoversAFailure() {
        UUID run = UUID.randomUUID();
        PolicyExceptionRecord exception = PolicyExceptionRecord.create(run, "LIC-001", "vendor license review pending", "this run",
                "manual review", "alice", NOW.minusSeconds(60), NOW.plusSeconds(3600));
        exception.decide(PolicyExceptionRecord.APPROVED, "bob", NOW.minusSeconds(30), "accepted temporarily");

        PolicyResult result = engine(policySet, List.of("LIC-001")).evaluate(policySet, "1.0.0", context(), List.of(exception), NOW);
        PolicyResult.Evaluation lic = result.evaluations().stream().filter(e -> e.policyId().equals("LIC-001")).findFirst().orElseThrow();
        assertThat(lic.outcome()).isEqualTo("EXCEPTION_REQUESTED");
        assertThat(lic.exceptionId()).isEqualTo(exception.getId());
        assertThat(result.blocked()).isFalse();
        assertThat(result.acceptedExceptions()).containsExactly("LIC-001");
    }

    @Test
    void anExpiredExceptionCountsAsUnapproved() {
        PolicyExceptionRecord expired = PolicyExceptionRecord.create(UUID.randomUUID(), "LIC-001", "r", "s", "c", "alice",
                NOW.minusSeconds(7200), NOW.minusSeconds(1));
        expired.decide(PolicyExceptionRecord.APPROVED, "bob", NOW.minusSeconds(3600), "ok");
        PolicyResult result = engine(policySet, List.of("LIC-001")).evaluate(policySet, "1.0.0", context(), List.of(expired), NOW);
        assertThat(result.blocked()).isTrue();
    }

    @Test
    void refusesToEvaluateAgainstADifferentVersionThanThePinnedOne() {
        assertThatThrownBy(() -> engine(policySet, List.of()).evaluate(policySet, "0.9.0", context(), List.of(), NOW))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("0.9.0");
    }

    @Test
    void readinessReflectsValidationBlockingAdvisoryAndDegradation() {
        ReadinessEvaluator readiness = new ReadinessEvaluator();
        PolicyResult clean = engine(policySet, List.of()).evaluate(policySet, "1.0.0", context(), List.of(), NOW);
        PolicyResult advisory = engine(policySet, List.of("AUT-001")).evaluate(policySet, "1.0.0", context(), List.of(), NOW);
        PolicyResult blocked = engine(policySet, List.of("TST-001")).evaluate(policySet, "1.0.0", context(), List.of(), NOW);

        assertThat(readiness.evaluate(true, clean, List.of(), NOW).outcome()).isEqualTo("READY");
        assertThat(readiness.evaluate(true, advisory, List.of(), NOW).outcome()).isEqualTo("READY_WITH_ACCEPTED_LIMITATIONS");
        assertThat(readiness.evaluate(true, clean, List.of("DOCUMENTATION"), NOW).limitations()).anyMatch(l -> l.contains("DOCUMENTATION"));
        assertThat(readiness.evaluate(true, blocked, List.of(), NOW).outcome()).isEqualTo("NOT_READY");
        assertThat(readiness.evaluate(false, clean, List.of(), NOW).outcome()).isEqualTo("NOT_READY");
        assertThat(readiness.evaluate(true, clean, List.of(), NOW).reasons()).isNotEmpty();
    }

    @Test
    void theLoadedPolicySetIsVersionOneWithFourteenPolicies() {
        assertThat(policySet.version()).isEqualTo("1.0.0");
        assertThat(policySet.policies()).extracting(PolicySet.PolicyDefinition::id).containsExactly("SEC-001", "SEC-002", "SEC-003",
                "PRV-001", "AUD-001", "AUD-002", "LIC-001", "TST-001", "TST-002", "DOC-001", "CHG-001", "CHG-002", "REL-001", "AUT-001");
        assertThat(policySet.policy("AUT-001").orElseThrow().mandatory()).isFalse();
    }
}
