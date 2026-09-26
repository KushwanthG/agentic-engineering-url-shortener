package com.agentic.urlshortener.orchestration.agent;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.agentic.urlshortener.common.util.CanonicalJson;
import com.agentic.urlshortener.orchestration.domain.StageType;
import com.agentic.urlshortener.orchestration.policy.PolicyContext;
import com.agentic.urlshortener.orchestration.policy.PolicyEngine;
import com.agentic.urlshortener.orchestration.policy.PolicyResult;
import com.agentic.urlshortener.orchestration.policy.PolicySetLoader;
import com.agentic.urlshortener.orchestration.policy.ReadinessEvaluator;
import com.agentic.urlshortener.orchestration.policy.RunFacts;

import tools.jackson.databind.JsonNode;

/**
 * COMPLIANCE_EVALUATION (FR-POL-01..06, FR-RDY-01): evaluates the policy set the run pinned against
 * the run's current artifacts and facts, and computes readiness. A mandatory failure without an
 * approved exception is returned as {@code PolicyBlocked}: the stage then waits for a policy-exception
 * decision and nothing downstream (release approval, release) can start. The agent only reads.
 */
@Component
public class ComplianceEvaluationAgent implements StageAgent {

    private final PolicyEngine engine;
    private final PolicySetLoader policySets;
    private final ReadinessEvaluator readiness;
    private final RunFacts facts;
    private final Clock clock;

    public ComplianceEvaluationAgent(PolicyEngine engine, PolicySetLoader policySets, ReadinessEvaluator readiness, RunFacts facts,
            Clock clock) {
        this.engine = engine;
        this.policySets = policySets;
        this.readiness = readiness;
        this.facts = facts;
        this.clock = clock;
    }

    @Override
    public StageType stageType() {
        return StageType.COMPLIANCE_EVALUATION;
    }

    @Override
    public String agentId() {
        return "compliance-evaluator@1.0";
    }

    @Override
    public Set<AgentPermission> permissions() {
        return Set.of();
    }

    @Override
    public StageResult execute(StageContext context) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        PolicyContext.Builder builder = facts.forRun(context.runId());
        context.inputs().forEach((type, input) -> builder.artifact(type, input.content()));
        PolicyResult result = engine.evaluate(policySets.current(), context.policySetVersion(), builder.build(),
                facts.exceptions(context.runId()), now);

        JsonNode validation = context.inputJson("VALIDATION_REPORT");
        ReadinessEvaluator.Readiness ready = readiness.evaluate(validation.path("passed").asBoolean(false), result,
                AgentInputs.strings(validation.path("degradedStages")), now);

        Map<String, Object> compliance = new LinkedHashMap<>();
        compliance.put("policySetVersion", result.policySetVersion());
        List<Map<String, Object>> evaluations = new ArrayList<>();
        for (PolicyResult.Evaluation evaluation : result.evaluations()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("policyId", evaluation.policyId());
            entry.put("severity", evaluation.severity());
            entry.put("outcome", evaluation.outcome());
            entry.put("evidence", evaluation.evidence());
            entry.put("exceptionId", evaluation.exceptionId() == null ? null : evaluation.exceptionId().toString());
            entry.put("simulated", evaluation.simulated());
            evaluations.add(entry);
        }
        compliance.put("evaluations", evaluations);
        compliance.put("blocked", result.blocked());
        compliance.put("blockingPolicies", result.blockingPolicies());

        Map<String, Object> readinessReport = new LinkedHashMap<>();
        readinessReport.put("outcome", ready.outcome());
        readinessReport.put("reasons", ready.reasons());
        readinessReport.put("limitations", ready.limitations());
        readinessReport.put("evaluatedAt", ready.evaluatedAt().toString());

        List<ArtifactDraft> drafts = List.of(ArtifactDraft.json("COMPLIANCE_REPORT", CanonicalJson.write(compliance)),
                ArtifactDraft.json("READINESS_REPORT", CanonicalJson.write(readinessReport)));
        if (result.blocked()) {
            return new StageResult.PolicyBlocked(drafts, result.blockingPolicies(),
                    "mandatory policies failed without an approved exception: " + result.blockingPolicies());
        }
        return new StageResult.Succeeded(drafts, "policy set " + result.policySetVersion() + " evaluated; readiness " + ready.outcome());
    }
}
