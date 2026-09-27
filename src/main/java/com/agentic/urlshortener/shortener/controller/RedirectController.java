package com.agentic.urlshortener.shortener.controller;

import java.net.URI;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.agentic.urlshortener.common.exception.ApiException;
import com.agentic.urlshortener.common.exception.ErrorCode;
import com.agentic.urlshortener.shortener.config.RateLimiters;
import com.agentic.urlshortener.shortener.dto.Resolution;
import com.agentic.urlshortener.shortener.service.RedirectService;
import com.agentic.urlshortener.shortener.service.TokenBucketRateLimiter;

/**
 * Public redirect resolution (FR-RED-01..05). Every response is {@code no-store} so each resolution
 * reaches the service and is counted. A client address that exhausts its not-found budget is
 * throttled before lookup, which slows code enumeration.
 */
@RestController
public class RedirectController {

    private final RedirectService redirects;
    private final RateLimiters rateLimiters;

    public RedirectController(RedirectService redirects, RateLimiters rateLimiters) {
        this.redirects = redirects;
        this.rateLimiters = rateLimiters;
    }

    @GetMapping("/{code:[A-Za-z0-9_-]{3,32}}")
    public ResponseEntity<Void> resolve(@PathVariable String code,
            @RequestHeader(name = HttpHeaders.REFERER, required = false) String referer, HttpServletRequest request) {
        String client = request.getRemoteAddr();
        TokenBucketRateLimiter notFoundLimiter = rateLimiters.notFound();
        if (!notFoundLimiter.hasCapacity(client)) {
            throw ApiException.rateLimited("Too many unknown short codes from this address; retry later.",
                    notFoundLimiter.retryAfterSeconds(client));
        }
        Resolution resolution = redirects.resolve(code, referer);
        return switch (resolution.outcome()) {
            case REDIRECT -> ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(resolution.targetUrl()))
                    .cacheControl(CacheControl.noStore())
                    .build();
            case NOT_FOUND -> {
                notFoundLimiter.tryAcquire(client);
                throw new ApiException(ErrorCode.LINK_NOT_FOUND, "No short link exists for this code.");
            }
            case EXPIRED -> throw new ApiException(ErrorCode.LINK_EXPIRED, "This short link has expired.");
            case UNAVAILABLE -> throw new ApiException(ErrorCode.STORE_UNAVAILABLE,
                    "The click of this click-limited link cannot be recorded right now; retry later.");
        };
    }
}
