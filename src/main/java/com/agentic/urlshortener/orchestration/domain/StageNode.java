package com.agentic.urlshortener.orchestration.domain;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import com.agentic.urlshortener.orchestration.engine.IllegalTransitionException;
import com.agentic.urlshortener.orchestration.engine.StageTransitions;

/**
 * One stage of a run's current plan. Every status change goes through {@link #transitionTo}, which
 * enforces the stage state machine; the version column makes concurrent gate decisions conflict.
 */
@Entity
@Table(name = "stage_node")
public class StageNode extends AssignedIdEntity {

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage_key", nullable = false, length = 40)
    private StageType stageKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage_type", nullable = false, length = 40)
    private StageType stageType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private StageStatus status;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private AwaitingType awaiting;

    @Column(name = "depends_on", nullable = false, length = 1000)
    private String dependsOn;

    @Column(nullable = false)
    private int generation;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "input_fingerprint", length = 64)
    private String inputFingerprint;

    @Column(nullable = false)
    private boolean reused;

    @Column(nullable = false)
    private boolean degraded;

    @Column(name = "skip_reason", length = 500)
    private String skipReason;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "decision_deadline")
    private Instant decisionDeadline;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_failure_class", length = 16)
    private FailureClass lastFailureClass;

    @Column(name = "last_failure_reason", length = 1000)
    private String lastFailureReason;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Version
    private Long version;

    protected StageNode() {
    }

    private StageNode(UUID id) {
        super(id);
    }

    public static StageNode create(UUID runId, StageType type, List<StageType> dependsOn) {
        StageNode node = new StageNode(UUID.randomUUID());
        node.runId = runId;
        node.stageKey = type;
        node.stageType = type;
        node.status = StageStatus.PENDING;
        node.dependsOn = dependsOn.stream().map(Enum::name).collect(Collectors.joining(","));
        node.generation = 1;
        return node;
    }

    /** Applies a transition allowed by the stage state machine; a prohibited one throws. */
    public void transitionTo(StageStatus to) {
        StageTransitions.check(status, to);
        status = to;
        if (to != StageStatus.AWAITING_DECISION) {
            awaiting = null;
            decisionDeadline = null;
        }
    }

    public void skip(String reason) {
        transitionTo(StageStatus.SKIPPED);
        skipReason = Texts.truncate(reason, 500);
    }

    public void awaitDecision(AwaitingType awaitingType, Instant deadline) {
        transitionTo(StageStatus.AWAITING_DECISION);
        awaiting = awaitingType;
        decisionDeadline = deadline;
    }

    /** Starts the next attempt of the current generation and returns its attempt number. */
    public int startAttempt(Instant now, String fingerprint) {
        transitionTo(StageStatus.RUNNING);
        attempts++;
        inputFingerprint = fingerprint;
        nextAttemptAt = null;
        if (startedAt == null) {
            startedAt = now;
        }
        return attempts;
    }

    /** Completes an agent stage. Gates cannot use this path: see {@link #succeedGate}. */
    public void succeed(Instant now) {
        if (stageType.isGate()) {
            throw new IllegalTransitionException("Gate " + stageKey + " can only succeed through a valid human approval decision");
        }
        transitionTo(StageStatus.SUCCEEDED);
        finishedAt = now;
    }

    /**
     * Completes a gate. The transition guard requires a valid, human, approving decision recorded for
     * this run and this gate (plan.md §5, anti-bypass guarantees).
     */
    public void succeedGate(Decision decision, Instant now) {
        boolean valid = stageType.isGate() && decision.isValid() && "APPROVED".equals(decision.getOutcome())
                && decision.getActorType() == ActorType.HUMAN && decision.getStageKey() == stageKey && runId.equals(decision.getRunId());
        if (!valid) {
            throw new IllegalTransitionException("Gate " + stageKey + " can only succeed through a valid human approval decision");
        }
        transitionTo(StageStatus.SUCCEEDED);
        finishedAt = now;
    }

    public void fail(FailureClass failureClass, String reason, Instant now) {
        transitionTo(StageStatus.FAILED);
        recordFailure(failureClass, reason);
        finishedAt = now;
    }

    /**
     * Returns the stage to {@code PENDING} as a new generation: results of earlier attempts become stale
     * and are discarded, and the stage is scheduled again (approval re-opened, re-plan).
     */
    public void reopen() {
        transitionTo(StageStatus.PENDING);
        generation++;
        attempts = 0;
        inputFingerprint = null;
        reused = false;
        degraded = false;
        skipReason = null;
        nextAttemptAt = null;
        lastFailureClass = null;
        lastFailureReason = null;
        startedAt = null;
        finishedAt = null;
    }

    public void recordFailure(FailureClass failureClass, String reason) {
        lastFailureClass = failureClass;
        lastFailureReason = Texts.truncate(reason, 1000);
    }

    public void scheduleRetry(Instant at) {
        transitionTo(StageStatus.RETRY_WAIT);
        nextAttemptAt = at;
    }

    public void markDegraded() {
        degraded = true;
    }

    public void markReused(Instant now) {
        if (stageType.isGate()) {
            throw new IllegalTransitionException("Gate " + stageKey + " cannot be reused; it needs a human decision");
        }
        transitionTo(StageStatus.SUCCEEDED);
        reused = true;
        finishedAt = now;
    }

    public UUID getRunId() {
        return runId;
    }

    public StageType getStageKey() {
        return stageKey;
    }

    public StageType getStageType() {
        return stageType;
    }

    public StageStatus getStatus() {
        return status;
    }

    public AwaitingType getAwaiting() {
        return awaiting;
    }

    public List<StageType> getDependsOn() {
        return dependsOn.isBlank() ? List.of() : Arrays.stream(dependsOn.split(",")).map(StageType::valueOf).toList();
    }

    public int getGeneration() {
        return generation;
    }

    public int getAttempts() {
        return attempts;
    }

    public String getInputFingerprint() {
        return inputFingerprint;
    }

    public boolean isReused() {
        return reused;
    }

    public boolean isDegraded() {
        return degraded;
    }

    public String getSkipReason() {
        return skipReason;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public Instant getDecisionDeadline() {
        return decisionDeadline;
    }

    public FailureClass getLastFailureClass() {
        return lastFailureClass;
    }

    public String getLastFailureReason() {
        return lastFailureReason;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public Long getVersion() {
        return version;
    }
}
