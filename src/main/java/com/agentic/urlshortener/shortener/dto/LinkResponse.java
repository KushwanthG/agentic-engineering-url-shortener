package com.agentic.urlshortener.shortener.dto;

import java.time.Instant;

import com.agentic.urlshortener.shortener.domain.LinkStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

/** Link metadata ({@code openapi.yaml#/components/schemas/LinkResponse}); absent optional fields are omitted. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LinkResponse(
        String code,
        String shortUrl,
        String targetUrl,
        Instant createdAt,
        Instant expiresAt,
        LinkStatus status,
        boolean customAlias,
        Long maxClicks) {

    public static LinkResponse from(LinkView view) {
        return new LinkResponse(view.code(), view.shortUrl(), view.targetUrl(), view.createdAt(), view.expiresAt(),
                view.status(), view.customAlias(), view.maxClicks());
    }
}
