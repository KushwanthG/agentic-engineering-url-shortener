package com.agentic.urlshortener.orchestration.agent;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.agentic.urlshortener.common.util.CanonicalJson;
import com.agentic.urlshortener.orchestration.config.OrchestrationProperties;
import com.agentic.urlshortener.orchestration.domain.FailureClass;
import com.agentic.urlshortener.orchestration.domain.StageType;
import com.agentic.urlshortener.orchestration.knowledge.CapabilityCatalog;
import com.agentic.urlshortener.orchestration.knowledge.CapabilityEntry;
import com.agentic.urlshortener.orchestration.knowledge.CodebaseScanner;

/**
 * IMPACT_ANALYSIS for changes to existing behavior (FR-ORC-14): scans the live source tree
 * ({@code app.orchestration.codebase-root}). The catalog's impact seeds are expanded through the
 * reverse-dependency closure of the import graph; every component states whether it is a catalog seed
 * or derived, with its dependency chain. Tests referencing impacted classes and documents mentioning
 * the changed endpoints are listed; data flows follow the closure from each controller down to the
 * seed. Without a source tree the stage fails, and the catalog-only fallback runs (degraded).
 */
@Component
public class ImpactAnalysisAgent implements StageAgent {

    private final CapabilityCatalog catalog;
    private final CodebaseScanner scanner;
    private final Path root;

    public ImpactAnalysisAgent(CapabilityCatalog catalog, OrchestrationProperties properties) {
        this.catalog = catalog;
        this.root = Path.of(properties.codebaseRoot()).toAbsolutePath().normalize();
        this.scanner = new CodebaseScanner(root);
    }

    @Override
    public StageType stageType() {
        return StageType.IMPACT_ANALYSIS;
    }

    @Override
    public String agentId() {
        return "impact-analyst@1.0";
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
        if (!scanner.available()) {
            return new StageResult.Failed(FailureClass.PERMANENT, "no source tree under " + root + " (src/main/java); "
                    + "the catalog-only analysis is the fallback");
        }
        CodebaseScanner.Index index = scanner.scan();
        Map<String, Object> report = ImpactReports.skeleton("SOURCE_SCAN", false, capabilities);
        Set<String> seeds = capabilities.stream().flatMap(c -> c.impactSeeds().stream()).collect(Collectors.toSet());
        Map<String, String> responsibilities = new LinkedHashMap<>();
        capabilities.forEach(c -> c.components().forEach(component -> responsibilities.putIfAbsent(component.name(),
                component.responsibility())));

        Map<String, List<String>> closure = index.reverseClosure(seeds);
        List<Map<String, Object>> components = new ArrayList<>();
        closure.forEach((name, chain) -> {
            String path = index.classes().get(name).path();
            String reason = chain.size() == 1
                    ? "catalog seed: " + responsibilities.getOrDefault(name, "named by the capability catalog")
                    : "derived through the reverse-dependency closure: " + String.join(" → ", chain);
            Map<String, Object> component = new LinkedHashMap<>();
            component.put("name", name);
            component.put("path", path);
            component.put("reason", reason);
            components.add(component);
        });
        seeds.stream().filter(s -> !closure.containsKey(s)).sorted().forEach(missing -> {
            Map<String, Object> component = new LinkedHashMap<>();
            component.put("name", missing);
            component.put("path", null);
            component.put("reason", "catalog seed not found in the source tree (new component)");
            components.add(component);
        });
        report.put("components", components);
        report.put("interfaces", ImpactReports.interfaces(capabilities));

        List<String> columns = capabilities.stream().filter(c -> c.schema() != null).flatMap(c -> c.schema().columns().stream()).toList();
        List<String> flows = new ArrayList<>();
        closure.forEach((name, chain) -> {
            if (name.endsWith("Controller")) {
                flows.add(String.join(" → ", chain) + (columns.isEmpty() ? "" : " → short_link " + columns));
            }
        });
        report.put("dataFlows", flows);

        report.put("tests", index.testsReferencing(closure.keySet()).stream()
                .map(path -> ImpactReports.entry("name", path.substring(path.lastIndexOf('/') + 1).replace(".java", ""), "path", path))
                .toList());
        List<String> terms = ImpactReports.documentationTerms(capabilities);
        report.put("documentation", index.docsMentioning(terms).stream()
                .map(path -> ImpactReports.entry("path", path, "reason", "mentions one of " + terms)).toList());
        ImpactReports.finish(report, capabilities);
        return new StageResult.Succeeded(List.of(ArtifactDraft.json("IMPACT_ANALYSIS", CanonicalJson.write(report))),
                components.size() + " impacted components (" + seeds.size() + " seeds), " + flows.size() + " data flows");
    }
}
