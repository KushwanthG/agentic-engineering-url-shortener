package com.agentic.urlshortener.orchestration.policy.rules;

import org.springframework.stereotype.Component;

import com.agentic.urlshortener.orchestration.policy.PolicyContext;
import com.agentic.urlshortener.orchestration.policy.PolicyRule;
import com.agentic.urlshortener.orchestration.policy.RuleOutcome;

import tools.jackson.databind.JsonNode;

/** TST-001: every acceptance criterion is verified by at least one passing probe. */
@Component
public class AcceptanceVerifiedRule implements PolicyRule {

    @Override
    public String policyId() {
        return "TST-001";
    }

    @Override
    public RuleOutcome evaluate(PolicyContext context) {
        JsonNode report = context.json("TEST_REPORT").orElse(null);
        if (report == null) {
            return RuleOutcome.fail("no TEST_REPORT: acceptance criteria were not verified");
        }
        boolean passed = report.path("unverifiedCriteria").isEmpty() && report.path("failed").asInt() == 0;
        return RuleOutcome.check(passed, report.path("passed").asInt() + " probes passed, " + report.path("failed").asInt()
                + " failed; unverified criteria " + report.path("unverifiedCriteria"));
    }
}
