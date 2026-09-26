package com.agentic.urlshortener.shortener.service;

import org.springframework.stereotype.Component;

import com.agentic.urlshortener.shortener.domain.AliasPolicy;
import com.agentic.urlshortener.shortener.domain.Capability;

/**
 * The custom-alias capability (GF-001): an alias is accepted only while the capability is released
 * (or previewed by a verification run) and only if it satisfies the alias policy.
 */
@Component
public class CustomAliasCapability {

    private final CapabilityService capabilities;
    private final AliasPolicy policy;

    public CustomAliasCapability(CapabilityService capabilities, AliasPolicy policy) {
        this.capabilities = capabilities;
        this.policy = policy;
    }

    /** Returns the alias to use as the short code, or throws CAPABILITY_NOT_AVAILABLE, INVALID_ALIAS, or RESERVED_ALIAS. */
    public String require(String alias) {
        capabilities.requireReleased(Capability.CUSTOM_ALIAS, "alias");
        return policy.validate(alias);
    }
}
