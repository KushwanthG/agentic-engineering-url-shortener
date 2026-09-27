package com.agentic.urlshortener.orchestration.policy.rules;

import org.springframework.stereotype.Component;

import com.agentic.urlshortener.orchestration.policy.PolicyContext;
import com.agentic.urlshortener.orchestration.policy.PolicyRule;
import com.agentic.urlshortener.orchestration.policy.RuleOutcome;

import tools.jackson.databind.JsonNode;

/** REL-001: the design contains a release plan and a rollback plan. */
@Component
public class ReleasePlanRule implements PolicyRule {

    @Override
    public String policyId() {
        return "REL-001";
    }

    @Override
    public RuleOutcome evaluate(PolicyContext context) {
        JsonNode design = context.json("DESIGN").orElse(null);
        if (design == null) {
            return RuleOutcome.fail("no DESIGN artifact");
        }
        boolean release = !design.path("releasePlan").path("capability").asString("").isBlank();
        boolean rollback = !design.path("rollbackPlan").asString("").isBlank();
        return RuleOutcome.check(release && rollback, "release plan " + (release ? "present" : "missing") + "; rollback plan "
                + (rollback ? "present" : "missing"));
    }
}
