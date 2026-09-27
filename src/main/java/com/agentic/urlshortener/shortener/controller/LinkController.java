package com.agentic.urlshortener.shortener.controller;

import java.net.URI;

import jakarta.validation.Valid;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.agentic.urlshortener.common.exception.ApiException;
import com.agentic.urlshortener.common.security.ApiPrincipal;
import com.agentic.urlshortener.shortener.ShortenerMeters;
import com.agentic.urlshortener.shortener.config.RateLimiters;
import com.agentic.urlshortener.shortener.dto.CreateLinkCommand;
import com.agentic.urlshortener.shortener.dto.CreateLinkRequest;
import com.agentic.urlshortener.shortener.dto.CreatedLink;
import com.agentic.urlshortener.shortener.dto.LinkResponse;
import com.agentic.urlshortener.shortener.dto.LinkStatsResponse;
import com.agentic.urlshortener.shortener.service.LinkCreationService;
import com.agentic.urlshortener.shortener.service.LinkQueryService;
import com.agentic.urlshortener.shortener.service.TokenBucketRateLimiter;

/** Links API ({@code openapi.yaml} tag {@code links}); role API_CONSUMER is enforced by SecurityConfig. */
@RestController
@RequestMapping(path = "/api/v1/links", produces = MediaType.APPLICATION_JSON_VALUE)
public class LinkController {

    static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    static final String IDEMPOTENCY_REPLAYED = "Idempotency-Replayed";

    private final LinkCreationService creation;
    private final LinkQueryService queries;
    private final RateLimiters rateLimiters;
    private final ShortenerMeters meters;

    public LinkController(LinkCreationService creation, LinkQueryService queries, RateLimiters rateLimiters, ShortenerMeters meters) {
        this.meters = meters;
        this.creation = creation;
        this.queries = queries;
        this.rateLimiters = rateLimiters;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<LinkResponse> create(@Valid @RequestBody CreateLinkRequest request,
            @RequestHeader(name = IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @AuthenticationPrincipal ApiPrincipal principal) {
        TokenBucketRateLimiter.Decision decision = rateLimiters.creation().tryAcquire(principal.id());
        if (!decision.allowed()) {
            meters.rateLimited(ShortenerMeters.CREATION_LIMITER);
            throw ApiException.rateLimited("Link creation limit reached for this consumer; retry later.",
                    decision.retryAfterSeconds());
        }
        CreatedLink created = creation.create(new CreateLinkCommand(request.url(), request.expiresAt(), request.alias(),
                request.maxClicks(), principal.id(), idempotencyKey));
        ResponseEntity.BodyBuilder response = ResponseEntity.created(URI.create("/api/v1/links/" + created.view().code()));
        if (created.replayed()) {
            response.header(IDEMPOTENCY_REPLAYED, "true");
        } else {
            meters.linkCreated();
        }
        return response.body(LinkResponse.from(created.view()));
    }

    @GetMapping("/{code}")
    public LinkResponse get(@PathVariable String code) {
        return LinkResponse.from(queries.get(code));
    }

    @GetMapping("/{code}/stats")
    public LinkStatsResponse stats(@PathVariable String code) {
        return queries.stats(code);
    }
}
