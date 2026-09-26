package com.agentic.urlshortener.orchestration.policy.rules;

import org.springframework.stereotype.Component;

import com.agentic.urlshortener.orchestration.policy.PolicyContext;
import com.agentic.urlshortener.orchestration.policy.PolicyRule;
import com.agentic.urlshortener.orchestration.policy.RuleOutcome;

import tools.jackson.databind.JsonNode;

/** SEC-001: every URL-validation probe of the security verification report passed. */
@Component
public class UrlValidationProbesRule implements PolicyRule {

    @Override
    public String policyId() {
        return "SEC-001";
    }

    @Override
    public RuleOutcome evaluate(PolicyContext context) {
        JsonNode report = context.json("SECURITY_REPORT").orElse(null);
        if (report == null) {
            return RuleOutcome.fail("no SECURITY_REPORT: URL validation was not verified");
        }
        for (JsonNode result : report.path("results")) {
            if (result.path("probeId").asString().equals("SEC-URL-CATALOG")) {
                return RuleOutcome.check(result.path("passed").asBoolean(), "SEC-URL-CATALOG " + (result.path("passed").asBoolean()
                        ? "passed: " : "failed: ") + result.path("evidence").asString());
            }
        }
        return RuleOutcome.fail("the security report contains no SEC-URL-CATALOG result");
    }
}
