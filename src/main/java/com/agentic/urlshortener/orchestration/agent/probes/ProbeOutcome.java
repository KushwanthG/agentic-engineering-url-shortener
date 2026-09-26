package com.agentic.urlshortener.orchestration.agent.probes;

/** Result of one probe with the observed evidence. */
public record ProbeOutcome(boolean passed, String evidence) {

    public static ProbeOutcome pass(String evidence) {
        return new ProbeOutcome(true, evidence);
    }

    public static ProbeOutcome fail(String evidence) {
        return new ProbeOutcome(false, evidence);
    }

    public static ProbeOutcome check(boolean passed, String evidence) {
        return new ProbeOutcome(passed, evidence);
    }
}
