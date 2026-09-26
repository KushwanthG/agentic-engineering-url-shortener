package com.agentic.urlshortener.shortener.dto;

/** Outcome of resolving a short code; {@code targetUrl} is set only for {@link Outcome#REDIRECT}. */
public record Resolution(Outcome outcome, String targetUrl) {

    public enum Outcome {
        REDIRECT,
        NOT_FOUND,
        EXPIRED
    }

    public static Resolution redirect(String targetUrl) {
        return new Resolution(Outcome.REDIRECT, targetUrl);
    }

    public static Resolution notFound() {
        return new Resolution(Outcome.NOT_FOUND, null);
    }

    public static Resolution expired() {
        return new Resolution(Outcome.EXPIRED, null);
    }
}
