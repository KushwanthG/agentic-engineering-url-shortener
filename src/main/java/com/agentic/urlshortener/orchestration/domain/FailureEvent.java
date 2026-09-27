package com.agentic.urlshortener.orchestration.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * One failure episode of a stage generation: from the first failed attempt to recovery or terminal
 * failure. MTTR is computed from recovered episodes (plan.md §7).
 */
@Entity
@Table(name = "failure_event")
public class FailureEvent extends AssignedIdEntity {

    public static final String OPEN = "OPEN";
    public static final String RECOVERED = "RECOVERED";
    public static final String UNRECOVERED = "UNRECOVERED";

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage_key", nullable = false, length = 40)
    private StageType stageKey;

    @Column(nullable = false)
    private int generation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private FailureClass classification;

    @Column(nullable = false, length = 40)
    private String cause;

    @Column(nullable = false)
    private boolean simulated;

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;

    @Column(name = "recovery_started_at")
    private Instant recoveryStartedAt;

    @Column(name = "recovery_completed_at")
    private Instant recoveryCompletedAt;

    @Column(length = 20)
    private String mechanism;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "excluded_reason", length = 200)
    private String excludedReason;

    protected FailureEvent() {
    }

    private FailureEvent(UUID id) {
        super(id);
    }

    public static FailureEvent open(UUID runId, StageType stageKey, int generation, FailureClass classification, String cause,
            boolean simulated, Instant detectedAt) {
        FailureEvent event = new FailureEvent(UUID.randomUUID());
        event.runId = runId;
        event.stageKey = stageKey;
        event.generation = generation;
        event.classification = classification;
        event.cause = cause;
        event.simulated = simulated;
        event.detectedAt = detectedAt;
        event.status = OPEN;
        return event;
    }

    public void recoveryStarted(Instant at, String mechanism) {
        if (recoveryStartedAt == null) {
            recoveryStartedAt = at;
        }
        this.mechanism = mechanism;
    }

    public void recovered(Instant at) {
        recoveryCompletedAt = at;
        status = RECOVERED;
    }

    public void unrecovered() {
        status = UNRECOVERED;
    }

    public void exclude(String reason) {
        excludedReason = Texts.truncate(reason, 200);
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

    public FailureClass getClassification() {
        return classification;
    }

    public String getCause() {
        return cause;
    }

    public boolean isSimulated() {
        return simulated;
    }

    public Instant getDetectedAt() {
        return detectedAt;
    }

    public Instant getRecoveryStartedAt() {
        return recoveryStartedAt;
    }

    public Instant getRecoveryCompletedAt() {
        return recoveryCompletedAt;
    }

    public String getMechanism() {
        return mechanism;
    }

    public String getStatus() {
        return status;
    }

    public String getExcludedReason() {
        return excludedReason;
    }
}
