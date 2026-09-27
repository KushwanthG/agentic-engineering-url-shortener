package com.agentic.urlshortener.orchestration.agent;

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
 * Fallback of IMPACT_ANALYSIS (FR-REL-03): the impact as declared by the capability catalog, without
 * a source scan, so no derived components or impacted tests. Marked {@code degraded}; validation lists
 * it and policy CHG-002 requires a non-degraded analysis, so the release needs an explicit exception.
 */
@Component
public class CatalogImpactAnalysisAgent implements StageAgent {

    private final CapabilityCatalog catalog;

    public CatalogImpactAnalysisAgent(CapabilityCatalog catalog) {
        this.catalog = catalog;
    }

    @Override
    public StageType stageType() {
        return StageType.IMPACT_ANALYSIS;
    }

    @Override
    public String agentId() {
        return "catalog-impact-analyst@1.0";
    }

    @Override
    public boolean fallback() {
        return true;
    }

    @Override
    public Set<AgentPermission> permissions() {
        return Set.of();
    }

    @Override
    public StageResult execute(StageContext context) {
        List<CapabilityEntry> capabilities = AgentInputs.scenarioCapabilities(context.inputJson("NORMALIZED_REQUIREMENT"), catalog);
        if (capabilities.isEmpty()) {
            return new StageResult.Failed(FailureClass.PERMANENT, "the requirement names no catalog capability to analyze");
        }
        Map<String, Object> report = ImpactReports.skeleton("CATALOG_ONLY", true, capabilities);
        report.put("components", capabilities.stream().flatMap(c -> c.components().stream()).map(component -> {
            Map<String, Object> entry = ImpactReports.entry("name", component.name(), "path", component.path());
            entry.put("reason", "catalog component (source scan unavailable): " + component.responsibility());
            return entry;
        }).toList());
        report.put("interfaces", ImpactReports.interfaces(capabilities));
        report.put("dataFlows", capabilities.stream().map(c -> c.id() + ": " + String.join(" → ",
                c.components().stream().map(CapabilityEntry.Component::name).toList())).toList());
        report.put("tests", List.of());
        report.put("documentation", capabilities.stream().flatMap(c -> c.docAnchors().stream())
                .map(a -> ImpactReports.entry("path", a.path(), "reason", "catalog documentation anchor for '" + a.mentions() + "'"))
                .toList());
        ImpactReports.finish(report, capabilities);
        return new StageResult.Succeeded(List.of(ArtifactDraft.json("IMPACT_ANALYSIS", CanonicalJson.write(report))),
                "catalog-only impact analysis (degraded)");
    }
}
