package com.agentic.urlshortener.orchestration.port;

/**
 * Outcome of a probe call against the application plane, independent of HTTP: created (with the
 * code), rejected (with the contract error code), a redirect (with the target), not found, expired,
 * or unavailable.
 */
public record ProbeResponse(Outcome outcome, String code, String errorCode, String detail, String targetUrl) {

    public enum Outcome {
        CREATED,
        REJECTED,
        REDIRECT,
        NOT_FOUND,
        EXPIRED,
        UNAVAILABLE
    }

    public static ProbeResponse created(String code, String targetUrl) {
        return new ProbeResponse(Outcome.CREATED, code, null, null, targetUrl);
    }

    public static ProbeResponse rejected(String errorCode, String detail) {
        return new ProbeResponse(Outcome.REJECTED, null, errorCode, detail, null);
    }

    public static ProbeResponse redirect(String targetUrl) {
        return new ProbeResponse(Outcome.REDIRECT, null, null, null, targetUrl);
    }

    public static ProbeResponse notFound() {
        return new ProbeResponse(Outcome.NOT_FOUND, null, null, null, null);
    }

    public static ProbeResponse expired() {
        return new ProbeResponse(Outcome.EXPIRED, null, null, null, null);
    }

    public static ProbeResponse unavailable(String detail) {
        return new ProbeResponse(Outcome.UNAVAILABLE, null, null, detail, null);
    }
}
