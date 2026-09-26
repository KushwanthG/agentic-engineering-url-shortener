package com.agentic.sdlc.orchestration.audit;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditChainHeadRepository extends JpaRepository<AuditChainHead, String> {

    /** {@code SELECT ... FOR UPDATE}: appends to the same chain serialize until the holder commits. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from AuditChainHead h where h.chainId = :chainId")
    Optional<AuditChainHead> lockByChainId(@Param("chainId") String chainId);
}
