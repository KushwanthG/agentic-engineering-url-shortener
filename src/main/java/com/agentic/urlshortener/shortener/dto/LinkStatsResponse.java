package com.agentic.urlshortener.shortener.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Click analytics of one link ({@code openapi.yaml#/components/schemas/LinkStatsResponse}). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LinkStatsResponse(
        String code,
        long totalClicks,
        Instant lastAccessedAt,
        int windowDays,
        List<Daily> daily) {

    /** Clicks of one UTC day; days without clicks are omitted. */
    public record Daily(LocalDate date, long clicks) {
    }
}
