package com.agentic.urlshortener.orchestration.policy.rules;

import org.springframework.stereotype.Component;

import com.agentic.urlshortener.orchestration.policy.PolicyContext;
import com.agentic.urlshortener.orchestration.policy.PolicyRule;
import com.agentic.urlshortener.orchestration.policy.RuleOutcome;

import tools.jackson.databind.JsonNode;

/** TST-002: for a change to existing behavior, the regression suite passed. */
@Component
public class RegressionPassedRule implements PolicyRule {

    @Override
    public String policyId() {
        return "TST-002";
    }

    @Override
    public RuleOutcome evaluate(PolicyContext context) {
        if (!context.changesExistingBehavior()) {
            return RuleOutcome.notApplicable("classification " + context.classification() + " does not change existing behavior");
        }
        JsonNode report = context.json("REGRESSION_REPORT").orElse(null);
        if (report == null) {
            return RuleOutcome.fail("no REGRESSION_REPORT for a change to existing behavior");
        }
        return RuleOutcome.check(report.path("failed").asInt() == 0,
                "regression suite: " + report.path("passed").asInt() + " passed, " + report.path("failed").asInt() + " failed");
    }
}
