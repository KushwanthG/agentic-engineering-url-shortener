package com.agentic.urlshortener.orchestration.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.agentic.urlshortener.orchestration.agent.probes.AcceptanceProbe;
import com.agentic.urlshortener.orchestration.agent.probes.ProbeContext;
import com.agentic.urlshortener.orchestration.agent.probes.ProbeRegistry;
import com.agentic.urlshortener.orchestration.agent.probes.SyntheticScope;
import com.agentic.urlshortener.orchestration.domain.FailureClass;
import com.agentic.urlshortener.orchestration.domain.StageType;
import com.agentic.urlshortener.orchestration.knowledge.CapabilityCatalog;
import com.agentic.urlshortener.orchestration.knowledge.CapabilityEntry;

import tools.jackson.databind.JsonNode;

/**
 * REGRESSION_TESTING for changes to existing behavior (FR-ORC-14, NFR-CHG-02): runs the baseline
 * probes the catalog lists for the capability against the live service, with the new capability
 * enabled for these calls only (preview). Every probe must pass; verification never degrades, so the
 * stage has no fallback. Synthetic links are removed in {@code finally}.
 */
@Component
public class RegressionTestingAgent implements StageAgent {

    private final ProbeRegistry probes;
    private final CapabilityCatalog catalog;

    public RegressionTestingAgent(ProbeRegistry probes, CapabilityCatalog catalog) {
        this.probes = probes;
        this.catalog = catalog;
    }

    @Override
    public StageType stageType() {
        return StageType.REGRESSION_TESTING;
    }

    @Override
    public String agentId() {
        return "regression-tester@1.0";
    }

    @Override
    public Set<AgentPermission> permissions() {
        return Set.of(AgentPermission.READ_LINKS, AgentPermission.WRITE_SYNTHETIC_LINKS, AgentPermission.PREVIEW_CAPABILITY);
    }

    @Override
    public StageResult execute(StageContext context) {
        JsonNode design = context.inputJson("DESIGN");
        String capability = design.path("releasePlan").path("capability").asString();
        List<String> probeIds = catalog.get(capability).map(CapabilityEntry::regressionProbes).orElse(List.of());
        List<AcceptanceProbe> regression = probes.regression(probeIds);
        if (regression.isEmpty()) {
            return new StageResult.Failed(FailureClass.PERMANENT, "no regression probes are defined for capability " + capability);
        }
        ProbeContext probeContext = new ProbeContext(context.runId(), context.port());
        int[] removed = new int[1];
        List<ProbeRuns.Result> results = SyntheticScope.exclusive(context.runId(), () -> {
            try {
                return context.port().withPreview(capability, ProbeRuns.releaseParameters(design), () -> {
                    List<ProbeRuns.Result> executed = new ArrayList<>();
                    for (AcceptanceProbe probe : regression) {
                        executed.add(ProbeRuns.run(probe, probeContext, List.of()));
                    }
                    return executed;
                });
            } finally {
                removed[0] = context.port().deleteSyntheticLinks(context.runId());
            }
        });
        List<String> failed = results.stream().filter(r -> !r.outcome().passed())
                .map(r -> r.probeId() + ": " + r.outcome().evidence()).toList();
        if (!failed.isEmpty()) {
            return new StageResult.Failed(FailureClass.PERMANENT, "regression detected with " + capability + " enabled: " + failed);
        }
        return new StageResult.Succeeded(List.of(ArtifactDraft.json("REGRESSION_REPORT",
                ProbeRuns.report("REGRESSION", results, List.of(), probeContext.created(), removed[0]))),
                results.size() + " regression probes passed with " + capability + " enabled");
    }
}
