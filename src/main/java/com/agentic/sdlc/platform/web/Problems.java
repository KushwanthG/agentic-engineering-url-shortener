package com.agentic.sdlc.platform.web;

import java.net.URI;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

/** Builds RFC 9457 problem details in the shape of the contract's {@code Problem} schema. */
public final class Problems {

    private Problems() {
    }

    public static ProblemDetail detail(ErrorCode code, String detail, String instance,
                                       List<ApiException.FieldError> errors, Long retryAfterSeconds) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(code.status(), detail);
        problem.setType(URI.create(code.type()));
        problem.setTitle(code.title());
        if (instance != null && isSafePath(instance)) {
            problem.setInstance(URI.create(instance));
        }
        problem.setProperty("code", code.name());
        problem.setProperty("correlationId", CorrelationIdFilter.currentId());
        if (errors != null && !errors.isEmpty()) {
            problem.setProperty("errors", errors);
        }
        if (retryAfterSeconds != null) {
            problem.setProperty("retryAfterSeconds", retryAfterSeconds);
        }
        return problem;
    }

    public static ResponseEntity<ProblemDetail> response(ErrorCode code, String detail, String instance,
                                                         List<ApiException.FieldError> errors, Long retryAfterSeconds) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(code.status()).contentType(MediaType.APPLICATION_PROBLEM_JSON);
        if (retryAfterSeconds != null) {
            builder.header(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds));
        }
        return builder.body(detail(code, detail, instance, errors, retryAfterSeconds));
    }

    private static boolean isSafePath(String path) {
        return path.length() <= 512 && path.chars().allMatch(c -> c > 0x20 && c < 0x7f && c != '"' && c != '<' && c != '>'
                && c != '\\' && c != '^' && c != '`' && c != '{' && c != '|' && c != '}');
    }
}
