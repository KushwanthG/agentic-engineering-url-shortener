package com.agentic.urlshortener.orchestration.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.agentic.urlshortener.orchestration.agent.probes.AcceptanceProbe;
import com.agentic.urlshortener.orchestration.agent.probes.ProbeContext;
import com.agentic.urlshortener.orchestration.agent.probes.ProbeRegistry;
import com.agentic.urlshortener.orchestration.agent.probes.SyntheticScope;
import com.agentic.urlshortener.orchestration.domain.FailureClass;
import com.agentic.urlshortener.orchestration.domain.StageType;

import tools.jackson.databind.JsonNode;

/**
 * TESTING (FR-ORC-16): runs the capability's acceptance probes against the running service, with the
 * unreleased capability enabled only for this call (preview), and records a result per acceptance
 * criterion. Synthetic links are removed in {@code finally}. A criterion without a passing probe, or
 * any failing probe, fails the stage: verification never degrades.
 */
@Component
public class TestingAgent implements StageAgent {

    private final ProbeRegistry probes;

    public TestingAgent(ProbeRegistry probes) {
        this.probes = probes;
    }

    @Override
    public StageType stageType() {
        return StageType.TESTING;
    }

    @Override
    public String agentId() {
        return "acceptance-tester@1.0";
    }

    @Override
    public Set<AgentPermission> permissions() {
        return Set.of(AgentPermission.READ_LINKS, AgentPermission.WRITE_SYNTHETIC_LINKS, AgentPermission.PREVIEW_CAPABILITY);
    }

    @Override
    public StageResult execute(StageContext context) {
        JsonNode design = context.inputJson("DESIGN");
        String capability = design.path("releasePlan").path("capability").asString();
        List<AcceptanceProbe> acceptance = probes.acceptance(capability);
        if (acceptance.isEmpty()) {
            return new StageResult.Failed(FailureClass.PERMANENT, "no acceptance probes exist for capability " + capability);
        }
        Map<String, String> criteria = AgentInputs.criteria(context.inputJson("NORMALIZED_REQUIREMENT"));
        ProbeContext probeContext = new ProbeContext(context.runId(), context.port(),
                ProbeRuns.releaseParameters(design));
        int[] removed = new int[1];
        List<ProbeRuns.Result> results = SyntheticScope.exclusive(context.runId(), () -> {
            try {
                return context.port().withPreview(capability, ProbeRuns.releaseParameters(design), () -> {
                    List<ProbeRuns.Result> executed = new ArrayList<>();
                    for (AcceptanceProbe probe : acceptance) {
                        List<String> verifies = criteria.entrySet().stream().filter(e -> probe.verifiesCriterion(e.getValue()))
                                .map(Map.Entry::getKey).toList();
                        executed.add(ProbeRuns.run(probe, probeContext, verifies));
                    }
                    return executed;
                });
            } finally {
                removed[0] = context.port().deleteSyntheticLinks(context.runId());
            }
        });

        List<String> unverified = criteria.keySet().stream()
                .filter(id -> results.stream().noneMatch(r -> r.outcome().passed() && r.verifies().contains(id))).toList();
        List<String> failedProbes = results.stream().filter(r -> !r.outcome().passed())
                .map(r -> r.probeId() + ": " + r.outcome().evidence()).toList();
        if (!unverified.isEmpty() || !failedProbes.isEmpty()) {
            return new StageResult.Failed(FailureClass.PERMANENT, "acceptance verification failed: unverified criteria "
                    + unverified + "; failed probes " + failedProbes);
        }
        return new StageResult.Succeeded(List.of(ArtifactDraft.json("TEST_REPORT",
                ProbeRuns.report("ACCEPTANCE", results, unverified, probeContext.created(), removed[0]))),
                results.size() + " probes passed; " + criteria.size() + " acceptance criteria verified");
    }
}
