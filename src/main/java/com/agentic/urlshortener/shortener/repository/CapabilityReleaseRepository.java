package com.agentic.urlshortener.shortener.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.agentic.urlshortener.shortener.domain.CapabilityRelease;

public interface CapabilityReleaseRepository extends JpaRepository<CapabilityRelease, String> {
}
