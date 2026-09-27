package com.agentic.urlshortener.orchestration.policy;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.agentic.urlshortener.common.util.CanonicalJson;
import com.agentic.urlshortener.orchestration.audit.AuditService;
import com.agentic.urlshortener.orchestration.domain.ActorType;
import com.agentic.urlshortener.orchestration.domain.AuditRecord;
import com.agentic.urlshortener.orchestration.domain.PolicyEvaluation;
import com.agentic.urlshortener.orchestration.domain.StageType;
import com.agentic.urlshortener.orchestration.repository.PolicyEvaluationRepository;

import tools.jackson.databind.JsonNode;

/**
 * Persists the outcomes of a compliance report as {@code policy_evaluation} rows, each with a
 * {@code POLICY_EVALUATED} audit event carrying the pinned policy-set version (FR-POL-06). Called by
 * the engine inside the transaction that records the compliance stage's result.
 */
@Component
public class PolicyEvaluationRecorder {

    private final PolicyEvaluationRepository evaluations;
    private final AuditService audit;

    public PolicyEvaluationRecorder(PolicyEvaluationRepository evaluations, AuditService audit) {
        this.evaluations = evaluations;
        this.audit = audit;
    }

    public void record(UUID runId, StageType stage, int generation, String complianceReport, String agentId, Instant now) {
        JsonNode report = CanonicalJson.parse(complianceReport);
        String version = report.path("policySetVersion").asString();
        for (JsonNode evaluation : report.path("evaluations")) {
            String exceptionId = evaluation.path("exceptionId").isNull() ? null : evaluation.path("exceptionId").asString(null);
            evaluations.save(PolicyEvaluation.create(runId, stage, generation, evaluation.path("policyId").asString(), version,
                    evaluation.path("severity").asString(), evaluation.path("outcome").asString(), evaluation.path("evidence").asString(),
                    exceptionId == null ? null : UUID.fromString(exceptionId), evaluation.path("simulated").asBoolean(false), now));
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("policySetVersion", version);
            details.put("severity", evaluation.path("severity").asString());
            details.put("generation", generation);
            audit.append(new AuditRecord(runId, ActorType.AGENT, agentId, "POLICY_EVALUATED", evaluation.path("policyId").asString(),
                    null, null, evaluation.path("outcome").asString(), evaluation.path("evidence").asString(), CanonicalJson.write(details)));
        }
    }
}
