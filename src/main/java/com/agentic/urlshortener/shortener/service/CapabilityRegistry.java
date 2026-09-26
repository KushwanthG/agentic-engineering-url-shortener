package com.agentic.urlshortener.shortener.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import com.agentic.urlshortener.shortener.domain.Capability;
import com.agentic.urlshortener.shortener.domain.CapabilityRelease;
import com.agentic.urlshortener.shortener.repository.CapabilityReleaseRepository;

/** Creates an unreleased {@code capability_release} row for every known capability at startup. */
@Component
public class CapabilityRegistry implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CapabilityRegistry.class);

    private final CapabilityReleaseRepository releases;

    public CapabilityRegistry(CapabilityReleaseRepository releases) {
        this.releases = releases;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (Capability capability : Capability.values()) {
            if (!releases.existsById(capability.id())) {
                try {
                    releases.saveAndFlush(CapabilityRelease.unreleased(capability.id()));
                    log.info("Registered capability '{}' (unreleased)", capability.id());
                } catch (DataIntegrityViolationException e) {
                    log.debug("Capability '{}' was registered concurrently", capability.id());
                }
            }
        }
    }
}
