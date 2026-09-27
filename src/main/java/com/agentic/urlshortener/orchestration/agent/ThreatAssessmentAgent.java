package com.agentic.urlshortener.orchestration.agent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.agentic.urlshortener.common.util.CanonicalJson;
import com.agentic.urlshortener.orchestration.domain.FailureClass;
import com.agentic.urlshortener.orchestration.domain.StageType;
import com.agentic.urlshortener.orchestration.knowledge.CapabilityCatalog;
import com.agentic.urlshortener.orchestration.knowledge.CapabilityEntry;

/**
 * THREAT_ASSESSMENT: STRIDE-classified threats of the matched capabilities with their mitigations and
 * the probes that verify them. Exit criterion: every HIGH threat names at least one verification probe.
 */
@Component
public class ThreatAssessmentAgent implements StageAgent {

    private final CapabilityCatalog catalog;

    public ThreatAssessmentAgent(CapabilityCatalog catalog) {
        this.catalog = catalog;
    }

    @Override
    public StageType stageType() {
        return StageType.THREAT_ASSESSMENT;
    }

    @Override
    public String agentId() {
        return "threat-assessor@1.0";
    }

    @Override
    public Set<AgentPermission> permissions() {
        return Set.of();
    }

    @Override
    public StageResult execute(StageContext context) {
        List<CapabilityEntry> capabilities = AgentInputs.scenarioCapabilities(context.inputJson("NORMALIZED_REQUIREMENT"), catalog);
        Map<String, Map<String, Object>> threats = new LinkedHashMap<>();
        for (CapabilityEntry capability : capabilities) {
            for (CapabilityEntry.Threat threat : capability.threats()) {
                threats.putIfAbsent(threat.id(), Map.of("id", threat.id(), "category", threat.category(),
                        "description", threat.description(), "severity", threat.severity(), "mitigation", threat.mitigation(),
                        "verifiedBy", threat.verifiedBy()));
            }
        }
        List<String> unverified = new ArrayList<>();
        threats.values().forEach(t -> {
            if ("HIGH".equals(t.get("severity")) && ((List<?>) t.get("verifiedBy")).isEmpty()) {
                unverified.add((String) t.get("id"));
            }
        });
        if (!unverified.isEmpty()) {
            return new StageResult.Failed(FailureClass.PERMANENT, "exit criterion not met: HIGH threats without a verification probe: " + unverified);
        }
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("capabilities", capabilities.stream().map(CapabilityEntry::id).toList());
        model.put("threats", List.copyOf(threats.values()));
        return new StageResult.Succeeded(List.of(ArtifactDraft.json("THREAT_MODEL", CanonicalJson.write(model))),
                threats.size() + " threats assessed");
    }
}
