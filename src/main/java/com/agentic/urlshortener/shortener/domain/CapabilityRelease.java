package com.agentic.urlshortener.shortener.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** Release flag of one capability, changed only through a governed orchestration run (ADR-018). */
@Entity
@Table(name = "capability_release")
public class CapabilityRelease {

    @Id
    @Column(name = "capability_id", length = 64)
    private String capabilityId;

    @Column(nullable = false)
    private boolean released;

    @Column(length = 10_000)
    private String parameters;

    @Column(name = "changed_by", length = 64)
    private String changedBy;

    @Column(name = "changed_by_run")
    private UUID changedByRun;

    @Column(name = "changed_at")
    private Instant changedAt;

    @Column(length = 500)
    private String reason;

    @Version
    private long version;

    protected CapabilityRelease() {
    }

    public static CapabilityRelease unreleased(String capabilityId) {
        CapabilityRelease release = new CapabilityRelease();
        release.capabilityId = capabilityId;
        return release;
    }

    public void change(boolean released, String parameters, String changedBy, UUID changedByRun, Instant changedAt, String reason) {
        this.released = released;
        this.parameters = parameters;
        this.changedBy = changedBy;
        this.changedByRun = changedByRun;
        this.changedAt = changedAt;
        this.reason = reason;
    }

    public String getCapabilityId() {
        return capabilityId;
    }

    public boolean isReleased() {
        return released;
    }

    public String getParameters() {
        return parameters;
    }

    public String getChangedBy() {
        return changedBy;
    }

    public UUID getChangedByRun() {
        return changedByRun;
    }

    public Instant getChangedAt() {
        return changedAt;
    }

    public String getReason() {
        return reason;
    }

    public long getVersion() {
        return version;
    }
}
