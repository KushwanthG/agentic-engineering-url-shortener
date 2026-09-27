package com.agentic.urlshortener.orchestration.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import com.agentic.urlshortener.orchestration.engine.RunTransitions;

/** One workflow run: the lifecycle of one requirement through the governed plan (data-model.md). */
@Entity
@Table(name = "workflow_run")
public class WorkflowRun extends AssignedIdEntity {

    @Column(name = "requirement_ref", length = 64)
    private String requirementRef;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "requested_by", nullable = false, length = 64)
    private String requestedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Classification classification;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RunStatus status;

    @Column(name = "current_plan_version", nullable = false)
    private int currentPlanVersion;

    @Column(name = "current_requirement_version", nullable = false)
    private int currentRequirementVersion;

    @Column(name = "policy_set_version", nullable = false, length = 20)
    private String policySetVersion;

    @Column(length = 40)
    private String readiness;

    @Column(name = "terminal_outcome", length = 32)
    private String terminalOutcome;

    @Column(name = "terminal_reason", length = 1000)
    private String terminalReason;

    @Column(name = "manual_intervention_required", nullable = false)
    private boolean manualInterventionRequired;

    @Column(name = "clarification_rounds", nullable = false)
    private int clarificationRounds;

    @Column(name = "attempts_used", nullable = false)
    private int attemptsUsed;

    @Column(name = "processing_millis", nullable = false)
    private long processingMillis;

    @Column(name = "fault_plan", length = 10_000)
    private String faultPlan;

    @Column(name = "gate_deadline_seconds")
    private Long gateDeadlineSeconds;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Version
    private Long version;

    protected WorkflowRun() {
    }

    private WorkflowRun(UUID id) {
        super(id);
    }

    public static WorkflowRun create(UUID id, String requirementRef, String title, String requestedBy,
            Classification classification, String policySetVersion, Instant now) {
        WorkflowRun run = new WorkflowRun(id);
        run.requirementRef = requirementRef;
        run.title = title;
        run.requestedBy = requestedBy;
        run.classification = classification;
        run.status = RunStatus.CREATED;
        run.currentPlanVersion = 1;
        run.currentRequirementVersion = 1;
        run.policySetVersion = policySetVersion;
        run.createdAt = now;
        run.updatedAt = now;
        return run;
    }

    /** Applies a run transition allowed by the state model; a prohibited one throws. */
    public void transitionTo(RunStatus to, Instant now) {
        RunTransitions.check(status, to);
        if (to == RunStatus.RUNNING && startedAt == null) {
            startedAt = now;
        }
        status = to;
        updatedAt = now;
    }

    /** Ends the run with a terminal status and its reason. */
    public void terminate(RunStatus outcome, String reason, Instant now) {
        transitionTo(outcome, now);
        terminalOutcome = outcome.name();
        terminalReason = Texts.truncate(reason, 1000);
        completedAt = now;
    }

    public void simulation(String faultPlan, Long gateDeadlineSeconds) {
        this.faultPlan = faultPlan;
        this.gateDeadlineSeconds = gateDeadlineSeconds;
    }

    public void countAttempt() {
        attemptsUsed++;
    }

    public void addProcessingMillis(long millis) {
        processingMillis += Math.max(0, millis);
    }

    public void classify(Classification classification) {
        this.classification = classification;
    }

    public void readiness(String readiness) {
        this.readiness = readiness;
    }

    public void planVersion(int version) {
        this.currentPlanVersion = version;
    }

    public void requirementVersion(int version) {
        this.currentRequirementVersion = version;
    }

    public void countClarificationRound() {
        clarificationRounds++;
    }

    public void requireManualIntervention() {
        manualInterventionRequired = true;
    }

    public void touch(Instant now) {
        updatedAt = now;
    }

    public String getRequirementRef() {
        return requirementRef;
    }

    public String getTitle() {
        return title;
    }

    public String getRequestedBy() {
        return requestedBy;
    }

    public Classification getClassification() {
        return classification;
    }

    public RunStatus getStatus() {
        return status;
    }

    public int getCurrentPlanVersion() {
        return currentPlanVersion;
    }

    public int getCurrentRequirementVersion() {
        return currentRequirementVersion;
    }

    public String getPolicySetVersion() {
        return policySetVersion;
    }

    public String getReadiness() {
        return readiness;
    }

    public String getTerminalOutcome() {
        return terminalOutcome;
    }

    public String getTerminalReason() {
        return terminalReason;
    }

    public boolean isManualInterventionRequired() {
        return manualInterventionRequired;
    }

    public int getClarificationRounds() {
        return clarificationRounds;
    }

    public int getAttemptsUsed() {
        return attemptsUsed;
    }

    public long getProcessingMillis() {
        return processingMillis;
    }

    public String getFaultPlan() {
        return faultPlan;
    }

    public Long getGateDeadlineSeconds() {
        return gateDeadlineSeconds;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Long getVersion() {
        return version;
    }
}
