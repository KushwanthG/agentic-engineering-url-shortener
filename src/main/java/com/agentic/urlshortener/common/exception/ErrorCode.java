package com.agentic.urlshortener.common.exception;

import java.util.Locale;

import org.springframework.http.HttpStatus;

/**
 * Stable machine-readable error codes of the API contract ({@code openapi.yaml#/components/schemas/Problem}).
 * Each code has one HTTP status and one human-readable title.
 */
public enum ErrorCode {

    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Request validation failed"),
    URL_INVALID(HttpStatus.BAD_REQUEST, "URL is not a valid absolute URL"),
    URL_SCHEME_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "URL scheme not allowed"),
    URL_CREDENTIALS_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "URL must not contain credentials"),
    URL_HOST_MISSING(HttpStatus.BAD_REQUEST, "URL has no host"),
    URL_HOST_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "URL host not allowed"),
    URL_TOO_LONG(HttpStatus.BAD_REQUEST, "URL too long"),
    INVALID_EXPIRY(HttpStatus.BAD_REQUEST, "Invalid expiry time"),
    INVALID_ALIAS(HttpStatus.BAD_REQUEST, "Invalid alias"),
    RESERVED_ALIAS(HttpStatus.BAD_REQUEST, "Alias is reserved"),
    ALIAS_CONFLICT(HttpStatus.CONFLICT, "Alias already in use"),
    INVALID_CLICK_LIMIT(HttpStatus.BAD_REQUEST, "Invalid click limit"),
    CAPABILITY_NOT_AVAILABLE(HttpStatus.UNPROCESSABLE_CONTENT, "Capability not available"),
    INVALID_IDEMPOTENCY_KEY(HttpStatus.BAD_REQUEST, "Invalid idempotency key"),
    IDEMPOTENCY_KEY_REUSED(HttpStatus.CONFLICT, "Idempotency key reused with a different request"),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded"),
    CODE_GENERATION_EXHAUSTED(HttpStatus.SERVICE_UNAVAILABLE, "Could not generate a unique short code"),
    LINK_NOT_FOUND(HttpStatus.NOT_FOUND, "Short link not found"),
    LINK_EXPIRED(HttpStatus.GONE, "Short link expired"),
    STORE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Link store temporarily unavailable"),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Authentication required"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "Not permitted"),
    SEPARATION_OF_DUTIES(HttpStatus.FORBIDDEN, "Separation of duties violated"),
    RUN_NOT_FOUND(HttpStatus.NOT_FOUND, "Workflow run not found"),
    ARTIFACT_NOT_FOUND(HttpStatus.NOT_FOUND, "Artifact not found"),
    CHANGE_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "Change request not found"),
    EXCEPTION_NOT_FOUND(HttpStatus.NOT_FOUND, "Policy exception not found"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Resource not found"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed"),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported media type"),
    ILLEGAL_STATE(HttpStatus.CONFLICT, "Operation not allowed in the current state"),
    DEADLINE_PASSED(HttpStatus.CONFLICT, "Decision deadline has passed"),
    RELEASE_NOT_READY(HttpStatus.CONFLICT, "Release readiness is NOT_READY"),
    RUN_TERMINAL(HttpStatus.CONFLICT, "Run is in a terminal state"),
    CONCURRENT_DECISION(HttpStatus.CONFLICT, "Another decision was recorded first"),
    FAULT_INJECTION_DISABLED(HttpStatus.BAD_REQUEST, "Fault injection is disabled"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error");

    private final HttpStatus status;
    private final String title;

    ErrorCode(HttpStatus status, String title) {
        this.status = status;
        this.title = title;
    }

    public HttpStatus status() {
        return status;
    }

    public String title() {
        return title;
    }

    /** RFC 9457 {@code type} URN, e.g. {@code urn:problem-type:url-scheme-not-allowed}. */
    public String type() {
        return "urn:problem-type:" + name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
