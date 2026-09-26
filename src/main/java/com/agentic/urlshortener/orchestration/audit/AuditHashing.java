package com.agentic.urlshortener.orchestration.audit;

import java.util.LinkedHashMap;
import java.util.Map;

import com.agentic.urlshortener.common.util.CanonicalJson;
import com.agentic.urlshortener.common.util.Fingerprints;
import com.agentic.urlshortener.orchestration.domain.AuditEvent;

/**
 * {@code hash = SHA-256(canonical JSON of prevHash and every event field)}. Canonical JSON (rather
 * than a delimiter-joined string) makes the encoding unambiguous, so no two different events can
 * share hash input.
 */
public final class AuditHashing {

    public static final String GENESIS = "0".repeat(64);

    private AuditHashing() {
    }

    public static String hash(AuditEvent event) {
        Map<String, Object> material = new LinkedHashMap<>();
        material.put("prevHash", event.getPrevHash());
        material.put("chainId", event.getChainId());
        material.put("seq", event.getSeq());
        material.put("runId", event.getRunId() == null ? null : event.getRunId().toString());
        material.put("occurredAt", event.getOccurredAt().toString());
        material.put("actorType", event.getActorType().name());
        material.put("actorId", event.getActorId());
        material.put("action", event.getAction());
        material.put("target", event.getTarget());
        material.put("fromState", event.getFromState());
        material.put("toState", event.getToState());
        material.put("result", event.getResult());
        material.put("reason", event.getReason());
        material.put("details", event.getDetails());
        return Fingerprints.sha256(CanonicalJson.write(material));
    }
}
