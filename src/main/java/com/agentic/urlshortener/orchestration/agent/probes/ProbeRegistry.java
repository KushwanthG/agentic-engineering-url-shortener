package com.agentic.urlshortener.orchestration.agent.probes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

/** All probes by id; acceptance probes are grouped by capability. */
@Component
public class ProbeRegistry {

    /** Post-release smoke probe per capability: create through the released capability, then resolve. */
    private static final Map<String, String> SMOKE = Map.of("custom-alias", "CA-P6");

    private final List<AcceptanceProbe> acceptance;
    private final List<AcceptanceProbe> all;

    public ProbeRegistry() {
        this.acceptance = List.copyOf(CustomAliasProbes.all());
        List<AcceptanceProbe> everything = new ArrayList<>(acceptance);
        everything.addAll(SecurityProbes.all());
        everything.addAll(RegressionProbes.all());
        this.all = List.copyOf(everything);
    }

    public List<AcceptanceProbe> acceptance(String capabilityId) {
        return acceptance.stream().filter(p -> capabilityId.equals(p.capabilityId())).toList();
    }

    /** The regression probes with the given ids, in the given order (unknown ids are skipped). */
    public List<AcceptanceProbe> regression(List<String> probeIds) {
        return probeIds.stream().map(this::byId).flatMap(Optional::stream)
                .filter(p -> RegressionProbes.BASELINE.equals(p.capabilityId())).toList();
    }

    /** The probe that verifies a capability right after release, without preview. */
    public Optional<AcceptanceProbe> smoke(String capabilityId) {
        return byId(SMOKE.getOrDefault(capabilityId, ""));
    }

    public Optional<AcceptanceProbe> byId(String id) {
        return all.stream().filter(p -> p.id().equals(id)).findFirst();
    }
}
