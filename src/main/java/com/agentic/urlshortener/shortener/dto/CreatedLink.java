package com.agentic.urlshortener.shortener.dto;

/** Outcome of a creation request; {@code replayed} when an idempotent request returned the stored result. */
public record CreatedLink(LinkView view, boolean replayed) {
}
