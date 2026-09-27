package com.agentic.urlshortener.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

import com.agentic.urlshortener.common.util.CanonicalJson;
import com.agentic.urlshortener.orchestration.audit.AuditService;
import com.agentic.urlshortener.orchestration.audit.AuditVerification;
import com.agentic.urlshortener.orchestration.domain.AuditEvent;
import com.agentic.urlshortener.support.ArtifactSchemas;
import com.agentic.urlshortener.support.HttpDriver;
import com.agentic.urlshortener.support.GovernanceInvariants;
import com.agentic.urlshortener.support.EvidenceExporter;
import com.agentic.urlshortener.support.Tokens;

import tools.jackson.databind.JsonNode;

/**
 * T057 — SCN-A greenfield end to end over HTTP with the real agents: GF-001 (custom aliases) goes
 * from submission to a released capability. Gate decisions by {@code bob} and {@code carol} are
 * <strong>simulated human input</strong> supplied by this test as labeled demo principals; they are
 * not decisions of the candidate. Exports E-A1..E-A9 to {@code target/evidence/scn-a/}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Tag("SCN-A")
@Tag("FR-ORC-04")
@Tag("FR-ORC-05")
@Tag("FR-ORC-13")
@Tag("FR-CAP-02")
@Tag("FR-POL-01")
@Tag("SC-002")
class ScenarioAGreenfieldE2ETest {

    private static final String SIMULATED = " (simulated human input: automated test acting as a labeled demo principal)";
    private static final Set<String> FORK = Set.of("IMPLEMENTATION", "DOCUMENTATION");
    private static final Set<String> VERIFY = Set.of("TESTING", "SECURITY_VERIFICATION");

    @Autowired private Environment environment;
    @Autowired private AuditService audit;

    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    @Test
    void customAliasesGoFromRequirementToReleasedCapability() throws Exception {
        String input = scenarioInput();

        // Submission (requester alice)
        HttpResponse<String> submitted = send("POST", "/api/v1/workflows", Tokens.REQUESTER, input);
        assertThat(submitted.statusCode()).isEqualTo(201);
        assertThat(submitted.headers().firstValue("Location")).isPresent();
        String runId = json(submitted).path("runId").asString();
        String runPath = "/api/v1/workflows/" + runId;

        // Architecture approval (simulated: bob, APPROVER)
        awaitPendingGate(runPath, "ARCHITECTURE_APPROVAL");
        decide(runPath, "ARCHITECTURE_APPROVAL", Tokens.APPROVER, "Design, threat model and impact reviewed" + SIMULATED);

        // Release approval (simulated: carol, RELEASE_OWNER)
        awaitPendingGate(runPath, "RELEASE_APPROVAL");
        decide(runPath, "RELEASE_APPROVAL", Tokens.RELEASE_OWNER, "Readiness, validation and compliance reviewed" + SIMULATED);

        JsonNode run = awaitTerminal(runPath);
        GovernanceInvariants.assertHold(new HttpDriver(port()), runPath);
        assertThat(ArtifactSchemas.assertRunArtifactsValid(new HttpDriver(port()), runPath)).isGreaterThan(10);
        assertThat(run.path("status").asString()).as("terminal reason: %s", run.path("terminalReason")).isEqualTo("COMPLETED");
        assertThat(run.path("readiness").asString()).isEqualTo("READY");
        assertThat(run.path("policySetVersion").asString()).isEqualTo("1.0.0");

        JsonNode stages = run.path("stages");
        Map<String, JsonNode> stageByKey = new LinkedHashMap<>();
        stages.forEach(stage -> stageByKey.put(stage.path("key").asString(), stage));

        // E-A1: requirement quality, clarification skipped with the no-clarification rationale
        assertThat(stageByKey.get("CLARIFICATION").path("status").asString()).isEqualTo("SKIPPED");
        assertThat(stageByKey.get("CLARIFICATION").path("skipReason").asString()).isNotBlank();
        Map<String, JsonNode> artifacts = currentArtifacts(runPath);
        JsonNode normalized = content(artifacts.get("NORMALIZED_REQUIREMENT"));
        assertThat(normalized.path("clarificationRequired").asBoolean()).isFalse();
        assertThat(normalized.path("clarificationRationale").asString()).isNotBlank();
        List<String> checks = new ArrayList<>();
        normalized.path("qualityChecks").forEach(check -> {
            checks.add(check.path("check").asString());
            assertThat(check.path("result").asString()).as("quality check %s", check.path("check")).isEqualTo("PASS");
        });
        assertThat(checks).hasSize(5);

        // E-A2: decomposition with dependencies
        JsonNode taskGraph = content(artifacts.get("TASK_GRAPH"));
        assertThat(taskGraph.path("tasks").size()).isPositive();

        // E-A3: API and schema impact in the design
        JsonNode design = content(artifacts.get("DESIGN"));
        assertThat(design.path("apiChanges").size()).isPositive();
        assertThat(design.path("schemaChanges").size()).isPositive();
        assertThat(design.path("materialChange").asBoolean()).isTrue();

        // E-A4: fork/join scheduling from the attempt audit (scheduling cycles) and the timeline
        JsonNode timeline = json(send("GET", runPath + "/timeline", Tokens.AUDITOR, null));
        Map<String, Long> cycles = schedulingCycles(runId);
        assertThat(FORK.stream().map(cycles::get).collect(Collectors.toSet()))
                .as("IMPLEMENTATION and DOCUMENTATION dispatched in one scheduling cycle: %s", cycles).hasSize(1).doesNotContainNull();
        assertThat(VERIFY.stream().map(cycles::get).collect(Collectors.toSet()))
                .as("TESTING and SECURITY_VERIFICATION dispatched in one scheduling cycle: %s", cycles).hasSize(1).doesNotContainNull();
        Instant validationStart = null;
        Instant lastPredecessorEnd = Instant.MIN;
        for (JsonNode attempt : timeline) {
            String key = attempt.path("stageKey").asString();
            if (key.equals("VALIDATION") && validationStart == null) {
                validationStart = Instant.parse(attempt.path("startedAt").asString());
            }
            if (FORK.contains(key) || VERIFY.contains(key)) {
                assertThat(attempt.path("finishedAt").isMissingNode()).as("%s finished", key).isFalse();
                Instant end = Instant.parse(attempt.path("finishedAt").asString());
                lastPredecessorEnd = end.isAfter(lastPredecessorEnd) ? end : lastPredecessorEnd;
            }
        }
        assertThat(validationStart).as("VALIDATION started").isNotNull();
        assertThat(validationStart).as("VALIDATION starts only after all four predecessors finished")
                .isAfterOrEqualTo(lastPredecessorEnd);

        // E-A5: decisions are the two simulated human gate decisions, bound to the reviewed artifacts
        JsonNode decisions = json(send("GET", runPath + "/decisions", Tokens.AUDITOR, null));
        Map<String, String> gateActors = new LinkedHashMap<>();
        decisions.forEach(decision -> {
            if (decision.path("type").asString().equals("GATE")) {
                gateActors.put(decision.path("stageKey").asString(), decision.path("actorId").asString());
                assertThat(decision.path("outcome").asString()).isEqualTo("APPROVED");
                assertThat(decision.path("rationale").asString()).contains("simulated human input");
                assertThat(decision.path("boundFingerprints").size()).isPositive();
            }
        });
        assertThat(gateActors).containsEntry("ARCHITECTURE_APPROVAL", "bob").containsEntry("RELEASE_APPROVAL", "carol");

        // E-A6: every acceptance criterion verified by a passing probe
        JsonNode testReport = content(artifacts.get("TEST_REPORT"));
        assertThat(testReport.path("failed").asLong()).isZero();
        assertThat(testReport.path("unverifiedCriteria").size()).isZero();
        Set<String> verified = new java.util.TreeSet<>();
        testReport.path("results").forEach(result -> result.path("verifies").forEach(ac -> verified.add(ac.asString())));
        assertThat(verified).contains("AC-1", "AC-2", "AC-3", "AC-4", "AC-5", "AC-6");

        // E-A7: policy outcomes carry the policy-set version, none mandatory-failed
        JsonNode evaluations = json(send("GET", runPath + "/policy-evaluations", Tokens.AUDITOR, null));
        assertThat(evaluations.size()).isPositive();
        evaluations.forEach(evaluation -> {
            assertThat(evaluation.path("policySetVersion").asString()).isEqualTo("1.0.0");
            assertThat(evaluation.path("outcome").asString()).as("policy %s", evaluation.path("policyId")).isNotEqualTo("FAIL");
        });

        // The released capability is usable over HTTP (AC-1, AC-6 against the live application plane)
        String alias = "scn-a-" + runId.substring(0, 8);
        HttpResponse<String> created = send("POST", "/api/v1/links", Tokens.CONSUMER,
                "{\"url\":\"https://example.com/spring-sale\",\"alias\":\"" + alias + "\"}");
        assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
        assertThat(json(created).path("code").asString()).isEqualTo(alias);
        HttpResponse<String> redirect = send("GET", "/" + alias, null, null);
        assertThat(redirect.statusCode()).isEqualTo(302);
        assertThat(redirect.headers().firstValue("Location")).contains("https://example.com/spring-sale");
        HttpResponse<String> conflict = send("POST", "/api/v1/links", Tokens.CONSUMER,
                "{\"url\":\"https://example.com/other\",\"alias\":\"" + alias + "\"}");
        assertThat(json(conflict).path("code").asString()).isEqualTo("ALIAS_CONFLICT");

        // E-A8: audit integrity
        AuditVerification verification = audit.verify(runId);
        assertThat(verification.valid()).as(verification.message()).isTrue();

        // E-A9: final engineering summary
        assertThat(artifacts).containsKey("FINAL_SUMMARY");
        String summary = content(artifacts.get("FINAL_SUMMARY")).asString();
        assertThat(summary).isNotBlank();

        export(runId, run, normalized, taskGraph, design, timeline, cycles, decisions, testReport, evaluations, verification, summary);
    }

    // --- evidence -------------------------------------------------------------------------------

    private void export(String runId, JsonNode run, JsonNode normalized, JsonNode taskGraph, JsonNode design, JsonNode timeline,
            Map<String, Long> cycles, JsonNode decisions, JsonNode testReport, JsonNode evaluations, AuditVerification verification,
            String summary) {
        EvidenceExporter evidence = new EvidenceExporter("scn-a", getClass(), true);
        evidence.json("E-A1-requirement-quality", Map.of("runId", runId,
                "qualityChecks", normalized.path("qualityChecks"),
                "clarificationRequired", normalized.path("clarificationRequired"),
                "clarificationRationale", normalized.path("clarificationRationale"),
                "clarificationStage", stage(run, "CLARIFICATION")));
        evidence.json("E-A2-decomposition", taskGraph);
        evidence.json("E-A3-api-and-schema-impact", Map.of("apiChanges", design.path("apiChanges"),
                "schemaChanges", design.path("schemaChanges"), "materialChange", design.path("materialChange"),
                "materialReasons", design.path("materialReasons")));
        evidence.json("E-A4-plan-and-timeline", Map.of("stages", run.path("stages"), "timeline", timeline,
                "schedulingCycles", cycles));
        evidence.json("E-A5-decisions", decisions);
        evidence.json("E-A6-acceptance-results", testReport);
        evidence.json("E-A7-policy-outcomes", evaluations);
        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("verification", verification);
        audit.put("events", this.audit.chain(runId).stream().map(ScenarioAGreenfieldE2ETest::auditRow).toList());
        evidence.json("E-A8-audit-trail", audit);
        evidence.markdown("E-A9-final-summary", summary);
    }

    private static Map<String, Object> auditRow(AuditEvent event) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("action", event.getAction());
        row.put("target", event.getTarget());
        row.put("actorId", event.getActorId());
        row.put("details", event.getDetails() == null ? null : CanonicalJson.parse(event.getDetails()));
        return row;
    }

    private static JsonNode stage(JsonNode run, String key) {
        for (JsonNode stage : run.path("stages")) {
            if (stage.path("key").asString().equals(key)) {
                return stage;
            }
        }
        return null;
    }

    // --- scheduling -----------------------------------------------------------------------------

    /** First scheduling cycle in which each stage was dispatched, from the ATTEMPT_STARTED audit events. */
    private Map<String, Long> schedulingCycles(String runId) {
        Map<String, Long> cycles = new LinkedHashMap<>();
        for (AuditEvent event : audit.chain(runId)) {
            if (event.getAction().equals("ATTEMPT_STARTED")) {
                JsonNode details = CanonicalJson.parse(event.getDetails());
                cycles.putIfAbsent(event.getTarget(), details.path("schedulingCycle").asLong());
            }
        }
        return cycles;
    }

    // --- HTTP -----------------------------------------------------------------------------------

    private void awaitPendingGate(String runPath, String gate) {
        await().atMost(Duration.ofSeconds(60)).pollInterval(Duration.ofMillis(100)).until(() -> {
            JsonNode run = json(send("GET", runPath, Tokens.AUDITOR, null));
            assertThat(run.path("status").asString()).as("run failed before %s: %s", gate, run.path("terminalReason"))
                    .isNotIn("FAILED", "REJECTED", "CANCELLED");
            for (JsonNode action : run.path("pendingActions")) {
                if (action.path("stageKey").asString().equals(gate)) {
                    return true;
                }
            }
            return false;
        });
    }

    private JsonNode awaitTerminal(String runPath) {
        JsonNode[] last = new JsonNode[1];
        await().atMost(Duration.ofSeconds(60)).pollInterval(Duration.ofMillis(100)).until(() -> {
            last[0] = json(send("GET", runPath, Tokens.AUDITOR, null));
            return last[0].path("terminalOutcome").isString();
        });
        return last[0];
    }

    private void decide(String runPath, String gate, String token, String rationale) throws Exception {
        HttpResponse<String> response = send("POST", runPath + "/gates/" + gate + "/decision", token,
                CanonicalJson.write(Map.of("decision", "APPROVE", "rationale", rationale)));
        assertThat(response.statusCode()).as("%s decision: %s", gate, response.body()).isEqualTo(200);
    }

    private Map<String, JsonNode> currentArtifacts(String runPath) throws Exception {
        Map<String, JsonNode> current = new LinkedHashMap<>();
        for (JsonNode artifact : json(send("GET", runPath + "/artifacts", Tokens.AUDITOR, null))) {
            if (!artifact.path("superseded").asBoolean()) {
                current.put(artifact.path("type").asString(),
                        json(send("GET", runPath + "/artifacts/" + artifact.path("artifactId").asString(), Tokens.AUDITOR, null)));
            }
        }
        return current;
    }

    /** The artifact's content: parsed JSON for JSON artifacts, a text node otherwise. */
    private static JsonNode content(JsonNode artifactDetail) {
        assertThat(artifactDetail).as("artifact present").isNotNull();
        String content = artifactDetail.path("content").asString();
        return artifactDetail.path("mediaType").asString().equals("application/json")
                ? CanonicalJson.parse(content)
                : tools.jackson.databind.node.JsonNodeFactory.instance.stringNode(content);
    }

    private HttpResponse<String> send(String method, String path, String token, String body) throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port() + path))
                .timeout(Duration.ofSeconds(20));
        if (token != null) {
            request.header("Authorization", Tokens.bearer(token));
        }
        if (body != null) {
            request.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(body));
        } else {
            request.method(method, HttpRequest.BodyPublishers.noBody());
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private int port() {
        return Integer.parseInt(Objects.requireNonNull(environment.getProperty("local.server.port")));
    }

    private static JsonNode json(HttpResponse<String> response) {
        return CanonicalJson.parse(response.body());
    }

    private static String scenarioInput() throws IOException {
        try (InputStream in = Objects.requireNonNull(ScenarioAGreenfieldE2ETest.class.getResourceAsStream("/scenarios/scn-a-greenfield.json"))) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
