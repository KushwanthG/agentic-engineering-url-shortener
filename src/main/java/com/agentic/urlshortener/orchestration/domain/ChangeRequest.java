package com.agentic.urlshortener.orchestration.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** An amendment to an active run's requirement, with its impact and change-control decision. */
@Entity
@Table(name = "change_request")
public class ChangeRequest extends AssignedIdEntity {

    public static final String PENDING_APPROVAL = "PENDING_APPROVAL";
    public static final String APPLIED = "APPLIED";
    public static final String REJECTED = "REJECTED";

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Column(name = "requested_by", nullable = false, length = 64)
    private String requestedBy;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(nullable = false, length = 1000)
    private String reason;

    @Column(name = "amended_requirement", nullable = false, length = 1_000_000)
    private String amendedRequirement;

    @Column(nullable = false)
    private boolean material;

    @Column(nullable = false, length = 100_000)
    private String impact;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "decided_by", length = 64)
    private String decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_rationale", length = 2000)
    private String decisionRationale;

    protected ChangeRequest() {
    }

    private ChangeRequest(UUID id) {
        super(id);
    }

    public static ChangeRequest create(UUID runId, String requestedBy, Instant requestedAt, String reason,
            String amendedRequirement, boolean material, String impact) {
        ChangeRequest request = new ChangeRequest(UUID.randomUUID());
        request.runId = runId;
        request.requestedBy = requestedBy;
        request.requestedAt = requestedAt;
        request.reason = Texts.truncate(reason, 1000);
        request.amendedRequirement = amendedRequirement;
        request.material = material;
        request.impact = impact;
        request.status = PENDING_APPROVAL;
        return request;
    }

    public void decide(String status, String decidedBy, Instant decidedAt, String rationale) {
        this.status = status;
        this.decidedBy = decidedBy;
        this.decidedAt = decidedAt;
        this.decisionRationale = Texts.truncate(rationale, 2000);
    }

    public UUID getRunId() {
        return runId;
    }

    public String getRequestedBy() {
        return requestedBy;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public String getReason() {
        return reason;
    }

    public String getAmendedRequirement() {
        return amendedRequirement;
    }

    public boolean isMaterial() {
        return material;
    }

    public String getImpact() {
        return impact;
    }

    public String getStatus() {
        return status;
    }

    public String getDecidedBy() {
        return decidedBy;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public String getDecisionRationale() {
        return decisionRationale;
    }
}
