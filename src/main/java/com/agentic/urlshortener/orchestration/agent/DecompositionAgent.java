package com.agentic.urlshortener.orchestration.agent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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
 * DECOMPOSITION: turns the matched capabilities' task templates into a dependency-aware task graph.
 * Tasks are mapped to acceptance criteria by the phrases each template declares ({@code "*"} = every
 * criterion). Exit criteria: every criterion is mapped to at least one task and the graph is acyclic.
 */
@Component
public class DecompositionAgent implements StageAgent {

    private final CapabilityCatalog catalog;

    public DecompositionAgent(CapabilityCatalog catalog) {
        this.catalog = catalog;
    }

    @Override
    public StageType stageType() {
        return StageType.DECOMPOSITION;
    }

    @Override
    public String agentId() {
        return "decomposer@1.0";
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
            return new StageResult.Failed(FailureClass.PERMANENT, "no deliverable catalog capability matched; nothing to decompose");
        }
        Map<String, String> criteria = AgentInputs.criteria(normalized);

        List<Map<String, Object>> tasks = new ArrayList<>();
        Map<String, List<String>> edges = new LinkedHashMap<>();
        Set<String> covered = new LinkedHashSet<>();
        int next = 1;
        for (CapabilityEntry capability : capabilities) {
            Map<String, String> renumbered = new HashMap<>();
            for (CapabilityEntry.Task template : capability.tasks()) {
                renumbered.put(template.id(), String.format("WT-%02d", next++));
            }
            for (CapabilityEntry.Task template : capability.tasks()) {
                String id = renumbered.get(template.id());
                List<String> dependsOn = template.dependsOn().stream().map(renumbered::get).toList();
                List<String> mapped = mapCriteria(template.criteria(), criteria);
                covered.addAll(mapped);
                edges.put(id, dependsOn);
                Map<String, Object> task = new LinkedHashMap<>();
                task.put("id", id);
                task.put("title", template.title());
                task.put("kind", template.kind());
                task.put("capability", capability.id());
                task.put("component", template.component());
                task.put("acceptanceCriteria", mapped);
                task.put("dependsOn", dependsOn);
                task.put("parallelizable", template.parallelizable());
                tasks.add(task);
            }
        }
        List<String> unmapped = criteria.keySet().stream().filter(id -> !covered.contains(id)).toList();
        if (!unmapped.isEmpty()) {
            return new StageResult.Failed(FailureClass.PERMANENT, "exit criterion not met: acceptance criteria not mapped to any task: " + unmapped);
        }
        List<String> criticalPath = criticalPath(edges);
        if (criticalPath == null) {
            return new StageResult.Failed(FailureClass.PERMANENT, "exit criterion not met: the task graph has a cycle");
        }
        Map<String, Object> graph = new LinkedHashMap<>();
        graph.put("capabilities", capabilities.stream().map(CapabilityEntry::id).toList());
        graph.put("tasks", tasks);
        graph.put("criticalPath", criticalPath);
        return new StageResult.Succeeded(List.of(ArtifactDraft.json("TASK_GRAPH", CanonicalJson.write(graph))),
                tasks.size() + " tasks; every acceptance criterion mapped");
    }

    private static List<String> mapCriteria(List<String> phrases, Map<String, String> criteria) {
        if (phrases.contains("*")) {
            return List.copyOf(criteria.keySet());
        }
        List<String> mapped = new ArrayList<>();
        criteria.forEach((id, text) -> {
            String lower = text.toLowerCase(Locale.ROOT);
            if (phrases.stream().anyMatch(p -> lower.contains(p.toLowerCase(Locale.ROOT)))) {
                mapped.add(id);
            }
        });
        return mapped;
    }

    /** Longest dependency chain (by task count), or null when the graph has a cycle. */
    static List<String> criticalPath(Map<String, List<String>> edges) {
        Map<String, List<String>> longest = new HashMap<>();
        Set<String> visiting = new LinkedHashSet<>();
        List<String> best = List.of();
        for (String task : edges.keySet()) {
            List<String> path = longestTo(task, edges, longest, visiting);
            if (path == null) {
                return null;
            }
            if (path.size() > best.size()) {
                best = path;
            }
        }
        return best;
    }

    private static List<String> longestTo(String task, Map<String, List<String>> edges, Map<String, List<String>> memo, Set<String> visiting) {
        if (memo.containsKey(task)) {
            return memo.get(task);
        }
        if (!visiting.add(task)) {
            return null;
        }
        List<String> best = List.of();
        for (String dependency : edges.getOrDefault(task, List.of())) {
            List<String> path = longestTo(dependency, edges, memo, visiting);
            if (path == null) {
                return null;
            }
            if (path.size() > best.size()) {
                best = path;
            }
        }
        visiting.remove(task);
        List<String> result = new ArrayList<>(best);
        result.add(task);
        memo.put(task, result);
        return result;
    }
}
