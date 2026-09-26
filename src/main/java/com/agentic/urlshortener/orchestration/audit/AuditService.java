package com.agentic.urlshortener.orchestration.audit;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.agentic.urlshortener.orchestration.domain.AuditChainHead;
import com.agentic.urlshortener.orchestration.domain.AuditEvent;
import com.agentic.urlshortener.orchestration.domain.AuditRecord;
import com.agentic.urlshortener.orchestration.repository.AuditChainHeadRepository;
import com.agentic.urlshortener.orchestration.repository.AuditEventRepository;

/**
 * Append-only, tamper-evident audit trail (ADR-012, FR-AUD-01/02). Each chain (a run id, or
 * {@link #GLOBAL_CHAIN}) has contiguous sequence numbers and SHA-256 links. Appends join the
 * caller's transaction, so a state change and its audit event commit or roll back together.
 */
@Service
public class AuditService {

    public static final String GLOBAL_CHAIN = AuditRecord.GLOBAL_CHAIN;

    private final AuditEventRepository events;
    private final AuditChainHeadRepository heads;
    private final Clock clock;
    private final TransactionTemplate requiresNew;
    private final Set<String> knownChains = ConcurrentHashMap.newKeySet();
    private final Object headCreation = new Object();

    public AuditService(AuditEventRepository events, AuditChainHeadRepository heads, Clock clock,
                        PlatformTransactionManager transactionManager) {
        this.events = events;
        this.heads = heads;
        this.clock = clock;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Appends one event. The atomic sequence increment takes the head's row lock, which is held until
     * the caller's transaction ends, so concurrent appends to one chain serialize and never reuse a
     * sequence number (the unique index on chain and sequence is the backstop).
     */
    @Transactional
    public AuditEvent append(AuditRecord record) {
        String chainId = record.chainId();
        ensureHead(chainId);
        if (heads.allocateNextSeq(chainId) != 1) {
            throw new IllegalStateException("audit chain head missing for " + chainId);
        }
        Object[] position = heads.position(chainId).getFirst();
        long seq = ((Number) position[0]).longValue();
        String previousHash = (String) position[1];
        // Stored with microsecond precision; truncate before hashing so verification reproduces the hash.
        Instant occurredAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
        AuditEvent event = new AuditEvent(chainId, seq, record.runId(), occurredAt, record, previousHash);
        event.seal(AuditHashing.hash(event));
        events.save(event);
        heads.recordHash(chainId, event.getHash());
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

    /**
     * Creates the chain head once, in its own committed transaction, with creation serialized in this
     * process. Racing inserts of the same head must be avoided: with H2, rolling back the losing
     * duplicate insert could reset the winner's head and hand out sequence 1 twice (stress run: 7–8
     * failed appenders in 40 rounds of 8 × 10 concurrent appends on new chains; 0 in 80 rounds once
     * creation was serialized). The H2 file database admits a single process, so an in-process guard is
     * sufficient; the unique index on (chain, sequence) remains the backstop. Heads are never deleted,
     * so known chains are remembered.
     */
    private void ensureHead(String chainId) {
        if (knownChains.contains(chainId)) {
            return;
        }
        synchronized (headCreation) {
            if (knownChains.contains(chainId)) {
                return;
            }
            try {
                requiresNew.executeWithoutResult(status -> {
                    if (!heads.existsById(chainId)) {
                        heads.saveAndFlush(new AuditChainHead(chainId, 0, AuditHashing.GENESIS));
                    }
                });
            } catch (DataIntegrityViolationException concurrentlyCreated) {
                // created by another instance; the unique index kept a single head
            }
            knownChains.add(chainId);
        }
    }
}
