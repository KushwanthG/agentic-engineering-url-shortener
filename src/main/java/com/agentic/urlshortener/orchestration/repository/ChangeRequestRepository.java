package com.agentic.urlshortener.orchestration.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.agentic.urlshortener.orchestration.domain.ChangeRequest;

public interface ChangeRequestRepository extends JpaRepository<ChangeRequest, UUID> {

    List<ChangeRequest> findByRunIdOrderByRequestedAtAsc(UUID runId);
}
