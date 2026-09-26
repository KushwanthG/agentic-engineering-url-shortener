package com.agentic.urlshortener.orchestration.agent.probes;

import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * An executable check against the running service. Acceptance probes declare the pattern of the
 * acceptance criteria they verify; security probes are referenced by id from the threat model.
 */
public record AcceptanceProbe(String id, String capabilityId, String description, Pattern verifies,
        Function<ProbeContext, ProbeOutcome> check) {

    /** Whether this probe verifies an acceptance criterion with the given text. */
    public boolean verifiesCriterion(String criterionText) {
        return verifies != null && verifies.matcher(criterionText).find();
    }

    public ProbeOutcome run(ProbeContext context) {
        try {
            return check.apply(context);
        } catch (RuntimeException e) {
            return ProbeOutcome.fail("probe raised " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    static Pattern pattern(String regex) {
        return Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
    }
}
