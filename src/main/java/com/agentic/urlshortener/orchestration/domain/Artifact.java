package com.agentic.urlshortener.orchestration.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * A versioned stage output. It records the producing stage, generation, attempt and agent, the
 * SHA-256 fingerprint of its content, and references to the input artifacts it was derived from
 * (FR-ORC-10), so that every artifact can be traced to its inputs and decisions.
 */
@Entity
@Table(name = "artifact")
public class Artifact extends AssignedIdEntity {

    public static final String JSON = "application/json";
    public static final String MARKDOWN = "text/markdown";

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage_key", nullable = false, length = 40)
    private StageType stageKey;

    @Column(nullable = false)
    private int generation;

    @Column(name = "attempt_no", nullable = false)
    private int attemptNo;

    @Column(name = "artifact_type", nullable = false, length = 40)
    private String artifactType;

    @Column(nullable = false)
    private int version;

    @Column(name = "media_type", nullable = false, length = 40)
    private String mediaType;

    @Column(nullable = false, length = 1_000_000)
    private String content;

    @Column(nullable = false, length = 64)
    private String fingerprint;

    @Column(name = "produced_by", nullable = false, length = 80)
    private String producedBy;

    @Column(name = "input_refs", nullable = false, length = 100_000)
    private String inputRefs;

    @Column(nullable = false)
    private boolean superseded;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Artifact() {
    }

    private Artifact(UUID id) {
        super(id);
    }

    public static Artifact create(UUID runId, StageType stageKey, int generation, int attemptNo, String artifactType, int version,
            String mediaType, String content, String fingerprint, String producedBy, String inputRefs, Instant createdAt) {
        Artifact artifact = new Artifact(UUID.randomUUID());
        artifact.runId = runId;
        artifact.stageKey = stageKey;
        artifact.generation = generation;
        artifact.attemptNo = attemptNo;
        artifact.artifactType = artifactType;
        artifact.version = version;
        artifact.mediaType = mediaType;
        artifact.content = content;
        artifact.fingerprint = fingerprint;
        artifact.producedBy = producedBy;
        artifact.inputRefs = inputRefs;
        artifact.createdAt = createdAt;
        return artifact;
    }

    /** Marks this version as replaced by a newer one; history is kept. */
    public void supersede() {
        superseded = true;
    }

    public boolean isJson() {
        return JSON.equals(mediaType);
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

    public String getArtifactType() {
        return artifactType;
    }

    public int getVersion() {
        return version;
    }

    public String getMediaType() {
        return mediaType;
    }

    public String getContent() {
        return content;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public String getProducedBy() {
        return producedBy;
    }

    public String getInputRefs() {
        return inputRefs;
    }

    public boolean isSuperseded() {
        return superseded;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
