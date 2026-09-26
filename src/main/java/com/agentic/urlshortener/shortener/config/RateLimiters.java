package com.agentic.urlshortener.shortener.config;

import com.agentic.urlshortener.shortener.service.TokenBucketRateLimiter;

/** The two limiters of the application plane: creation per API consumer, not-found outcomes per client address. */
public record RateLimiters(TokenBucketRateLimiter creation, TokenBucketRateLimiter notFound) {
}
