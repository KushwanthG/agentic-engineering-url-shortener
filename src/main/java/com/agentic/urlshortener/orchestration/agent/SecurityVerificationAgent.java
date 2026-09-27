package com.agentic.urlshortener.orchestration.agent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.agentic.urlshortener.orchestration.agent.probes.AcceptanceProbe;
import com.agentic.urlshortener.orchestration.agent.probes.ProbeContext;
import com.agentic.urlshortener.orchestration.agent.probes.ProbeOutcome;
import com.agentic.urlshortener.orchestration.agent.probes.ProbeRegistry;
import com.agentic.urlshortener.orchestration.agent.probes.SyntheticScope;
import com.agentic.urlshortener.orchestration.domain.FailureClass;
import com.agentic.urlshortener.orchestration.domain.StageType;

import tools.jackson.databind.JsonNode;

/**
 * SECURITY_VERIFICATION: runs the probes named by the threat model (always including the malicious
 * URL catalog) in preview mode and checks that every HIGH threat is verified by a passing probe.
 * Synthetic links are removed in {@code finally}.
 */
@Component
public class SecurityVerificationAgent implements StageAgent {

    private static final String URL_CATALOG = "SEC-URL-CATALOG";

    private final ProbeRegistry probes;

    public SecurityVerificationAgent(ProbeRegistry probes) {
        this.probes = probes;
    }

    @Override
    public StageType stageType() {
        return StageType.SECURITY_VERIFICATION;
    }

    @Override
    public String agentId() {
        return "security-verifier@1.0";
    }

    @Override
    public Set<AgentPermission> permissions() {
        return Set.of(AgentPermission.READ_LINKS, AgentPermission.WRITE_SYNTHETIC_LINKS, AgentPermission.PREVIEW_CAPABILITY);
    }

    @Override
    public StageResult execute(StageContext context) {
        JsonNode design = context.inputJson("DESIGN");
        String capability = design.path("releasePlan").path("capability").asString();
        Map<String, List<String>> threatsByProbe = new LinkedHashMap<>();
        threatsByProbe.put(URL_CATALOG, new ArrayList<>());
        Set<String> highThreats = new LinkedHashSet<>();
        for (JsonNode threat : context.inputJson("THREAT_MODEL").path("threats")) {
            String threatId = threat.path("id").asString();
            if ("HIGH".equals(threat.path("severity").asString())) {
                highThreats.add(threatId);
            }
            threat.path("verifiedBy").forEach(p -> threatsByProbe.computeIfAbsent(p.asString(), k -> new ArrayList<>()).add(threatId));
        }

        ProbeContext probeContext = new ProbeContext(context.runId(), context.port());
        int[] removed = new int[1];
        List<ProbeRuns.Result> results = SyntheticScope.exclusive(context.runId(), () -> {
            try {
                return context.port().withPreview(capability, ProbeRuns.releaseParameters(design), () -> {
                    List<ProbeRuns.Result> executed = new ArrayList<>();
                    threatsByProbe.forEach((probeId, threats) -> {
                        Optional<AcceptanceProbe> probe = probes.byId(probeId);
                        executed.add(probe.isPresent() ? ProbeRuns.run(probe.get(), probeContext, threats)
                                : new ProbeRuns.Result(probeId, "unregistered probe", threats,
                                        ProbeOutcome.fail("no probe " + probeId + " is registered"), 0));
                    });
                    return executed;
                });
            } finally {
                removed[0] = context.port().deleteSyntheticLinks(context.runId());
            }
        });

        List<String> unverified = highThreats.stream()
                .filter(t -> results.stream().noneMatch(r -> r.outcome().passed() && r.verifies().contains(t))).toList();
        List<String> failedProbes = results.stream().filter(r -> !r.outcome().passed())
                .map(r -> r.probeId() + ": " + r.outcome().evidence()).toList();
        if (!unverified.isEmpty() || !failedProbes.isEmpty()) {
            return new StageResult.Failed(FailureClass.PERMANENT, "security verification failed: unverified HIGH threats "
                    + unverified + "; failed probes " + failedProbes);
        }
        return new StageResult.Succeeded(List.of(ArtifactDraft.json("SECURITY_REPORT",
                ProbeRuns.report("SECURITY", results, unverified, probeContext.created(), removed[0]))),
                results.size() + " security probes passed; " + highThreats.size() + " HIGH threats verified");
    }
}
