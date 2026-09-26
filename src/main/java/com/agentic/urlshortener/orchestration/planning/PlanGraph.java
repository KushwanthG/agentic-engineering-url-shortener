package com.agentic.urlshortener.orchestration.planning;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import com.agentic.urlshortener.common.util.CanonicalJson;
import com.agentic.urlshortener.orchestration.domain.StageType;

/** An explicit dependency graph of stages (the plan); stages are kept in template order. */
public record PlanGraph(List<StageSpec> stages) {

    public PlanGraph {
        stages = List.copyOf(stages);
    }

    public List<StageType> keys() {
        return stages.stream().map(StageSpec::key).toList();
    }

    public boolean contains(StageType key) {
        return stages.stream().anyMatch(s -> s.key() == key);
    }

    public StageSpec stage(StageType key) {
        return stages.stream().filter(s -> s.key() == key).findFirst()
                .orElseThrow(() -> new NoSuchElementException("no stage " + key + " in plan"));
    }

    /** Stages that depend directly on {@code key}. */
    public List<StageType> dependents(StageType key) {
        return stages.stream().filter(s -> s.dependsOn().contains(key)).map(StageSpec::key).toList();
    }

    /** JSON stored in {@code plan_version.graph} and returned by the plan-versions API. */
    public String toJson() {
        return CanonicalJson.write(Map.of("stages", stages.stream().map(s -> {
            LinkedHashMap<String, Object> node = new LinkedHashMap<>();
            node.put("key", s.key().name());
            node.put("dependsOn", s.dependsOn().stream().map(Enum::name).toList());
            node.put("gate", s.gate());
            if (s.condition() != null) {
                node.put("condition", s.condition());
            }
            return node;
        }).toList()));
    }
}
