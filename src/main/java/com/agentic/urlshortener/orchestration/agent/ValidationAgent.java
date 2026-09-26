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

import tools.jackson.databind.JsonNode;

/**
 * VALIDATION (synchronization of the verification branches, FR-ORC-05): every acceptance criterion
 * must be verified by a passing probe, every suite must be free of failures, and documentation must
 * be generated. Degraded stages (fallback outputs) are listed; they lower readiness later.
 */
@Component
public class ValidationAgent implements StageAgent {

    private static final List<String> REPORTS = List.of("TEST_REPORT", "REGRESSION_REPORT", "SECURITY_REPORT");

    @Override
    public StageType stageType() {
        return StageType.VALIDATION;
    }

    @Override
    public String agentId() {
        return "validator@1.0";
    }

    @Override
    public Set<AgentPermission> permissions() {
        return Set.of();
    }

    @Override
    public StageResult execute(StageContext context) {
        Map<String, String> criteria = AgentInputs.criteria(context.inputJson("NORMALIZED_REQUIREMENT"));
        JsonNode tests = context.inputJson("TEST_REPORT");

        List<Map<String, Object>> criteriaResults = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        for (String id : criteria.keySet()) {
            List<String> verifiedBy = new ArrayList<>();
            for (JsonNode result : tests.path("results")) {
                if (result.path("passed").asBoolean() && AgentInputs.strings(result.path("verifies")).contains(id)) {
                    verifiedBy.add(result.path("probeId").asString());
                }
            }
            criteriaResults.add(Map.of("id", id, "verifiedBy", verifiedBy, "passed", !verifiedBy.isEmpty()));
            if (verifiedBy.isEmpty()) {
                problems.add(id + " is not verified by a passing probe");
            }
        }

        List<Map<String, Object>> suites = new ArrayList<>();
        for (String type : REPORTS) {
            context.input(type).ifPresent(input -> {
                JsonNode report = input.json();
                int failed = report.path("failed").asInt();
                suites.add(Map.of("suite", report.path("suite").asString(), "passed", report.path("passed").asInt(), "failed", failed));
                if (failed > 0 || !report.path("unverifiedCriteria").isEmpty()) {
                    problems.add(report.path("suite").asString() + " suite: " + failed + " failed, unverified "
                            + AgentInputs.strings(report.path("unverifiedCriteria")));
                }
            });
        }

        String documentation = context.input("DOCUMENTATION").map(StageContext.ArtifactInput::content).orElse("");
        boolean generated = !documentation.isBlank();
        if (!generated) {
            problems.add("documentation was not generated");
        }
        List<String> degraded = new ArrayList<>();
        if (DocumentationAgent.isDegraded(documentation)) {
            degraded.add("DOCUMENTATION");
        }
        context.input("IMPACT_ANALYSIS").filter(i -> i.json().path("degraded").asBoolean()).ifPresent(i -> degraded.add("IMPACT_ANALYSIS"));

        if (!problems.isEmpty()) {
            return new StageResult.Failed(FailureClass.PERMANENT, "validation failed: " + problems);
        }
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("passed", true);
        report.put("criteria", criteriaResults);
        report.put("suites", suites);
        report.put("documentation", Map.of("generated", generated,
                "repositoryDocsUpdated", DocumentationAgent.repositoryDocsUpdated(documentation),
                "evidence", "documentation artifact present; repository check recorded by the documentation stage"));
        report.put("degradedStages", degraded);
        return new StageResult.Succeeded(List.of(ArtifactDraft.json("VALIDATION_REPORT", CanonicalJson.write(report))),
                criteria.size() + " criteria verified across " + suites.size() + " suites");
    }
}
