package com.agentic.urlshortener.shortener.config;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import com.agentic.urlshortener.shortener.repository.ShortLinkRepository;

/**
 * Readiness contributor {@code linkStore}: DOWN while the link store cannot answer a trivial query,
 * so traffic is withheld during an outage (FR-OPS-01). Details are never exposed.
 */
@Component("linkStoreHealthIndicator")
public class LinkStoreHealthIndicator implements HealthIndicator {

    private final ShortLinkRepository links;

    public LinkStoreHealthIndicator(ShortLinkRepository links) {
        this.links = links;
    }

    @Override
    public Health health() {
        try {
            links.probe();
            return Health.up().build();
        } catch (RuntimeException e) {
            return Health.down().withDetail("reason", "link store unavailable").build();
        }
    }
}
