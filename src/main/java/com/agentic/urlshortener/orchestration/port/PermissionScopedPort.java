package com.agentic.urlshortener.orchestration.port;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import com.agentic.urlshortener.orchestration.agent.AgentPermission;
import com.agentic.urlshortener.orchestration.agent.AgentPermissionDeniedException;
import com.agentic.urlshortener.orchestration.domain.StageType;

/**
 * The proxy agents receive instead of the real port: every call is checked against the agent's
 * declared permissions; a denied call is reported to the listener (audited) and throws, without
 * reaching the application plane (NFR-AUT-01).
 */
public final class PermissionScopedPort implements ApplicationPlanePort {

    /** Receives denied calls (implemented by the audit trail). */
    public interface DeniedListener {
        void denied(UUID runId, StageType stage, String agentId, AgentPermission permission, String operation);
    }

    private final ApplicationPlanePort delegate;
    private final Set<AgentPermission> granted;
    private final UUID runId;
    private final StageType stage;
    private final String agentId;
    private final DeniedListener listener;

    public PermissionScopedPort(ApplicationPlanePort delegate, Set<AgentPermission> granted, UUID runId, StageType stage,
            String agentId, DeniedListener listener) {
        this.delegate = delegate;
        this.granted = Set.copyOf(granted);
        this.runId = runId;
        this.stage = stage;
        this.agentId = agentId;
        this.listener = listener;
    }

    private void require(AgentPermission permission, String operation) {
        if (!granted.contains(permission)) {
            listener.denied(runId, stage, agentId, permission, operation);
            throw new AgentPermissionDeniedException(agentId, permission, operation);
        }
    }

    @Override
    public Optional<LinkSnapshot> findLink(String code) {
        require(AgentPermission.READ_LINKS, "findLink");
        return delegate.findLink(code);
    }

    @Override
    public ProbeResponse resolve(String code) {
        require(AgentPermission.READ_LINKS, "resolve");
        return delegate.resolve(code);
    }

    @Override
    public ProbeResponse createSyntheticLink(UUID runId, SyntheticLinkSpec spec) {
        require(AgentPermission.WRITE_SYNTHETIC_LINKS, "createSyntheticLink");
        return delegate.createSyntheticLink(this.runId, spec);
    }

    @Override
    public int deleteSyntheticLinks(UUID runId) {
        require(AgentPermission.WRITE_SYNTHETIC_LINKS, "deleteSyntheticLinks");
        return delegate.deleteSyntheticLinks(this.runId);
    }

    @Override
    public CapabilityState capability(String capabilityId) {
        require(AgentPermission.READ_CAPABILITIES, "capability");
        return delegate.capability(capabilityId);
    }

    @Override
    public DeliveryStatus deliveryStatus(String capabilityId, String migrationVersion, List<String> contractFields) {
        require(AgentPermission.READ_CAPABILITIES, "deliveryStatus");
        return delegate.deliveryStatus(capabilityId, migrationVersion, contractFields);
    }

    @Override
    public <T> T withPreview(String capabilityId, Map<String, Object> parameters, Supplier<T> action) {
        require(AgentPermission.PREVIEW_CAPABILITY, "withPreview");
        return delegate.withPreview(capabilityId, parameters, action);
    }

    @Override
    public <T> T withSyntheticClickOutage(Supplier<T> action) {
        require(AgentPermission.PREVIEW_CAPABILITY, "withSyntheticClickOutage");
        return delegate.withSyntheticClickOutage(action);
    }

    @Override
    public CapabilityState setRelease(String capabilityId, boolean released, Map<String, Object> parameters, UUID runId,
            String reason) {
        require(AgentPermission.CHANGE_CAPABILITY_RELEASE, "setRelease");
        return delegate.setRelease(capabilityId, released, parameters, this.runId, reason);
    }
}
