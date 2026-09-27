package com.agentic.urlshortener.orchestration.policy.rules;

import org.springframework.stereotype.Component;

import com.agentic.urlshortener.orchestration.policy.PolicyContext;
import com.agentic.urlshortener.orchestration.policy.PolicyRule;
import com.agentic.urlshortener.orchestration.policy.RuleOutcome;

/** AUD-002: the configured audit retention is at least 365 days. */
@Component
public class AuditRetentionRule implements PolicyRule {

    private static final int MINIMUM_DAYS = 365;

    @Override
    public String policyId() {
        return "AUD-002";
    }

    @Override
    public RuleOutcome evaluate(PolicyContext context) {
        return RuleOutcome.check(context.auditRetentionDays() >= MINIMUM_DAYS,
                "configured audit retention " + context.auditRetentionDays() + " days (minimum " + MINIMUM_DAYS + ")");
    }
}
