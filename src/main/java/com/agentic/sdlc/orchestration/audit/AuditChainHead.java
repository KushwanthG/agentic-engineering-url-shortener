package com.agentic.sdlc.orchestration.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Last sequence number and hash per audit chain. Locked pessimistically while appending so that
 * concurrent appends serialize per chain, and compared during verification so that truncating the
 * tail of a chain is detected.
 */
@Entity
@Table(name = "audit_chain_head")
public class AuditChainHead {

    @Id
    @Column(name = "chain_id", length = 64)
    private String chainId;

    @Column(name = "last_seq", nullable = false)
    private long lastSeq;

    @Column(name = "last_hash", nullable = false, length = 64)
    private String lastHash;

    protected AuditChainHead() {
    }

    AuditChainHead(String chainId, long lastSeq, String lastHash) {
        this.chainId = chainId;
        this.lastSeq = lastSeq;
        this.lastHash = lastHash;
    }

    public String getChainId() {
        return chainId;
    }

    public long getLastSeq() {
        return lastSeq;
    }

    public String getLastHash() {
        return lastHash;
    }

    void advance(long seq, String hash) {
        this.lastSeq = seq;
        this.lastHash = hash;
    }
}
