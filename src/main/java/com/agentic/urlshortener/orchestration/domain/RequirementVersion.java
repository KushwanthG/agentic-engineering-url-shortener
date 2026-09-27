package com.agentic.urlshortener.orchestration.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** An immutable requirement version: as submitted, clarified, or amended by a change request. */
@Entity
@Table(name = "requirement_version")
public class RequirementVersion extends AssignedIdEntity {

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Column(nullable = false)
    private int version;

    @Column(nullable = false, length = 32)
    private String source;

    @Column(nullable = false, length = 1_000_000)
    private String content;

    @Column(nullable = false, length = 64)
    private String fingerprint;

    @Column(name = "created_by", nullable = false, length = 64)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "decision_id")
    private UUID decisionId;

    protected RequirementVersion() {
    }

    private RequirementVersion(UUID id) {
        super(id);
    }

    public static RequirementVersion create(UUID runId, int version, String source, String content, String fingerprint,
            String createdBy, Instant createdAt, UUID decisionId) {
        RequirementVersion requirement = new RequirementVersion(UUID.randomUUID());
        requirement.runId = runId;
        requirement.version = version;
        requirement.source = source;
        requirement.content = content;
        requirement.fingerprint = fingerprint;
        requirement.createdBy = createdBy;
        requirement.createdAt = createdAt;
        requirement.decisionId = decisionId;
        return requirement;
    }

    public UUID getRunId() {
        return runId;
    }

    public int getVersion() {
        return version;
    }

    public String getSource() {
        return source;
    }

    public String getContent() {
        return content;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public UUID getDecisionId() {
        return decisionId;
    }
}
