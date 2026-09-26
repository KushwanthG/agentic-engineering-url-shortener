package com.agentic.urlshortener.common.exception;

import java.util.List;

/** An expected, client-visible failure carrying a contract error code. */
public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final List<FieldError> fieldErrors;
    private final Long retryAfterSeconds;

    public ApiException(ErrorCode code, String detail) {
        this(code, detail, List.of(), null);
    }

    public ApiException(ErrorCode code, String detail, List<FieldError> fieldErrors, Long retryAfterSeconds) {
        super(detail);
        this.code = code;
        this.fieldErrors = List.copyOf(fieldErrors);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public static ApiException rateLimited(String detail, long retryAfterSeconds) {
        return new ApiException(ErrorCode.RATE_LIMITED, detail, List.of(), retryAfterSeconds);
    }

    public ErrorCode code() {
        return code;
    }

    public List<FieldError> fieldErrors() {
        return fieldErrors;
    }

    public Long retryAfterSeconds() {
        return retryAfterSeconds;
    }

    /** A field-level validation message ({@code Problem.errors[]} in the contract). */
    public record FieldError(String field, String message) {
    }
}
