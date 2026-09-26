package com.agentic.urlshortener.orchestration.agent;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.agentic.urlshortener.orchestration.audit.AuditService;
import com.agentic.urlshortener.orchestration.domain.ActorType;
import com.agentic.urlshortener.orchestration.domain.AuditRecord;
import com.agentic.urlshortener.orchestration.domain.StageType;
import com.agentic.urlshortener.orchestration.port.PermissionScopedPort;

/** Records every denied port call of an agent in the run's audit chain (NFR-AUT-01). */
@Component
public class PermissionAudit implements PermissionScopedPort.DeniedListener {

    private final AuditService audit;

    public PermissionAudit(AuditService audit) {
        this.audit = audit;
    }

    @Override
    public void denied(UUID runId, StageType stage, String agentId, AgentPermission permission, String operation) {
        audit.append(new AuditRecord(runId, ActorType.AGENT, agentId, "AGENT_PERMISSION_DENIED", stage.name(), null, null,
                "DENIED", "called " + operation + " without permission " + permission.name(), null));
    }
}
