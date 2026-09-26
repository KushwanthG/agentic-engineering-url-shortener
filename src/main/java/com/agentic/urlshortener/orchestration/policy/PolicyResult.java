package com.agentic.urlshortener.orchestration.policy;

import java.util.List;
import java.util.UUID;

/** Outcome of evaluating the pinned policy set for one run. */
public record PolicyResult(
        String policySetVersion,
        List<Evaluation> evaluations,
        boolean blocked,
        List<String> blockingPolicies,
        List<String> advisoryFailures,
        List<String> acceptedExceptions) {

    public PolicyResult {
        evaluations = List.copyOf(evaluations);
        blockingPolicies = List.copyOf(blockingPolicies);
        advisoryFailures = List.copyOf(advisoryFailures);
        acceptedExceptions = List.copyOf(acceptedExceptions);
    }

    /** One policy outcome with its evidence; {@code exceptionId} is set for {@code EXCEPTION_REQUESTED}. */
    public record Evaluation(String policyId, String title, String domain, String severity, String outcome, String evidence,
            UUID exceptionId, boolean simulated) {
    }
}
