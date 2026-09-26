package com.agentic.urlshortener.orchestration.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.agentic.urlshortener.support.IntegrationTest;

/**
 * T079 guardrail against drift: every policy of the pinned set has exactly one production rule, and no
 * rule exists for a policy the set does not define (docs/governance/policy-set.md mirrors the set).
 */
@IntegrationTest
@Tag("FR-POL-01")
@Tag("FR-POL-02")
class PolicySetCoverageTest {

    @Autowired private List<PolicyRule> rules;
    @Autowired private PolicySetLoader policySets;

    @Test
    void everyPolicyOfTheSetHasExactlyOneRule() {
        List<String> policyIds = policySets.current().policies().stream().map(PolicySet.PolicyDefinition::id).toList();
        List<String> ruleIds = rules.stream().map(PolicyRule::policyId).toList();

        assertThat(ruleIds).doesNotHaveDuplicates().containsExactlyInAnyOrderElementsOf(policyIds);
        assertThat(policySets.current().version()).isEqualTo("1.0.0");
    }
}
