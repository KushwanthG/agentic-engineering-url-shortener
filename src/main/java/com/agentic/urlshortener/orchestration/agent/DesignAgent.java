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

import tools.jackson.databind.JsonNode;

/**
 * DESIGN (join of the analysis branches): components, API and schema deltas from the catalog, the
 * release plan with its parameters, the rollback plan, and materiality. A change to the public API,
 * the stored data structure, or a security control is always material (FR-RPL-03), which opens the
 * architecture approval gate.
 */
@Component
public class DesignAgent implements StageAgent {

    private final CapabilityCatalog catalog;

    public DesignAgent(CapabilityCatalog catalog) {
        this.catalog = catalog;
    }

    @Override
    public StageType stageType() {
        return StageType.DESIGN;
    }

    @Override
    public String agentId() {
        return "designer@1.0";
    }

    @Override
    public Set<AgentPermission> permissions() {
        return Set.of();
    }

    @Override
    public StageResult execute(StageContext context) {
        JsonNode normalized = context.inputJson("NORMALIZED_REQUIREMENT");
        List<CapabilityEntry> capabilities = AgentInputs.scenarioCapabilities(normalized, catalog);
        if (capabilities.isEmpty()) {
            return new StageResult.Failed(FailureClass.PERMANENT, "no deliverable capability to design");
        }
        CapabilityEntry primary = capabilities.getFirst();

        List<Map<String, Object>> components = new ArrayList<>();
        List<Map<String, Object>> apiChanges = new ArrayList<>();
        List<Map<String, Object>> schemaChanges = new ArrayList<>();
        List<String> reasons = new ArrayList<>();
        List<String> risks = new ArrayList<>();
        boolean securityControl = false;
        for (CapabilityEntry capability : capabilities) {
            capability.components().forEach(c -> components.add(Map.of("name", c.name(), "responsibility", c.responsibility(),
                    "change", c.change())));
            if (capability.api() != null) {
                for (CapabilityEntry.ApiChange change : capability.api().changes()) {
                    apiChanges.add(Map.of("operation", change.operation(), "change", change.change(),
                            "compatibility", change.compatibility(), "contractVersion", capability.api().contractVersion()));
                    reasons.add("public API change (contract " + capability.api().contractVersion() + "): " + change.operation());
                }
            }
            if (capability.schema() != null) {
                for (CapabilityEntry.SchemaChange change : capability.schema().changes()) {
                    schemaChanges.add(Map.of("migration", change.migration(), "change", change.change(),
                            "compatibility", change.compatibility()));
                    reasons.add("schema change (stored data structure): " + change.migration());
                }
            }
            if (capability.securityControlChange()) {
                securityControl = true;
                reasons.add("security control change: " + capability.id());
            }
            capability.threats().stream().filter(t -> !"LOW".equals(t.severity()))
                    .forEach(t -> risks.add(t.id() + " (" + t.severity() + "): " + t.description() + "; mitigation: " + t.mitigation()));
            capability.impactedRequirements().forEach(r -> risks.add("changes existing behavior covered by " + r));
        }
        context.input("IMPACT_ANALYSIS").ifPresent(impact -> impact.json().path("regressionRisks").forEach(r -> risks.add(
                "regression risk " + r.path("id").asString() + " (" + r.path("severity").asString() + "): " + r.path("mitigation").asString())));

        List<String> decisions = new ArrayList<>();
        AgentInputs.strings(normalized.path("constraints")).forEach(c -> decisions.add("Constraint honored: " + c));
        decisions.add("Delivered unreleased behind the '" + primary.id() + "' capability flag; released only after release approval (ADR-018)");

        Map<String, Object> design = new LinkedHashMap<>();
        design.put("capabilities", capabilities.stream().map(CapabilityEntry::id).toList());
        design.put("components", components);
        design.put("apiChanges", apiChanges);
        design.put("schemaChanges", schemaChanges);
        design.put("releasePlan", Map.of("capability", primary.id(), "strategy", primary.release().strategy(),
                "parameters", releaseParameters(primary, normalized)));
        design.put("rollbackPlan", primary.rollback());
        design.put("materialChange", !apiChanges.isEmpty() || !schemaChanges.isEmpty() || securityControl);
        design.put("materialReasons", reasons);
        design.put("decisions", decisions);
        design.put("risks", risks);
        return new StageResult.Succeeded(List.of(ArtifactDraft.json("DESIGN", CanonicalJson.write(design))),
                "design for " + design.get("capabilities") + "; material: " + design.get("materialChange"));
    }

    /** Catalog defaults overridden by parameters decided for this requirement (for example by clarification). */
    private static Map<String, Object> releaseParameters(CapabilityEntry capability, JsonNode normalized) {
        Map<String, Object> parameters = new LinkedHashMap<>(capability.release().parameters());
        JsonNode decided = normalized.path("parameters");
        for (String key : capability.release().parameters().keySet()) {
            if (decided.has(key)) {
                parameters.put(key, CanonicalJson.read(CanonicalJson.write(decided.get(key)), Object.class));
            }
        }
        return parameters;
    }
}
