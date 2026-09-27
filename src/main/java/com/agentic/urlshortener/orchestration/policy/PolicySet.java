package com.agentic.urlshortener.orchestration.policy;

import java.util.List;
import java.util.Optional;

/** A versioned policy set (FR-POL-01); each run pins the version it was created with. */
public record PolicySet(String version, List<PolicyDefinition> policies) {

    public PolicySet {
        policies = List.copyOf(policies);
    }

    /** One policy: identity, domain, severity, applicability, and description. */
    public record PolicyDefinition(String id, String title, String domain, String severity, String appliesWhen,
            String description) {

        public boolean mandatory() {
            return "MANDATORY".equals(severity);
        }
    }

    public Optional<PolicyDefinition> policy(String id) {
        return policies.stream().filter(p -> p.id().equals(id)).findFirst();
    }
}
