package com.agentic.urlshortener.orchestration.port;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/** Resolves the real port on first use, so the engine does not require an adapter until an agent calls one. */
public final class DeferredApplicationPlanePort implements ApplicationPlanePort {

    private final Supplier<ApplicationPlanePort> target;

    public DeferredApplicationPlanePort(Supplier<ApplicationPlanePort> target) {
        this.target = target;
    }

    @Override
    public Optional<LinkSnapshot> findLink(String code) {
        return target.get().findLink(code);
    }

    @Override
    public ProbeResponse resolve(String code) {
        return target.get().resolve(code);
    }

    @Override
    public ProbeResponse createSyntheticLink(UUID runId, SyntheticLinkSpec spec) {
        return target.get().createSyntheticLink(runId, spec);
    }

    @Override
    public int deleteSyntheticLinks(UUID runId) {
        return target.get().deleteSyntheticLinks(runId);
    }

    @Override
    public CapabilityState capability(String capabilityId) {
        return target.get().capability(capabilityId);
    }

    @Override
    public DeliveryStatus deliveryStatus(String capabilityId, String migrationVersion, List<String> contractFields) {
        return target.get().deliveryStatus(capabilityId, migrationVersion, contractFields);
    }

    @Override
    public <T> T withPreview(String capabilityId, Map<String, Object> parameters, Supplier<T> action) {
        return target.get().withPreview(capabilityId, parameters, action);
    }

    @Override
    public <T> T withSyntheticClickOutage(Supplier<T> action) {
        return target.get().withSyntheticClickOutage(action);
    }

    @Override
    public CapabilityState setRelease(String capabilityId, boolean released, Map<String, Object> parameters, UUID runId,
            String reason) {
        return target.get().setRelease(capabilityId, released, parameters, runId, reason);
    }
}
