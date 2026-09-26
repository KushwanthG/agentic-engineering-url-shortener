package com.agentic.urlshortener.orchestration.agent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.agentic.urlshortener.common.util.CanonicalJson;
import com.agentic.urlshortener.orchestration.agent.probes.AcceptanceProbe;
import com.agentic.urlshortener.orchestration.agent.probes.ProbeContext;
import com.agentic.urlshortener.orchestration.agent.probes.ProbeOutcome;
import com.agentic.urlshortener.orchestration.agent.probes.ProbeRegistry;
import com.agentic.urlshortener.orchestration.agent.probes.SyntheticScope;
import com.agentic.urlshortener.orchestration.domain.FailureClass;
import com.agentic.urlshortener.orchestration.domain.StageType;
import com.agentic.urlshortener.orchestration.port.CapabilityState;

import tools.jackson.databind.JsonNode;

/**
 * RELEASE (FR-RDY-03, ADR-010, ADR-018): the only agent allowed to change release state. It sets the
 * capability's release flag with the designed parameters and verifies it right after release with
 * a smoke probe that runs without preview. If verification fails, the release state is rolled back
 * to its previous value (rollback is claimed only for this system-owned state) and the stage fails
 * permanently. Synthetic smoke data is removed in {@code finally}.
 */
@Component
public class ReleaseAgent implements StageAgent {

    private final ProbeRegistry probes;

    public ReleaseAgent(ProbeRegistry probes) {
        this.probes = probes;
    }

    @Override
    public StageType stageType() {
        return StageType.RELEASE;
    }

    @Override
    public String agentId() {
        return "release-manager@1.0";
    }

    @Override
    public Set<AgentPermission> permissions() {
        return Set.of(AgentPermission.CHANGE_CAPABILITY_RELEASE, AgentPermission.READ_CAPABILITIES, AgentPermission.READ_LINKS,
                AgentPermission.WRITE_SYNTHETIC_LINKS);
    }

    @Override
    public StageResult execute(StageContext context) {
        JsonNode design = context.inputJson("DESIGN");
        String capability = design.path("releasePlan").path("capability").asString();
        Map<String, Object> parameters = ProbeRuns.releaseParameters(design);
        CapabilityState previous = context.port().capability(capability);

        context.port().setRelease(capability, true, parameters, context.runId(), "released after release approval");
        ProbeOutcome verification = SyntheticScope.exclusive(context.runId(), () -> {
            try {
                return probes.smoke(capability)
                        .map(probe -> probe.run(new ProbeContext(context.runId(), context.port())))
                        .orElse(ProbeOutcome.fail("no smoke probe is registered for " + capability));
            } finally {
                context.port().deleteSyntheticLinks(context.runId());
            }
        });

        if (!verification.passed()) {
            // Roll back only while the flag still holds this run's value (ADR-010); otherwise record the conflict.
            CapabilityState current = context.port().capability(capability);
            if (!context.runId().equals(current.changedByRun())) {
                return new StageResult.Failed(FailureClass.PERMANENT, "post-release verification failed (" + verification.evidence()
                        + "); rollback skipped: " + capability + " was changed by run " + current.changedByRun()
                        + " after this run released it (conflict recorded; manual review)");
            }
            context.port().setRelease(capability, previous.released(), previous.parameters(), context.runId(),
                    "rollback: post-release verification failed");
            return new StageResult.Failed(FailureClass.PERMANENT, "post-release verification failed (" + verification.evidence()
                    + "); release state of " + capability + " rolled back to released=" + previous.released());
        }
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("capability", capability);
        record.put("previouslyReleased", previous.released());
        record.put("released", true);
        record.put("parameters", parameters);
        record.put("verification", Map.of("passed", true, "evidence", smokeId(capability) + ": " + verification.evidence()));
        record.put("rolledBack", false);
        record.put("mechanism", "NONE");
        return new StageResult.Succeeded(List.of(ArtifactDraft.json("RELEASE_RECORD", CanonicalJson.write(record))),
                capability + " released and verified");
    }

    private String smokeId(String capability) {
        return probes.smoke(capability).map(AcceptanceProbe::id).orElse("none");
    }
}
