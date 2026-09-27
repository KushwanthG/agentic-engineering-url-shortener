package com.agentic.urlshortener.shortener.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.agentic.urlshortener.shortener.domain.Capability;

/**
 * Default link expiry (SCN-C decision D1, FR-CAP-04): while the capability is released with the release
 * parameter {@code defaultExpiryDays}, a link created without an expiry expires that many days after
 * creation. The rule applies at creation only, so existing links are never changed (decision D4), and
 * expiries already assigned remain after the capability is withdrawn.
 */
@Component
public class DefaultExpiryCapability {

    static final String PARAMETER = "defaultExpiryDays";
    /** PVT-07: the maximum expiry horizon is 5 years. */
    private static final long MAX_DAYS = 5 * 365;

    private final CapabilityService capabilities;

    public DefaultExpiryCapability(CapabilityService capabilities) {
        this.capabilities = capabilities;
    }

    /** The expiry a new link without an explicit expiry receives, if the capability is released. */
    public Optional<Instant> defaultExpiry(Instant createdAt) {
        if (!capabilities.isReleased(Capability.DEFAULT_EXPIRY)) {
            return Optional.empty();
        }
        Object days = capabilities.parameters(Capability.DEFAULT_EXPIRY).get(PARAMETER);
        if (!(days instanceof Number number) || number.longValue() < 1 || number.longValue() > MAX_DAYS) {
            return Optional.empty();
        }
        return Optional.of(createdAt.plus(number.longValue(), ChronoUnit.DAYS));
    }
}
