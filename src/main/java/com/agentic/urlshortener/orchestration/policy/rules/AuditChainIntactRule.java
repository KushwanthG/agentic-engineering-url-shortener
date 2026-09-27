package com.agentic.urlshortener.orchestration.policy.rules;

import org.springframework.stereotype.Component;

import com.agentic.urlshortener.orchestration.policy.PolicyContext;
import com.agentic.urlshortener.orchestration.policy.PolicyRule;
import com.agentic.urlshortener.orchestration.policy.RuleOutcome;

/** AUD-001: the run's audit chain verifies. */
@Component
public class AuditChainIntactRule implements PolicyRule {

    @Override
    public String policyId() {
        return "AUD-001";
    }

    @Override
    public RuleOutcome evaluate(PolicyContext context) {
        return RuleOutcome.check(context.auditValid(), context.auditEvidence());
    }
}
