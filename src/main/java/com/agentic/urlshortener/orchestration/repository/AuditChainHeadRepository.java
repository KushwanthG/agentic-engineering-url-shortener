package com.agentic.urlshortener.orchestration.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.agentic.urlshortener.orchestration.domain.AuditChainHead;

public interface AuditChainHeadRepository extends JpaRepository<AuditChainHead, String> {

    /**
     * Allocates the next sequence number with one atomic {@code UPDATE}. The statement takes the row
     * lock, so appends to the same chain serialize until the holder commits, and the increment always
     * applies to the latest committed value.
     */
    @Modifying(flushAutomatically = true)
    @Query("update AuditChainHead h set h.lastSeq = h.lastSeq + 1 where h.chainId = :chainId")
    int allocateNextSeq(@Param("chainId") String chainId);

    /** The head as seen by the current transaction: {@code [lastSeq, lastHash]} (scalar, never a cached entity). */
    @Query("select h.lastSeq, h.lastHash from AuditChainHead h where h.chainId = :chainId")
    List<Object[]> position(@Param("chainId") String chainId);

    @Modifying(flushAutomatically = true)
    @Query("update AuditChainHead h set h.lastHash = :hash where h.chainId = :chainId")
    int recordHash(@Param("chainId") String chainId, @Param("hash") String hash);
}
