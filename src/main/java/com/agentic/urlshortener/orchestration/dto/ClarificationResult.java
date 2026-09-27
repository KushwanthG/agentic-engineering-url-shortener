package com.agentic.urlshortener.orchestration.dto;

import java.util.List;

/** Outcome of answering a clarification (contract schema {@code ClarificationResult}). */
public record ClarificationResult(int requirementVersion, int planVersion, List<DecisionView> decisions) {
}
