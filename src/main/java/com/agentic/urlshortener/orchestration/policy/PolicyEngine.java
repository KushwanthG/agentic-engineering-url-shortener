package com.agentic.urlshortener.orchestration.policy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.agentic.urlshortener.orchestration.domain.PolicyExceptionRecord;

/**
 * Evaluates the policy set pinned by a run (FR-POL-01..05, ADR-019). Each policy yields exactly one
 * outcome with evidence. A mandatory {@code FAIL} blocks unless an approved, unexpired exception
 * covers it ({@code EXCEPTION_REQUESTED}); an advisory {@code FAIL} is reported but never blocks. A
 * policy without an implementing rule fails: absence of a check is never a pass.
 */
@Component
public class PolicyEngine {

    private final Map<String, PolicyRule> rules;

    public PolicyEngine(List<PolicyRule> rules) {
        this.rules = rules.stream().collect(Collectors.toUnmodifiableMap(PolicyRule::policyId, Function.identity()));
    }

    public PolicyResult evaluate(PolicySet policySet, String pinnedVersion, PolicyContext context, List<PolicyExceptionRecord> exceptions,
            Instant now) {
        if (!policySet.version().equals(pinnedVersion)) {
            throw new IllegalStateException("the run pinned policy set " + pinnedVersion + " but " + policySet.version() + " is loaded");
        }
        List<PolicyResult.Evaluation> evaluations = new ArrayList<>();
        List<String> blocking = new ArrayList<>();
        List<String> advisory = new ArrayList<>();
        List<String> accepted = new ArrayList<>();
        for (PolicySet.PolicyDefinition policy : policySet.policies()) {
            PolicyRule rule = rules.get(policy.id());
            boolean simulated = context.simulatedPolicyFailures() != null && context.simulatedPolicyFailures().contains(policy.id());
            RuleOutcome outcome = simulated
                    ? RuleOutcome.fail("simulated policy failure (fault injection; demonstration input, not a real violation)")
                    : rule == null ? RuleOutcome.fail("no rule implements " + policy.id()) : safely(rule, context);
            String result = outcome.outcome();
            String evidence = outcome.evidence();
            UUID exceptionId = null;
            if ("FAIL".equals(result)) {
                Optional<PolicyExceptionRecord> covering = exceptions.stream()
                        .filter(e -> e.getPolicyId().equals(policy.id()) && e.isEffectiveAt(now)).findFirst();
                if (covering.isPresent()) {
                    result = "EXCEPTION_REQUESTED";
                    exceptionId = covering.get().getId();
                    evidence = evidence + "; covered by approved exception " + exceptionId + " until " + covering.get().getExpiresAt();
                    accepted.add(policy.id());
                } else if (policy.mandatory()) {
                    blocking.add(policy.id());
                } else {
                    advisory.add(policy.id());
                }
            }
            evaluations.add(new PolicyResult.Evaluation(policy.id(), policy.title(), policy.domain(), policy.severity(), result, evidence,
                    exceptionId, simulated));
        }
        return new PolicyResult(pinnedVersion, evaluations, !blocking.isEmpty(), blocking, advisory, accepted);
    }

    private static RuleOutcome safely(PolicyRule rule, PolicyContext context) {
        try {
            return rule.evaluate(context);
        } catch (RuntimeException e) {
            return RuleOutcome.fail("rule " + rule.policyId() + " could not evaluate: " + e.getClass().getSimpleName());
        }
    }
}
