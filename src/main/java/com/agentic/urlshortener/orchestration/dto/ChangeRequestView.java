package com.agentic.urlshortener.orchestration.dto;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.agentic.urlshortener.common.util.CanonicalJson;
import com.agentic.urlshortener.orchestration.domain.ChangeRequest;
import com.fasterxml.jackson.annotation.JsonInclude;

import tools.jackson.databind.JsonNode;

/** A change request (contract schema {@code ChangeRequest}); undecided fields are omitted. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChangeRequestView(String changeRequestId, String status, boolean material, String requestedBy, Instant requestedAt,
        String reason, Impact impact, String decidedBy, Instant decidedAt) {

    /** Stages the change re-opens, approvals re-validated against their bound artifacts, and why the change is material. */
    public record Impact(List<String> affectedStages, List<String> invalidatedApprovals, List<String> materialReasons) {
    }

    public static ChangeRequestView of(ChangeRequest request) {
        JsonNode impact = CanonicalJson.parse(request.getImpact());
        return new ChangeRequestView(request.getId().toString(), request.getStatus(), request.isMaterial(), request.getRequestedBy(),
                request.getRequestedAt(), request.getReason(), new Impact(strings(impact.path("affectedStages")),
                        strings(impact.path("invalidatedApprovals")), strings(impact.path("materialReasons"))),
                request.getDecidedBy(), request.getDecidedAt());
    }

    private static List<String> strings(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(v -> values.add(v.asString()));
        return values;
    }
}
