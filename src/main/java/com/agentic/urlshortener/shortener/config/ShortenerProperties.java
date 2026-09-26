package com.agentic.urlshortener.shortener.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Application-plane settings ({@code app.shortener.*}); the defaults are the PVT values of the spec. */
@ConfigurationProperties("app.shortener")
public record ShortenerProperties(
        String baseUrl,
        int codeLength,
        int codeGenerationAttempts,
        int maxUrlLength,
        Duration maxExpiry,
        Duration idempotencyWindow,
        int analyticsWindowDays,
        List<String> reservedAliases,
        List<String> selfHosts,
        RateLimit rateLimit) {

    public ShortenerProperties {
        reservedAliases = reservedAliases == null ? List.of() : List.copyOf(reservedAliases);
        selfHosts = selfHosts == null ? List.of() : List.copyOf(selfHosts);
        if (rateLimit == null) {
            rateLimit = new RateLimit(30, 60, 10_000);
        }
    }

    /** PVT-05 (creation per API consumer) and PVT-06 (not-found outcomes per client address). */
    public record RateLimit(int creationPerMinute, int notFoundPerMinute, int maxTrackedClients) {
    }
}
