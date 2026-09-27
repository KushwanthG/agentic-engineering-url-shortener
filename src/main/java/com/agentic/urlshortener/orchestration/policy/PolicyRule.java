package com.agentic.urlshortener.orchestration.policy;

/**
 * The executable check of one policy of the policy set (ADR-019). Rules are side-effect free: they
 * read the facts of a {@link PolicyContext} and return an outcome with evidence.
 */
public interface PolicyRule {

    String policyId();

    RuleOutcome evaluate(PolicyContext context);
}
