package com.agentic.urlshortener.orchestration.reliability;

import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.agentic.urlshortener.orchestration.engine.RunAudit;
import com.agentic.urlshortener.orchestration.port.ApplicationPlanePort;

/**
 * Compensates the completed side effects of a run that will not complete (ADR-010). Only data the
 * run owns is touched: synthetic probe links are deleted by run; consumer data is never modified.
 * Every action is audited as {@code COMPENSATION_ACTION}.
 */
@Component
public class CompensationCoordinator {

    public static final String SYNTHETIC_DATA = "SYNTHETIC_DATA";

    private final ObjectProvider<ApplicationPlanePort> port;
    private final RunAudit audit;

    public CompensationCoordinator(ObjectProvider<ApplicationPlanePort> port, RunAudit audit) {
        this.port = port;
        this.audit = audit;
    }

    /**
     * Runs the compensation actions of {@code runId}; returns whether all of them succeeded. The
     * synthetic-data deletion flushes and clears the persistence context, so callers must re-load
     * entities they change afterwards.
     */
    public boolean compensate(UUID runId, String reason) {
        return removeSyntheticData(runId, reason);
    }

    private boolean removeSyntheticData(UUID runId, String reason) {
        int removed = port.getObject().deleteSyntheticLinks(runId);
        audit.system(runId, "COMPENSATION_ACTION", SYNTHETIC_DATA, "OK", reason, Map.of("removed", removed));
        return true;
    }
}
