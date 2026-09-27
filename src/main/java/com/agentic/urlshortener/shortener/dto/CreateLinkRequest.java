package com.agentic.urlshortener.shortener.dto;

import java.time.Instant;

import jakarta.validation.constraints.NotNull;

/** Body of {@code POST /api/v1/links} ({@code openapi.yaml#/components/schemas/CreateLinkRequest}). */
public record CreateLinkRequest(
        @NotNull(message = "url is required") String url,
        Instant expiresAt,
        String alias,
        Long maxClicks) {
}
