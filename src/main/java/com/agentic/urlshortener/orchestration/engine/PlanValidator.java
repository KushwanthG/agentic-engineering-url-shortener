package com.agentic.urlshortener.orchestration.engine;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.agentic.urlshortener.orchestration.domain.StageType;
import com.agentic.urlshortener.orchestration.planning.PlanGraph;
import com.agentic.urlshortener.orchestration.planning.StageSpec;

/**
 * Checks a plan before any execution (plan.md §3): no duplicate stages, every dependency resolvable,
 * exactly one root ({@code REQUIREMENT_INGESTION}), acyclic (Kahn's algorithm), {@code RELEASE}
 * reachable only through {@code RELEASE_APPROVAL}, and every side-effecting stage downstream of a gate.
 */
@Component
public class PlanValidator {

    public void validate(PlanGraph plan) {
        List<String> violations = new ArrayList<>();
        Set<StageType> keys = EnumSet.noneOf(StageType.class);
        for (StageSpec stage : plan.stages()) {
            if (!keys.add(stage.key())) {
                violations.add("duplicate stage " + stage.key());
            }
        }
        for (StageSpec stage : plan.stages()) {
            for (StageType dependency : stage.dependsOn()) {
                if (!keys.contains(dependency)) {
                    violations.add("unknown dependency " + dependency + " of " + stage.key());
                }
            }
        }
        List<StageType> roots = plan.stages().stream().filter(s -> s.dependsOn().isEmpty()).map(StageSpec::key).distinct().toList();
        if (!roots.equals(List.of(StageType.REQUIREMENT_INGESTION))) {
            violations.add("the plan must have exactly one root REQUIREMENT_INGESTION, found " + roots);
        }
        if (violations.isEmpty()) {
            Set<StageType> cyclic = cyclicStages(plan);
            if (!cyclic.isEmpty()) {
                violations.add("cycle among " + cyclic);
            } else {
                checkGateInvariants(plan, violations);
            }
        }
        if (!violations.isEmpty()) {
            throw new InvalidPlanException(violations);
        }
    }

    /** Kahn's algorithm; returns the stages left over, which lie on or behind a cycle. */
    private static Set<StageType> cyclicStages(PlanGraph plan) {
        Map<StageType, Integer> inDegree = new EnumMap<>(StageType.class);
        plan.stages().forEach(s -> inDegree.put(s.key(), s.dependsOn().size()));
        Deque<StageType> ready = new ArrayDeque<>();
        inDegree.forEach((key, degree) -> {
            if (degree == 0) {
                ready.add(key);
            }
        });
        Set<StageType> visited = new HashSet<>();
        while (!ready.isEmpty()) {
            StageType key = ready.poll();
            visited.add(key);
            for (StageType dependent : plan.dependents(key)) {
                if (inDegree.merge(dependent, -1, Integer::sum) == 0) {
                    ready.add(dependent);
                }
            }
        }
        Set<StageType> leftover = EnumSet.noneOf(StageType.class);
        plan.keys().stream().filter(k -> !visited.contains(k)).forEach(leftover::add);
        return leftover;
    }

    private static void checkGateInvariants(PlanGraph plan, List<String> violations) {
        for (StageSpec stage : plan.stages()) {
            Set<StageType> ancestors = ancestors(plan, stage.key());
            if (stage.key() == StageType.RELEASE && !ancestors.contains(StageType.RELEASE_APPROVAL)) {
                violations.add("RELEASE must be downstream of RELEASE_APPROVAL");
            }
            if (stage.key().hasSideEffects() && ancestors.stream().noneMatch(StageType::isGate)) {
                violations.add(stage.key() + " has side effects but is not downstream of a gate");
            }
        }
    }

    private static Set<StageType> ancestors(PlanGraph plan, StageType key) {
        Set<StageType> result = EnumSet.noneOf(StageType.class);
        Deque<StageType> todo = new ArrayDeque<>(plan.stage(key).dependsOn());
        while (!todo.isEmpty()) {
            StageType next = todo.poll();
            if (result.add(next)) {
                todo.addAll(plan.stage(next).dependsOn());
            }
        }
        return result;
    }
}
