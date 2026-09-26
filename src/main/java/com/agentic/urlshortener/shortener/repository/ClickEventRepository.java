package com.agentic.urlshortener.shortener.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.agentic.urlshortener.shortener.domain.ClickEvent;

public interface ClickEventRepository extends JpaRepository<ClickEvent, Long> {

    /** Clicks of one UTC day (timestamps are written in UTC, see {@code hibernate.jdbc.time_zone}). */
    record DailyCount(LocalDate date, long clicks) {
    }

    long countByLinkId(Long linkId);

    @Query("select cast(e.occurredAt as LocalDate), count(e) from ClickEvent e"
            + " where e.linkId = :linkId and e.occurredAt >= :since"
            + " group by cast(e.occurredAt as LocalDate) order by cast(e.occurredAt as LocalDate)")
    List<Object[]> dailyCountRows(@Param("linkId") Long linkId, @Param("since") Instant since);

    /** Days with at least one click since {@code since}, oldest first (sparse, FR-ANL-03). */
    default List<DailyCount> dailyCounts(Long linkId, Instant since) {
        return dailyCountRows(linkId, since).stream()
                .map(row -> new DailyCount((LocalDate) row[0], ((Number) row[1]).longValue()))
                .toList();
    }
}
