package com.agentic.urlshortener.orchestration.policy.rules;

import org.springframework.stereotype.Component;

import com.agentic.urlshortener.orchestration.policy.PolicyContext;
import com.agentic.urlshortener.orchestration.policy.PolicyRule;
import com.agentic.urlshortener.orchestration.policy.RuleOutcome;

/** AUT-001 (advisory): autonomy budget usage is below 80 percent. */
@Component
public class AutonomyHeadroomRule implements PolicyRule {

    private static final double LIMIT = 0.8;

    @Override
    public String policyId() {
        return "AUT-001";
    }

    @Override
    public RuleOutcome evaluate(PolicyContext context) {
        double usage = context.maxAttempts() <= 0 ? 1 : (double) context.attemptsUsed() / context.maxAttempts();
        return RuleOutcome.check(usage < LIMIT, context.attemptsUsed() + " of " + context.maxAttempts() + " stage attempts used ("
                + Math.round(usage * 100) + "%, limit " + Math.round(LIMIT * 100) + "%)");
    }
}
