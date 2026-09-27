package com.agentic.urlshortener.orchestration.policy.rules;

import org.springframework.stereotype.Component;

import com.agentic.urlshortener.orchestration.policy.PolicyContext;
import com.agentic.urlshortener.orchestration.policy.PolicyRule;
import com.agentic.urlshortener.orchestration.policy.RuleOutcome;

import tools.jackson.databind.JsonNode;

/** CHG-002: for a change to existing behavior, an impact analysis is present and not degraded. */
@Component
public class ImpactAnalysisCompleteRule implements PolicyRule {

    @Override
    public String policyId() {
        return "CHG-002";
    }

    @Override
    public RuleOutcome evaluate(PolicyContext context) {
        if (!context.changesExistingBehavior()) {
            return RuleOutcome.notApplicable("classification " + context.classification() + " does not change existing behavior");
        }
        JsonNode impact = context.json("IMPACT_ANALYSIS").orElse(null);
        if (impact == null) {
            return RuleOutcome.fail("no IMPACT_ANALYSIS for a change to existing behavior");
        }
        boolean degraded = impact.path("degraded").asBoolean(false);
        return RuleOutcome.check(!degraded, degraded ? "impact analysis is degraded (catalog-only)"
                : "impact analysis present (" + impact.path("method").asString("source scan") + ")");
    }
}
