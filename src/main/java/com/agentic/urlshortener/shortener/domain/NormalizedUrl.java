package com.agentic.urlshortener.shortener.domain;

/** A target URL that passed {@link UrlPolicy}: lower-case scheme and host, ASCII host, no default port. */
public record NormalizedUrl(String value, String host) {
}
