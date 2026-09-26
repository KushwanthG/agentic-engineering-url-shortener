package com.agentic.urlshortener.orchestration.dto;

import java.time.Instant;
import java.util.Map;

import com.agentic.urlshortener.common.util.CanonicalJson;
import com.agentic.urlshortener.orchestration.domain.Decision;
import com.fasterxml.jackson.annotation.JsonInclude;

/** A recorded decision ({@code openapi.yaml#/components/schemas/Decision}). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DecisionView(
        String decisionId,
        String type,
        String outcome,
        String stageKey,
        String actorType,
        String actorId,
        String actorRole,
        String rationale,
        Map<String, String> boundFingerprints,
        String payload,
        boolean valid,
        String invalidatedReason,
        Instant createdAt) {

    @SuppressWarnings("unchecked")
    public static DecisionView of(Decision decision) {
        Map<String, String> bound = decision.getBoundFingerprints() == null ? null
                : CanonicalJson.read(decision.getBoundFingerprints(), Map.class);
        return new DecisionView(decision.getId().toString(), decision.getDecisionType().name(), decision.getOutcome(),
                decision.getStageKey() == null ? null : decision.getStageKey().name(), decision.getActorType().name(),
                decision.getActorId(), decision.getActorRole(), decision.getRationale(), bound, decision.getPayload(),
                decision.isValid(), decision.getInvalidatedReason(), decision.getCreatedAt());
    }
}
