package com.agentic.urlshortener.shortener.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.agentic.urlshortener.shortener.domain.ShortLink;

public interface ShortLinkRepository extends JpaRepository<ShortLink, Long> {

    Optional<ShortLink> findByCode(String code);

    /**
     * Counts one click atomically in SQL, so concurrent redirects never lose updates (FR-ANL-05).
     * Returns the number of updated rows (1, or 0 when the link no longer exists).
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query("update ShortLink l set l.clickCount = l.clickCount + 1, l.lastAccessedAt = :now where l.id = :id")
    int incrementClicks(@Param("id") Long id, @Param("now") Instant now);

    /** Removes the verification links of one orchestration run; click events cascade in the database. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query("delete from ShortLink l where l.synthetic = true and l.syntheticRunId = :runId")
    int deleteBySyntheticRunId(@Param("runId") UUID runId);

    /** Cheap round trip used by the readiness indicator. */
    @Query(value = "SELECT 1", nativeQuery = true)
    Integer probe();
}
