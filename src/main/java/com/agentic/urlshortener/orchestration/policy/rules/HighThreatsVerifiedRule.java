package com.agentic.urlshortener.orchestration.policy.rules;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.agentic.urlshortener.orchestration.policy.PolicyContext;
import com.agentic.urlshortener.orchestration.policy.PolicyRule;
import com.agentic.urlshortener.orchestration.policy.RuleOutcome;

import tools.jackson.databind.JsonNode;

/** SEC-003: every HIGH threat of the threat model is verified by a passing probe. */
@Component
public class HighThreatsVerifiedRule implements PolicyRule {

    @Override
    public String policyId() {
        return "SEC-003";
    }

    @Override
    public RuleOutcome evaluate(PolicyContext context) {
        JsonNode model = context.json("THREAT_MODEL").orElse(null);
        if (model == null || model.path("threats").isEmpty()) {
            return RuleOutcome.notApplicable("no threats were identified");
        }
        JsonNode report = context.json("SECURITY_REPORT").orElse(null);
        List<String> high = new ArrayList<>();
        List<String> unverified = new ArrayList<>();
        for (JsonNode threat : model.path("threats")) {
            if (!"HIGH".equals(threat.path("severity").asString())) {
                continue;
            }
            String id = threat.path("id").asString();
            high.add(id);
            boolean verified = false;
            if (report != null) {
                for (JsonNode result : report.path("results")) {
                    for (JsonNode verifies : result.path("verifies")) {
                        verified |= result.path("passed").asBoolean() && verifies.asString().equals(id);
                    }
                }
            }
            if (!verified) {
                unverified.add(id);
            }
        }
        if (high.isEmpty()) {
            return RuleOutcome.notApplicable("no HIGH threats");
        }
        return RuleOutcome.check(unverified.isEmpty(), unverified.isEmpty()
                ? "HIGH threats " + high + " verified by passing probes" : "HIGH threats without a passing probe: " + unverified);
    }
}
