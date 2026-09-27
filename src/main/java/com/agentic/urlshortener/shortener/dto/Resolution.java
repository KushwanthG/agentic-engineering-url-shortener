package com.agentic.urlshortener.shortener.dto;

/** Outcome of resolving a short code; {@code targetUrl} is set only for {@link Outcome#REDIRECT}. */
public record Resolution(Outcome outcome, String targetUrl) {

    public enum Outcome {
        REDIRECT,
        NOT_FOUND,
        EXPIRED,
        /** A click-limited link whose click could not be recorded: the redirect is refused (fail closed, AC-6). */
        UNAVAILABLE
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

    public static Resolution unavailable() {
        return new Resolution(Outcome.UNAVAILABLE, null);
    }
}
