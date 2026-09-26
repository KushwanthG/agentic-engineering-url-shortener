package com.agentic.urlshortener.orchestration.policy;

/** {@code PASS}, {@code FAIL}, or {@code NOT_APPLICABLE}, always with evidence (FR-POL-02). */
public record RuleOutcome(String outcome, String evidence) {

    public static RuleOutcome pass(String evidence) {
        return new RuleOutcome("PASS", evidence);
    }

    public static RuleOutcome fail(String evidence) {
        return new RuleOutcome("FAIL", evidence);
    }

    public static RuleOutcome notApplicable(String evidence) {
        return new RuleOutcome("NOT_APPLICABLE", evidence);
    }

    public static RuleOutcome check(boolean passed, String evidence) {
        return passed ? pass(evidence) : fail(evidence);
    }
}
