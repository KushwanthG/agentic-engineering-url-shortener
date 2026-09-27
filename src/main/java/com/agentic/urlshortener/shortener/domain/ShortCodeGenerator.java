package com.agentic.urlshortener.shortener.domain;

/** Source of candidate short codes; uniqueness is enforced by the database, not by the generator (ADR-004). */
public interface ShortCodeGenerator {

    String next();
}
