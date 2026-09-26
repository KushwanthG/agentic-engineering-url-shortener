package com.agentic.urlshortener.orchestration.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.agentic.urlshortener.orchestration.domain.FailureEvent;

public interface FailureEventRepository extends JpaRepository<FailureEvent, UUID> {

    List<FailureEvent> findByRunId(UUID runId);
}
