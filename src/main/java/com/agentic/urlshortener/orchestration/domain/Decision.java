package com.agentic.urlshortener.orchestration.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * A recorded decision: who decided what, in which role, why, and on which artifact fingerprints
 * (FR-GOV-09). Decisions are never deleted; invalidation keeps the row and records the reason.
 */
@Entity
@Table(name = "decision")
public class Decision extends AssignedIdEntity {

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage_key", length = 40)
    private StageType stageKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision_type", nullable = false, length = 40)
    private DecisionType decisionType;

    @Column(nullable = false, length = 40)
    private String outcome;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, length = 16)
    private ActorType actorType;

    @Column(name = "actor_id", nullable = false, length = 64)
    private String actorId;

    @Column(name = "actor_role", length = 32)
    private String actorRole;

    @Column(length = 2000)
    private String rationale;

    @Column(length = 100_000)
    private String payload;

    @Column(name = "bound_fingerprints", length = 10_000)
    private String boundFingerprints;

    @Column(nullable = false)
    private boolean valid;

    @Column(name = "invalidated_reason", length = 500)
    private String invalidatedReason;

    @Column
    private UUID supersedes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Decision() {
    }

    private Decision(UUID id) {
        super(id);
    }

    public static Decision create(UUID runId, StageType stageKey, DecisionType decisionType, String outcome, ActorType actorType,
            String actorId, String actorRole, String rationale, String payload, String boundFingerprints, Instant createdAt) {
        Decision decision = new Decision(UUID.randomUUID());
        decision.runId = runId;
        decision.stageKey = stageKey;
        decision.decisionType = decisionType;
        decision.outcome = outcome;
        decision.actorType = actorType;
        decision.actorId = actorId;
        decision.actorRole = actorRole;
        decision.rationale = Texts.truncate(rationale, 2000);
        decision.payload = payload;
        decision.boundFingerprints = boundFingerprints;
        decision.valid = true;
        decision.createdAt = createdAt;
        return decision;
    }

    public void invalidate(String reason) {
        valid = false;
        invalidatedReason = Texts.truncate(reason, 500);
    }

    public void supersedes(UUID previous) {
        supersedes = previous;
    }

    public UUID getRunId() {
        return runId;
    }

    public StageType getStageKey() {
        return stageKey;
    }

    public DecisionType getDecisionType() {
        return decisionType;
    }

    public String getOutcome() {
        return outcome;
    }

    public ActorType getActorType() {
        return actorType;
    }

    public String getActorId() {
        return actorId;
    }

    public String getActorRole() {
        return actorRole;
    }

    public String getRationale() {
        return rationale;
    }

    public String getPayload() {
        return payload;
    }

    public String getBoundFingerprints() {
        return boundFingerprints;
    }

    public boolean isValid() {
        return valid;
    }

    public String getInvalidatedReason() {
        return invalidatedReason;
    }

    public UUID getSupersedes() {
        return supersedes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
