package com.agentic.urlshortener.orchestration.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** An immutable snapshot of a run's plan graph, with the diff to the previous version and its trigger. */
@Entity
@Table(name = "plan_version")
public class PlanVersion extends AssignedIdEntity {

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Column(nullable = false)
    private int version;

    @Column(nullable = false, length = 1_000_000)
    private String graph;

    @Column(length = 1_000_000)
    private String diff;

    @Column(name = "trigger_type", nullable = false, length = 40)
    private String triggerType;

    @Column(nullable = false, length = 1000)
    private String reason;

    @Column(name = "created_by", nullable = false, length = 64)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PlanVersion() {
    }

    private PlanVersion(UUID id) {
        super(id);
    }

    public static PlanVersion create(UUID runId, int version, String graph, String diff, String triggerType, String reason,
            String createdBy, Instant createdAt) {
        PlanVersion plan = new PlanVersion(UUID.randomUUID());
        plan.runId = runId;
        plan.version = version;
        plan.graph = graph;
        plan.diff = diff;
        plan.triggerType = triggerType;
        plan.reason = Texts.truncate(reason, 1000);
        plan.createdBy = createdBy;
        plan.createdAt = createdAt;
        return plan;
    }

    public UUID getRunId() {
        return runId;
    }

    public int getVersion() {
        return version;
    }

    public String getGraph() {
        return graph;
    }

    public String getDiff() {
        return diff;
    }

    public String getTriggerType() {
        return triggerType;
    }

    public String getReason() {
        return reason;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
