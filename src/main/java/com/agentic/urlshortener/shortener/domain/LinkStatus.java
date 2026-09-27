package com.agentic.urlshortener.shortener.domain;

/** Lifecycle status of a short link as reported by the API ({@code LinkResponse.status}). */
public enum LinkStatus {
    ACTIVE,
    EXPIRED
}
