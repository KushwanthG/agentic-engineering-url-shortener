package com.agentic.urlshortener.orchestration.policy.rules;

import org.springframework.stereotype.Component;

import com.agentic.urlshortener.orchestration.policy.PolicyContext;
import com.agentic.urlshortener.orchestration.policy.PolicyRule;
import com.agentic.urlshortener.orchestration.policy.RuleOutcome;

/** CHG-001: a material change has a valid architecture approval bound to the current design fingerprint. */
@Component
public class ArchitectureApprovalBoundRule implements PolicyRule {

    @Override
    public String policyId() {
        return "CHG-001";
    }

    @Override
    public RuleOutcome evaluate(PolicyContext context) {
        boolean material = context.json("DESIGN").map(d -> d.path("materialChange").asBoolean()).orElse(false);
        if (!material) {
            return RuleOutcome.notApplicable("the design is not a material change");
        }
        String design = context.fingerprint("DESIGN").orElse("");
        boolean bound = context.decisions().stream().anyMatch(d -> "ARCHITECTURE_APPROVAL".equals(d.stageKey())
                && "APPROVED".equals(d.outcome()) && design.equals(d.boundFingerprints().get("DESIGN")));
        return RuleOutcome.check(bound, bound ? "valid architecture approval bound to design " + design.substring(0, 12)
                : "no valid architecture approval bound to the current design " + (design.isEmpty() ? "" : design.substring(0, 12)));
    }
}
