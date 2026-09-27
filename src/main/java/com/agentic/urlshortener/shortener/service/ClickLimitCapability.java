package com.agentic.urlshortener.shortener.service;

import org.springframework.stereotype.Component;

import com.agentic.urlshortener.common.exception.ApiException;
import com.agentic.urlshortener.common.exception.ErrorCode;
import com.agentic.urlshortener.shortener.domain.Capability;

/**
 * Click-limited links (BF-001, contract 1.2.0, PVT-09): a new link may carry {@code maxClicks} in
 * 1..1,000,000 while the capability is released. Enforcement of stored limits does not depend on the
 * flag, so withdrawing the capability never lifts existing limits (Q4).
 */
@Component
public class ClickLimitCapability {

    static final long MIN = 1;
    static final long MAX = 1_000_000;

    private final CapabilityService capabilities;

    public ClickLimitCapability(CapabilityService capabilities) {
        this.capabilities = capabilities;
    }

    /** Returns the limit to store, or throws CAPABILITY_NOT_AVAILABLE or INVALID_CLICK_LIMIT. */
    public long require(long maxClicks) {
        capabilities.requireReleased(Capability.CLICK_LIMIT, "maxClicks");
        if (maxClicks < MIN || maxClicks > MAX) {
            throw new ApiException(ErrorCode.INVALID_CLICK_LIMIT, "maxClicks must be between " + MIN + " and " + MAX + ".");
        }
        return maxClicks;
    }
}
