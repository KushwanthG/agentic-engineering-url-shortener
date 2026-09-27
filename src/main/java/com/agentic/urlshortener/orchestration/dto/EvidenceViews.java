package com.agentic.urlshortener.orchestration.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.agentic.urlshortener.orchestration.domain.AuditEvent;
import com.agentic.urlshortener.orchestration.policy.PolicySet;
import com.agentic.urlshortener.orchestration.port.CapabilityReleaseReader.CapabilityRecord;
import com.fasterxml.jackson.annotation.JsonInclude;

/** Views of the evidence API ({@code openapi.yaml} tag {@code evidence}). */
public final class EvidenceViews {

    private EvidenceViews() {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AuditEventView(String chainId, long seq, Instant occurredAt, String actorType, String actorId, String action,
            String target, String fromState, String toState, String result, String reason, String details, String prevHash,
            String hash) {

        public static AuditEventView of(AuditEvent e) {
            return new AuditEventView(e.getChainId(), e.getSeq(), e.getOccurredAt(), e.getActorType().name(), e.getActorId(),
                    e.getAction(), e.getTarget(), e.getFromState(), e.getToState(), e.getResult(), e.getReason(), e.getDetails(),
                    e.getPrevHash(), e.getHash());
        }
    }

    public record PolicySetView(String version, List<PolicyView> policies) {

        public static PolicySetView of(PolicySet set) {
            return new PolicySetView(set.version(), set.policies().stream()
                    .map(p -> new PolicyView(p.id(), p.title(), p.domain(), p.severity(), p.description())).toList());
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PolicyView(String id, String title, String domain, String severity, String description) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CapabilityView(String capabilityId, boolean released, Map<String, Object> parameters, String changedBy,
            String changedByRun, Instant changedAt, String reason) {

        public static CapabilityView of(CapabilityRecord c) {
            return new CapabilityView(c.capabilityId(), c.released(), c.parameters(), c.changedBy(),
                    c.changedByRun() == null ? null : c.changedByRun().toString(), c.changedAt(), c.reason());
        }
    }
}
