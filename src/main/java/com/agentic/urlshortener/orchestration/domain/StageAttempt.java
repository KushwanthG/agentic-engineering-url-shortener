package com.agentic.urlshortener.orchestration.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * One execution attempt of a stage generation. Start and end times make parallel execution and
 * synchronization verifiable (FR-AUD-06); the scheduling cycle groups attempts dispatched together.
 */
@Entity
@Table(name = "stage_attempt")
public class StageAttempt extends AssignedIdEntity {

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage_key", nullable = false, length = 40)
    private StageType stageKey;

    @Column(nullable = false)
    private int generation;

    @Column(name = "attempt_no", nullable = false)
    private int attemptNo;

    @Column(name = "agent_id", nullable = false, length = 80)
    private String agentId;

    @Column(nullable = false)
    private boolean fallback;

    @Column(name = "simulated_fault", length = 40)
    private String simulatedFault;

    @Column(name = "scheduling_cycle")
    private Long schedulingCycle;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private AttemptOutcome outcome;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_class", length = 16)
    private FailureClass failureClass;

    @Column(length = 2000)
    private String error;

    @Column(name = "input_fingerprint", length = 64)
    private String inputFingerprint;

    protected StageAttempt() {
    }

    private StageAttempt(UUID id) {
        super(id);
    }

    public static StageAttempt start(UUID runId, StageType stageKey, int generation, int attemptNo, String agentId,
            boolean fallback, String simulatedFault, Long schedulingCycle, Instant startedAt, String inputFingerprint) {
        StageAttempt attempt = new StageAttempt(UUID.randomUUID());
        attempt.runId = runId;
        attempt.stageKey = stageKey;
        attempt.generation = generation;
        attempt.attemptNo = attemptNo;
        attempt.agentId = agentId;
        attempt.fallback = fallback;
        attempt.simulatedFault = simulatedFault;
        attempt.schedulingCycle = schedulingCycle;
        attempt.startedAt = startedAt;
        attempt.inputFingerprint = inputFingerprint;
        return attempt;
    }

    public void finish(AttemptOutcome outcome, FailureClass failureClass, String error, Instant finishedAt) {
        this.outcome = outcome;
        this.failureClass = failureClass;
        this.error = Texts.truncate(error, 2000);
        this.finishedAt = finishedAt;
    }

    public boolean isFinished() {
        return finishedAt != null;
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

    public int getAttemptNo() {
        return attemptNo;
    }

    public String getAgentId() {
        return agentId;
    }

    public boolean isFallback() {
        return fallback;
    }

    public String getSimulatedFault() {
        return simulatedFault;
    }

    public Long getSchedulingCycle() {
        return schedulingCycle;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public AttemptOutcome getOutcome() {
        return outcome;
    }

    public FailureClass getFailureClass() {
        return failureClass;
    }

    public String getError() {
        return error;
    }

    public String getInputFingerprint() {
        return inputFingerprint;
    }
}
