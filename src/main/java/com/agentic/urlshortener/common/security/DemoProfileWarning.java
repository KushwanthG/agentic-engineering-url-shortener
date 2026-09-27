package com.agentic.urlshortener.common.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * ADR-015 mitigation: when the demo profile is active, the application announces at startup that it
 * runs with published, non-production demo credentials and simulation features. No token or hash is
 * logged.
 */
@Component
@Profile("demo")
class DemoProfileWarning {

    private static final Logger log = LoggerFactory.getLogger(DemoProfileWarning.class);

    @EventListener(ApplicationReadyEvent.class)
    void warn() {
        log.warn("DEMO PROFILE ACTIVE: labeled NON-PRODUCTION demo principals and fault injection are enabled. "
                + "The demo tokens are published in the documentation; never expose this instance or reuse them.");
    }
}
