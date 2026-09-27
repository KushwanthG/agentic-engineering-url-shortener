package com.agentic.urlshortener.orchestration.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.agentic.urlshortener.orchestration.domain.PolicyEvaluation;

public interface PolicyEvaluationRepository extends JpaRepository<PolicyEvaluation, UUID> {

    List<PolicyEvaluation> findByRunIdOrderByEvaluatedAtAsc(UUID runId);
}
