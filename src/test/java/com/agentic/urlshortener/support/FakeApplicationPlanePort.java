package com.agentic.urlshortener.support;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import com.agentic.urlshortener.orchestration.port.ApplicationPlanePort;
import com.agentic.urlshortener.orchestration.port.CapabilityState;
import com.agentic.urlshortener.orchestration.port.DeliveryStatus;
import com.agentic.urlshortener.orchestration.port.LinkSnapshot;
import com.agentic.urlshortener.orchestration.port.ProbeResponse;
import com.agentic.urlshortener.orchestration.port.SyntheticLinkSpec;

/** A scriptable port for agent unit tests; unscripted calls fail loudly. */
public final class FakeApplicationPlanePort implements ApplicationPlanePort {

    /** Scripted answer of {@link #deliveryStatus}. */
    public interface Delivery {
        DeliveryStatus answer(String capabilityId, String migrationVersion, List<String> contractFields);
    }

    public Delivery delivery;

    @Override
    public Optional<LinkSnapshot> findLink(String code) {
        throw new UnsupportedOperationException("findLink not scripted");
    }

    @Override
    public ProbeResponse resolve(String code) {
        throw new UnsupportedOperationException("resolve not scripted");
    }

    @Override
    public ProbeResponse createSyntheticLink(UUID runId, SyntheticLinkSpec spec) {
        throw new UnsupportedOperationException("createSyntheticLink not scripted");
    }

    @Override
    public int deleteSyntheticLinks(UUID runId) {
        throw new UnsupportedOperationException("deleteSyntheticLinks not scripted");
    }

    @Override
    public CapabilityState capability(String capabilityId) {
        throw new UnsupportedOperationException("capability not scripted");
    }

    @Override
    public DeliveryStatus deliveryStatus(String capabilityId, String migrationVersion, List<String> contractFields) {
        if (delivery == null) {
            throw new UnsupportedOperationException("deliveryStatus not scripted");
        }
        return delivery.answer(capabilityId, migrationVersion, contractFields);
    }

    @Override
    public <T> T withPreview(String capabilityId, Map<String, Object> parameters, Supplier<T> action) {
        throw new UnsupportedOperationException("withPreview not scripted");
    }

    @Override
    public CapabilityState setRelease(String capabilityId, boolean released, Map<String, Object> parameters, UUID runId, String reason) {
        throw new UnsupportedOperationException("setRelease not scripted");
    }
}
