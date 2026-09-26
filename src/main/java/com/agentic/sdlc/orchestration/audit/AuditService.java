package com.agentic.sdlc.orchestration.audit;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Append-only, tamper-evident audit trail (ADR-012, FR-AUD-01/02). Each chain (a run id, or
 * {@link #GLOBAL_CHAIN}) has contiguous sequence numbers and SHA-256 links. Appends join the
 * caller's transaction, so a state change and its audit event commit or roll back together.
 */
@Service
public class AuditService {

    public static final String GLOBAL_CHAIN = "GLOBAL";

    private final AuditEventRepository events;
    private final AuditChainHeadRepository heads;
    private final Clock clock;
    private final TransactionTemplate requiresNew;

    public AuditService(AuditEventRepository events, AuditChainHeadRepository heads, Clock clock,
                        PlatformTransactionManager transactionManager) {
        this.events = events;
        this.heads = heads;
        this.clock = clock;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Transactional
    public AuditEvent append(AuditRecord record) {
        String chainId = record.chainId();
        ensureHead(chainId);
        AuditChainHead head = heads.lockByChainId(chainId)
                .orElseThrow(() -> new IllegalStateException("audit chain head missing for " + chainId));
        long seq = head.getLastSeq() + 1;
        // Stored with microsecond precision; truncate before hashing so verification reproduces the hash.
        Instant occurredAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
        AuditEvent event = new AuditEvent(chainId, seq, record.runId(), occurredAt, record, head.getLastHash());
        event.seal(AuditHashing.hash(event));
        events.save(event);
        head.advance(seq, event.getHash());
        return event;
    }

    @Transactional(readOnly = true)
    public List<AuditEvent> chain(String chainId) {
        return events.findByChainIdOrderBySeqAsc(chainId);
    }

    /** Recomputes sequence, links, and hashes, and compares the chain head (detects tail truncation). */
    @Transactional(readOnly = true)
    public AuditVerification verify(String chainId) {
        List<AuditEvent> chain = events.findByChainIdOrderBySeqAsc(chainId);
        String previousHash = AuditHashing.GENESIS;
        long expectedSeq = 1;
        for (AuditEvent event : chain) {
            if (event.getSeq() != expectedSeq) {
                return broken(chainId, expectedSeq, chain.size(), "sequence gap: expected " + expectedSeq + " but found " + event.getSeq());
            }
            if (!event.getPrevHash().equals(previousHash)) {
                return broken(chainId, event.getSeq(), chain.size(), "link to the previous event is broken at " + event.getSeq());
            }
            if (!AuditHashing.hash(event).equals(event.getHash())) {
                return broken(chainId, event.getSeq(), chain.size(), "event content does not match its hash at " + event.getSeq());
            }
            previousHash = event.getHash();
            expectedSeq++;
        }
        long lastSeq = expectedSeq - 1;
        Optional<AuditChainHead> head = heads.findById(chainId);
        if (head.isPresent() && (head.get().getLastSeq() != lastSeq || !head.get().getLastHash().equals(previousHash))) {
            return broken(chainId, lastSeq + 1, chain.size(),
                    "chain head records sequence " + head.get().getLastSeq() + " but the trail ends at " + lastSeq);
        }
        return new AuditVerification(chainId, true, chain.size(), null, clock.instant(),
                "Chain intact: " + chain.size() + " events verified.");
    }

    private AuditVerification broken(String chainId, long seq, int checked, String message) {
        return new AuditVerification(chainId, false, checked, seq, clock.instant(), message);
    }

    private void ensureHead(String chainId) {
        if (heads.existsById(chainId)) {
            return;
        }
        try {
            requiresNew.executeWithoutResult(status -> heads.saveAndFlush(new AuditChainHead(chainId, 0, AuditHashing.GENESIS)));
        } catch (DataIntegrityViolationException concurrentlyCreated) {
            // another transaction created the head first; the pessimistic lock below serializes the appends
        }
    }
}
