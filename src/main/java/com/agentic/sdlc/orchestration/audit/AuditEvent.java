package com.agentic.sdlc.orchestration.audit;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.agentic.sdlc.orchestration.model.ActorType;

/** One insert-only, hash-chained audit event (data-model.md, AuditEvent). */
@Entity
@Table(name = "audit_event")
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chain_id", nullable = false, length = 64)
    private String chainId;

    @Column(nullable = false)
    private long seq;

    @Column(name = "run_id")
    private UUID runId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, length = 16)
    private ActorType actorType;

    @Column(name = "actor_id", nullable = false, length = 64)
    private String actorId;

    @Column(nullable = false, length = 60)
    private String action;

    @Column(length = 200)
    private String target;

    @Column(name = "from_state", length = 40)
    private String fromState;

    @Column(name = "to_state", length = 40)
    private String toState;

    @Column(nullable = false, length = 24)
    private String result;

    @Column(length = 2000)
    private String reason;

    @Column(length = 1_000_000)
    private String details;

    @Column(name = "prev_hash", nullable = false, length = 64)
    private String prevHash;

    @Column(nullable = false, length = 64)
    private String hash;

    protected AuditEvent() {
    }

    AuditEvent(String chainId, long seq, UUID runId, Instant occurredAt, AuditRecord record, String prevHash) {
        this.chainId = chainId;
        this.seq = seq;
        this.runId = runId;
        this.occurredAt = occurredAt;
        this.actorType = record.actorType();
        this.actorId = record.actorId();
        this.action = record.action();
        this.target = record.target();
        this.fromState = record.fromState();
        this.toState = record.toState();
        this.result = record.result();
        this.reason = record.reason();
        this.details = record.details();
        this.prevHash = prevHash;
    }

    void seal(String computedHash) {
        this.hash = computedHash;
    }

    public Long getId() {
        return id;
    }

    public String getChainId() {
        return chainId;
    }

    public long getSeq() {
        return seq;
    }

    public UUID getRunId() {
        return runId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public ActorType getActorType() {
        return actorType;
    }

    public String getActorId() {
        return actorId;
    }

    public String getAction() {
        return action;
    }

    public String getTarget() {
        return target;
    }

    public String getFromState() {
        return fromState;
    }

    public String getToState() {
        return toState;
    }

    public String getResult() {
        return result;
    }

    public String getReason() {
        return reason;
    }

    public String getDetails() {
        return details;
    }

    public String getPrevHash() {
        return prevHash;
    }

    public String getHash() {
        return hash;
    }
}
