package com.agentic.urlshortener.orchestration.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.agentic.urlshortener.orchestration.domain.PolicyExceptionRecord;

public interface PolicyExceptionRepository extends JpaRepository<PolicyExceptionRecord, UUID> {

    List<PolicyExceptionRecord> findByRunIdOrderByRequestedAtAsc(UUID runId);
}
