package com.agentic.urlshortener.common.exception;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.agentic.urlshortener.common.web.CorrelationIdFilter;

/**
 * Maps every failure to an RFC 9457 problem with a stable {@code code} and the correlation id
 * (FR-OPS-03). Unexpected failures are logged with their stack trace but answered generically, so no
 * internal detail (SQL, class names, stack frames) reaches the client.
 */
@RestControllerAdvice
public class ProblemDetailsHandler {

    private static final Logger log = LoggerFactory.getLogger(ProblemDetailsHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ProblemDetail> api(ApiException ex, HttpServletRequest request) {
        return Problems.response(ex.code(), ex.getMessage(), request.getRequestURI(), ex.fieldErrors(), ex.retryAfterSeconds());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> invalidBody(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ApiException.FieldError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiException.FieldError(error.getField(), String.valueOf(error.getDefaultMessage())))
                .toList();
        return Problems.response(ErrorCode.VALIDATION_FAILED, "The request body failed validation.", request.getRequestURI(), errors, null);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ProblemDetail> invalidParameters(HandlerMethodValidationException ex, HttpServletRequest request) {
        return Problems.response(ErrorCode.VALIDATION_FAILED, "The request parameters failed validation.", request.getRequestURI(), List.of(), null);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingRequestHeaderException.class,
            MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ProblemDetail> unreadable(Exception ex, HttpServletRequest request) {
        return Problems.response(ErrorCode.VALIDATION_FAILED, "The request could not be read.", request.getRequestURI(), List.of(), null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ProblemDetail> notFound(NoResourceFoundException ex, HttpServletRequest request) {
        return Problems.response(ErrorCode.RESOURCE_NOT_FOUND, "No resource exists at this path.", request.getRequestURI(), List.of(), null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ProblemDetail> methodNotAllowed(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return Problems.response(ErrorCode.METHOD_NOT_ALLOWED, "This method is not supported for the path.", request.getRequestURI(), List.of(), null);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ProblemDetail> unsupportedMediaType(HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        return Problems.response(ErrorCode.UNSUPPORTED_MEDIA_TYPE, "Use Content-Type application/json.", request.getRequestURI(), List.of(), null);
    }

    @ExceptionHandler({DataAccessResourceFailureException.class, TransientDataAccessException.class,
            QueryTimeoutException.class, CannotCreateTransactionException.class})
    ResponseEntity<ProblemDetail> storeUnavailable(Exception ex, HttpServletRequest request) {
        log.warn("Store unavailable while handling {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getClass().getSimpleName());
        return Problems.response(ErrorCode.STORE_UNAVAILABLE, "The link store is temporarily unavailable; retry later.",
                request.getRequestURI(), List.of(), 1L);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> forbidden(AccessDeniedException ex, HttpServletRequest request) {
        return Problems.response(ErrorCode.FORBIDDEN, "You are not permitted to perform this operation.", request.getRequestURI(), List.of(), null);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unexpected(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error handling {} {} (correlationId={})", request.getMethod(), request.getRequestURI(),
                CorrelationIdFilter.currentId(), ex);
        return Problems.response(ErrorCode.INTERNAL_ERROR, "An unexpected error occurred. Quote the correlation id when reporting it.",
                request.getRequestURI(), List.of(), null);
    }
}
