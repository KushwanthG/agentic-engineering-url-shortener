package com.agentic.urlshortener.orchestration.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * A time-bound exception to a failed policy (FR-POL-04). An approved exception whose expiry has
 * passed counts as unapproved.
 */
@Entity
@Table(name = "policy_exception")
public class PolicyExceptionRecord extends AssignedIdEntity {

    public static final String PENDING = "PENDING";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Column(name = "policy_id", nullable = false, length = 20)
    private String policyId;

    @Column(nullable = false, length = 1000)
    private String reason;

    @Column(nullable = false, length = 500)
    private String scope;

    @Column(name = "compensating_control", nullable = false, length = 1000)
    private String compensatingControl;

    @Column(name = "requested_by", nullable = false, length = 64)
    private String requestedBy;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "decided_by", length = 64)
    private String decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_rationale", length = 2000)
    private String decisionRationale;

    protected PolicyExceptionRecord() {
    }

    private PolicyExceptionRecord(UUID id) {
        super(id);
    }

    public static PolicyExceptionRecord create(UUID runId, String policyId, String reason, String scope, String compensatingControl,
            String requestedBy, Instant requestedAt, Instant expiresAt) {
        PolicyExceptionRecord record = new PolicyExceptionRecord(UUID.randomUUID());
        record.runId = runId;
        record.policyId = policyId;
        record.reason = Texts.truncate(reason, 1000);
        record.scope = Texts.truncate(scope, 500);
        record.compensatingControl = Texts.truncate(compensatingControl, 1000);
        record.requestedBy = requestedBy;
        record.requestedAt = requestedAt;
        record.expiresAt = expiresAt;
        record.status = PENDING;
        return record;
    }

    public void decide(String status, String decidedBy, Instant decidedAt, String rationale) {
        this.status = status;
        this.decidedBy = decidedBy;
        this.decidedAt = decidedAt;
        this.decisionRationale = Texts.truncate(rationale, 2000);
    }

    /** Approved and not yet expired at {@code now}. */
    public boolean isEffectiveAt(Instant now) {
        return APPROVED.equals(status) && now.isBefore(expiresAt);
    }

    public UUID getRunId() {
        return runId;
    }

    public String getPolicyId() {
        return policyId;
    }

    public String getReason() {
        return reason;
    }

    public String getScope() {
        return scope;
    }

    public String getCompensatingControl() {
        return compensatingControl;
    }

    public String getRequestedBy() {
        return requestedBy;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
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
