package com.agentic.urlshortener.orchestration.policy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

/**
 * Release readiness (FR-RDY-01): {@code NOT_READY} when validation did not pass or a mandatory policy
 * fails without an approved exception; {@code READY_WITH_ACCEPTED_LIMITATIONS} when an advisory
 * policy failed, an exception was used, or a stage ran degraded; otherwise {@code READY}.
 */
@Component
public class ReadinessEvaluator {

    /** Readiness with its reasons and the limitations a release owner accepts when approving. */
    public record Readiness(String outcome, List<String> reasons, List<String> limitations, Instant evaluatedAt) {
    }

    public Readiness evaluate(boolean validationPassed, PolicyResult policies, List<String> degradedStages, Instant now) {
        List<String> reasons = new ArrayList<>();
        List<String> limitations = new ArrayList<>();
        if (!validationPassed) {
            reasons.add("validation did not pass");
        }
        if (policies.blocked()) {
            reasons.add("mandatory policies failed without an approved exception: " + policies.blockingPolicies());
        }
        if (!reasons.isEmpty()) {
            return new Readiness("NOT_READY", reasons, limitations, now);
        }
        policies.advisoryFailures().forEach(p -> limitations.add("advisory policy " + p + " failed"));
        policies.acceptedExceptions().forEach(p -> limitations.add("policy " + p + " covered by an approved exception"));
        degradedStages.forEach(s -> limitations.add("stage " + s + " completed in degraded (fallback) mode"));
        if (limitations.isEmpty()) {
            reasons.add("validation passed and every mandatory policy passed or is not applicable (policy set "
                    + policies.policySetVersion() + ")");
            return new Readiness("READY", reasons, limitations, now);
        }
        reasons.add("validation passed; mandatory policies satisfied; limitations must be accepted at release approval");
        return new Readiness("READY_WITH_ACCEPTED_LIMITATIONS", reasons, limitations, now);
    }
}
