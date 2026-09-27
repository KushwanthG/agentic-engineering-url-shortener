package com.agentic.urlshortener.orchestration.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.agentic.urlshortener.common.util.CanonicalJson;
import com.agentic.urlshortener.orchestration.domain.FailureClass;
import com.agentic.urlshortener.support.AgentChain;
import com.agentic.urlshortener.support.ArtifactSchemas;
import com.agentic.urlshortener.support.RequirementFixtures;

import tools.jackson.databind.JsonNode;

/** T048: decomposition into dependency-aware tasks with every acceptance criterion mapped (FR-ORC-03). */
@Tag("FR-ORC-03")
@Tag("SCN-A")
class DecompositionAgentTest {

    private final DecompositionAgent agent = new DecompositionAgent(AgentChain.CATALOG);

    @Test
    void greenfieldDecompositionMapsEveryCriterionToATask() {
        AgentChain chain = AgentChain.analyzed(RequirementFixtures.gf001()).then(agent);
        String taskGraph = chain.artifact("TASK_GRAPH");
        ArtifactSchemas.assertValid("task-graph", taskGraph);
        JsonNode graph = CanonicalJson.parse(taskGraph);

        assertThat(graph.path("capabilities").get(0).asString()).isEqualTo("custom-alias");
        assertThat(graph.path("tasks").size()).isEqualTo(7);

        Set<String> covered = new HashSet<>();
        Map<String, List<String>> dependsOn = new HashMap<>();
        for (JsonNode task : graph.path("tasks")) {
            task.path("acceptanceCriteria").forEach(ac -> covered.add(ac.asString()));
            List<String> deps = new ArrayList<>();
            task.path("dependsOn").forEach(d -> deps.add(d.asString()));
            dependsOn.put(task.path("id").asString(), deps);
        }
        assertThat(covered).containsExactlyInAnyOrder("AC-1", "AC-2", "AC-3", "AC-4", "AC-5", "AC-6");
        assertThat(dependsOn.get("WT-04")).containsExactlyInAnyOrder("WT-02", "WT-03");
        assertThat(graph.path("criticalPath").get(graph.path("criticalPath").size() - 1).asString()).isEqualTo("WT-07");
    }

    @Test
    void failsPermanentlyWhenThereIsNothingToDecompose() {
        AgentChain chain = AgentChain.analyzed(RequirementFixtures.document("OUT-1", "Payroll export", "NEW_CAPABILITY",
                "Integrate with the payroll system.", List.of("Given a month has ended, when the export runs, then an invoice is sent."),
                List.of()));
        StageResult result = chain.run(agent);
        assertThat(result).isInstanceOfSatisfying(StageResult.Failed.class,
                f -> assertThat(f.failureClass()).isEqualTo(FailureClass.PERMANENT));
    }
}
