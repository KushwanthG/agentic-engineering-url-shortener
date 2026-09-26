package com.agentic.urlshortener.orchestration.port;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The only way the control plane reaches the URL shortener (ADR-001). It offers no operation on
 * non-synthetic consumer data beyond reading, so no agent can modify or delete consumers' links
 * (FR-GOV-01). Each operation needs an {@code AgentPermission}; agents only ever receive a
 * {@link PermissionScopedPort}.
 */
public interface ApplicationPlanePort {

    /** READ_LINKS. */
    Optional<LinkSnapshot> findLink(String code);

    /** READ_LINKS: resolves a code exactly like a public redirect (counted as a click). */
    ProbeResponse resolve(String code);

    /** WRITE_SYNTHETIC_LINKS: creates a link labeled synthetic and owned by the run. */
    ProbeResponse createSyntheticLink(UUID runId, SyntheticLinkSpec spec);

    /** WRITE_SYNTHETIC_LINKS: deletes the synthetic links of one run only. */
    int deleteSyntheticLinks(UUID runId);

    /** READ_CAPABILITIES. */
    CapabilityState capability(String capabilityId);

    /** READ_CAPABILITIES: live checks that a capability is delivered (provider, migration, contract). */
    DeliveryStatus deliveryStatus(String capabilityId, String migrationVersion, List<String> contractFields);

    /** PREVIEW_CAPABILITY: runs {@code action} with the capability enabled for the calling thread only. */
    <T> T withPreview(String capabilityId, Map<String, Object> parameters, Supplier<T> action);

    /** CHANGE_CAPABILITY_RELEASE (release stage only): sets (not toggles) the release state; idempotent. */
    CapabilityState setRelease(String capabilityId, boolean released, Map<String, Object> parameters, UUID runId, String reason);
}
