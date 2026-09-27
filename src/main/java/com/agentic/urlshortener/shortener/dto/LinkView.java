package com.agentic.urlshortener.shortener.dto;

import java.time.Instant;

import com.agentic.urlshortener.shortener.domain.LinkStatus;
import com.agentic.urlshortener.shortener.domain.ShortLink;

/** Read model of a link; also the snapshot replayed for idempotent requests. */
public record LinkView(
        String code,
        String shortUrl,
        String targetUrl,
        Instant createdAt,
        Instant expiresAt,
        LinkStatus status,
        boolean customAlias,
        Long maxClicks) {

    public static LinkView of(ShortLink link, String baseUrl, Instant now) {
        return new LinkView(link.getCode(), shortUrl(baseUrl, link.getCode()), link.getTargetUrl(), link.getCreatedAt(),
                link.getExpiresAt(), link.statusAt(now), link.isCustomAlias(), link.getMaxClicks());
    }

    public static String shortUrl(String baseUrl, String code) {
        return (baseUrl.endsWith("/") ? baseUrl : baseUrl + "/") + code;
    }
}
