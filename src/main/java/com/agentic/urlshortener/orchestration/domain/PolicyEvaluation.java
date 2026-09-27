package com.agentic.urlshortener.orchestration.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/** One policy outcome of one stage generation, with the pinned policy-set version (FR-POL-01/02). */
@Entity
@Table(name = "policy_evaluation")
public class PolicyEvaluation extends AssignedIdEntity {

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage_key", nullable = false, length = 40)
    private StageType stageKey;

    @Column(nullable = false)
    private int generation;

    @Column(name = "policy_id", nullable = false, length = 20)
    private String policyId;

    @Column(name = "policy_set_version", nullable = false, length = 20)
    private String policySetVersion;

    @Column(nullable = false, length = 12)
    private String severity;

    @Column(nullable = false, length = 24)
    private String outcome;

    @Column(nullable = false, length = 2000)
    private String evidence;

    @Column(name = "exception_id")
    private UUID exceptionId;

    @Column(nullable = false)
    private boolean simulated;

    @Column(name = "evaluated_at", nullable = false)
    private Instant evaluatedAt;

    protected PolicyEvaluation() {
    }

    private PolicyEvaluation(UUID id) {
        super(id);
    }

    public static PolicyEvaluation create(UUID runId, StageType stageKey, int generation, String policyId, String policySetVersion,
            String severity, String outcome, String evidence, UUID exceptionId, boolean simulated, Instant evaluatedAt) {
        PolicyEvaluation evaluation = new PolicyEvaluation(UUID.randomUUID());
        evaluation.runId = runId;
        evaluation.stageKey = stageKey;
        evaluation.generation = generation;
        evaluation.policyId = policyId;
        evaluation.policySetVersion = policySetVersion;
        evaluation.severity = severity;
        evaluation.outcome = outcome;
        evaluation.evidence = Texts.truncate(evidence, 2000);
        evaluation.exceptionId = exceptionId;
        evaluation.simulated = simulated;
        evaluation.evaluatedAt = evaluatedAt;
        return evaluation;
    }

    public UUID getRunId() {
        return runId;
    }

    public StageType getStageKey() {
        return stageKey;
    }

    public int getGeneration() {
        return generation;
    }

    public String getPolicyId() {
        return policyId;
    }

    public String getPolicySetVersion() {
        return policySetVersion;
    }

    public String getSeverity() {
        return severity;
    }

    public String getOutcome() {
        return outcome;
    }

    public String getEvidence() {
        return evidence;
    }

    public UUID getExceptionId() {
        return exceptionId;
    }

    public boolean isSimulated() {
        return simulated;
    }

    public Instant getEvaluatedAt() {
        return evaluatedAt;
    }
}
