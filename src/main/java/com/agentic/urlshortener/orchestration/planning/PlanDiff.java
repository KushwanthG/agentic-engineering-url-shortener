package com.agentic.urlshortener.orchestration.planning;

import java.util.ArrayList;
import java.util.List;

import com.agentic.urlshortener.common.util.CanonicalJson;
import com.agentic.urlshortener.orchestration.domain.StageType;

/** Difference between two plan versions (FR-RPL-02): added, removed, invalidated stages, changed dependencies. */
public record PlanDiff(List<String> added, List<String> removed, List<String> invalidated, List<String> dependencyChanges) {

    public PlanDiff {
        added = List.copyOf(added);
        removed = List.copyOf(removed);
        invalidated = List.copyOf(invalidated);
        dependencyChanges = List.copyOf(dependencyChanges);
    }

    public static PlanDiff between(PlanGraph before, PlanGraph after) {
        List<String> added = new ArrayList<>();
        List<String> removed = new ArrayList<>();
        List<String> changes = new ArrayList<>();
        for (StageType key : after.keys()) {
            if (!before.contains(key)) {
                added.add(key.name());
            } else if (!before.stage(key).dependsOn().equals(after.stage(key).dependsOn())) {
                changes.add(key.name() + ": " + before.stage(key).dependsOn() + " -> " + after.stage(key).dependsOn());
            }
        }
        for (StageType key : before.keys()) {
            if (!after.contains(key)) {
                removed.add(key.name());
            }
        }
        return new PlanDiff(added, removed, List.of(), changes);
    }

    public PlanDiff withInvalidated(List<String> invalidatedStages) {
        return new PlanDiff(added, removed, invalidatedStages, dependencyChanges);
    }

    public String toJson() {
        return CanonicalJson.write(this);
    }
}
