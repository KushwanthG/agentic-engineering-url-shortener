package com.agentic.urlshortener.orchestration.reliability;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.agentic.urlshortener.common.util.CanonicalJson;
import com.agentic.urlshortener.orchestration.domain.Artifact;
import com.agentic.urlshortener.orchestration.engine.ArtifactStore;
import com.agentic.urlshortener.orchestration.engine.RunAudit;
import com.agentic.urlshortener.orchestration.port.ApplicationPlanePort;
import com.agentic.urlshortener.orchestration.port.CapabilityState;

import tools.jackson.databind.JsonNode;

/**
 * Compensates the completed side effects of a run that will not complete (ADR-010, plan.md §6), in
 * reverse completion order: first the release (rollback of the capability flag, only while the flag
 * still holds this run's value), then the run's synthetic probe data. Each action is tried up to
 * three times (PVT-16) and audited as {@code COMPENSATION_ACTION}; consumer data is never touched.
 */
@Component
public class CompensationCoordinator {

    public static final String SYNTHETIC_DATA = "SYNTHETIC_DATA";
    public static final String RELEASE = "RELEASE";
    static final int MAX_TRIES = 3;

    private final ObjectProvider<ApplicationPlanePort> port;
    private final ArtifactStore artifacts;
    private final RunAudit audit;
    private final FaultInjector faults;

    public CompensationCoordinator(ObjectProvider<ApplicationPlanePort> port, ArtifactStore artifacts, RunAudit audit,
            FaultInjector faults) {
        this.faults = faults;
        this.port = port;
        this.artifacts = artifacts;
        this.audit = audit;
    }

    /** Outcome of one compensation action: {@code OK}, {@code CONFLICT} (left as is, recorded), or {@code FAILED}. */
    public record Action(String target, String result, String detail, int tries) {
    }

    /** All actions of one compensation; {@code succeeded} is false when any action failed (manual intervention). */
    public record Result(List<Action> actions) {

        public boolean succeeded() {
            return actions.stream().noneMatch(a -> "FAILED".equals(a.result()));
        }
    }

    /**
     * Runs the compensation actions of {@code runId}. The synthetic-data deletion flushes and clears the
     * persistence context, so callers must re-load entities they change afterwards.
     */
    public Result compensate(UUID runId, String reason) {
        List<Action> actions = new ArrayList<>();
        Artifact releaseRecord = artifacts.current(runId).get("RELEASE_RECORD");
        if (releaseRecord != null) {
            actions.add(record(runId, reason, rollBackRelease(runId, CanonicalJson.parse(releaseRecord.getContent()))));
        }
        actions.add(record(runId, reason, withRetries(SYNTHETIC_DATA, () -> {
            if (faults.compensationFails(runId)) {
                throw new IllegalStateException("simulated compensation failure (fault injection)");
            }
            int removed = port.getObject().deleteSyntheticLinks(runId);
            return new Action(SYNTHETIC_DATA, "OK", "removed " + removed + " synthetic link(s) of the run", 0);
        })));
        return new Result(actions);
    }

    private Action rollBackRelease(UUID runId, JsonNode record) {
        String capability = record.path("capability").asString();
        if (!record.path("released").asBoolean() || record.path("rolledBack").asBoolean()) {
            return new Action(RELEASE, "OK", "nothing to roll back: " + capability + " was not left released by this run", 1);
        }
        return withRetries(RELEASE, () -> {
            CapabilityState state = port.getObject().capability(capability);
            if (!runId.equals(state.changedByRun())) {
                return new Action(RELEASE, "CONFLICT", "not rolled back: " + capability + " was changed by run " + state.changedByRun()
                        + " after this run released it; its current state is kept", 0);
            }
            if (record.path("previouslyReleased").asBoolean()) {
                return new Action(RELEASE, "FAILED", "not rolled back: " + capability + " was already released before this run and "
                        + "the release record does not hold the previous parameters; manual review required", 0);
            }
            port.getObject().setRelease(capability, false, Map.of(), runId, "rollback: compensation of run " + runId);
            return new Action(RELEASE, "OK", "release of " + capability + " rolled back to unreleased", 0);
        });
    }

    private static Action withRetries(String target, Supplier<Action> action) {
        RuntimeException last = null;
        for (int tries = 1; tries <= MAX_TRIES; tries++) {
            try {
                Action done = action.get();
                return new Action(done.target(), done.result(), done.detail(), tries);
            } catch (RuntimeException e) {
                last = e;
            }
        }
        return new Action(target, "FAILED", "failed after " + MAX_TRIES + " tries: " + last.getClass().getSimpleName() + ": "
                + last.getMessage(), MAX_TRIES);
    }

    private Action record(UUID runId, String reason, Action action) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("detail", action.detail());
        details.put("tries", action.tries());
        audit.system(runId, "COMPENSATION_ACTION", action.target(), action.result(), reason, details);
        return action;
    }
}
