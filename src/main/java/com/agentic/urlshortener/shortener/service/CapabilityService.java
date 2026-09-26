package com.agentic.urlshortener.shortener.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.agentic.urlshortener.common.exception.ApiException;
import com.agentic.urlshortener.common.exception.ErrorCode;
import com.agentic.urlshortener.shortener.domain.Capability;
import com.agentic.urlshortener.shortener.domain.CapabilityRelease;
import com.agentic.urlshortener.shortener.repository.CapabilityReleaseRepository;

/**
 * Read side of capability release flags (ADR-018). A capability without a row, or with an
 * unreleased row, is not available; inputs that need it are rejected, never silently ignored
 * (FR-CAP-01).
 */
@Service
public class CapabilityService {

    private final CapabilityReleaseRepository releases;

    public CapabilityService(CapabilityReleaseRepository releases) {
        this.releases = releases;
    }

    @Transactional(readOnly = true)
    public boolean isReleased(Capability capability) {
        return releases.findById(capability.id()).map(CapabilityRelease::isReleased).orElse(false);
    }

    public void requireReleased(Capability capability, String input) {
        if (!isReleased(capability)) {
            throw new ApiException(ErrorCode.CAPABILITY_NOT_AVAILABLE,
                    "'" + input + "' needs the '" + capability.id() + "' capability, which is not released.");
        }
    }
}
