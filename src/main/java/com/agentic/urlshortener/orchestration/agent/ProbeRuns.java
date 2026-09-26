package com.agentic.urlshortener.orchestration.agent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.agentic.urlshortener.common.util.CanonicalJson;
import com.agentic.urlshortener.orchestration.agent.probes.AcceptanceProbe;
import com.agentic.urlshortener.orchestration.agent.probes.ProbeContext;
import com.agentic.urlshortener.orchestration.agent.probes.ProbeOutcome;

import tools.jackson.databind.JsonNode;

/** Runs probes and builds {@code verification-report} artifacts (acceptance, regression, security). */
final class ProbeRuns {

    /** One executed probe and the criteria or threats it verifies. */
    record Result(String probeId, String description, List<String> verifies, ProbeOutcome outcome, long durationMillis) {
    }

    private ProbeRuns() {
    }

    static Result run(AcceptanceProbe probe, ProbeContext context, List<String> verifies) {
        long start = System.nanoTime();
        ProbeOutcome outcome = probe.run(context);
        return new Result(probe.id(), probe.description(), verifies, outcome, (System.nanoTime() - start) / 1_000_000);
    }

    static String report(String suite, List<Result> results, List<String> unverified, int created, int removed) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("suite", suite);
        report.put("results", results.stream().map(r -> {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("probeId", r.probeId());
            result.put("description", r.description());
            result.put("verifies", r.verifies());
            result.put("passed", r.outcome().passed());
            result.put("evidence", r.outcome().evidence());
            result.put("durationMillis", r.durationMillis());
            return result;
        }).toList());
        report.put("unverifiedCriteria", unverified);
        report.put("passed", results.stream().filter(r -> r.outcome().passed()).count());
        report.put("failed", results.stream().filter(r -> !r.outcome().passed()).count());
        report.put("syntheticRecordsCreated", created);
        report.put("syntheticRecordsRemoved", removed);
        return CanonicalJson.write(report);
    }

    /** Capability and release parameters of the design, used for the preview. */
    @SuppressWarnings("unchecked")
    static Map<String, Object> releaseParameters(JsonNode design) {
        return CanonicalJson.read(CanonicalJson.write(design.path("releasePlan").path("parameters")), Map.class);
    }
}
