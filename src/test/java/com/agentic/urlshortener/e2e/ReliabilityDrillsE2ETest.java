package com.agentic.urlshortener.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import com.agentic.urlshortener.common.util.CanonicalJson;
import com.agentic.urlshortener.support.EvidenceExporter;
import com.agentic.urlshortener.support.HttpDriver;
import com.agentic.urlshortener.support.Tokens;

import tools.jackson.databind.JsonNode;

/**
 * T080 — reliability drills RDR-01..RDR-04, RDR-06, RDR-07 end to end over HTTP with the real agents
 * (RDR-05 restart is RestartResumeTest). Every drill uses the SCN-A input with <strong>simulated</strong>
 * faults, and gate decisions by {@code bob} and {@code carol} are <strong>simulated human input</strong>.
 * Evidence goes to {@code target/evidence/drills/}. Backoff and the deadline sweep are shortened.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "app.orchestration.stages.defaults.initial-backoff=PT0.05S",
        "app.orchestration.deadline-sweep-interval=PT1S" })
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Tag("RDR-01")
@Tag("RDR-02")
@Tag("RDR-03")
@Tag("RDR-04")
@Tag("RDR-06")
@Tag("RDR-07")
@Tag("FR-REL-01")
@Tag("FR-REL-03")
@Tag("FR-REL-05")
@Tag("FR-REL-06")
@Tag("FR-REL-07")
@Tag("FR-POL-04")
@Tag("SC-002")
class ReliabilityDrillsE2ETest {

    private static final String SIMULATED = " (simulated human input: automated drill acting as a labeled demo principal)";

    @Autowired private Environment environment;

    private HttpDriver http;
    private final EvidenceExporter evidence = new EvidenceExporter("drills", ReliabilityDrillsE2ETest.class, true);

    @BeforeEach
    void driver() {
        http = new HttpDriver(Integer.parseInt(Objects.requireNonNull(environment.getProperty("local.server.port"))));
    }

    private String submitWith(String simulationJson) {
        return http.submit(HttpDriver.withSimulation(HttpDriver.scenario("scn-a-greenfield.json"), simulationJson));
    }

    private void approve(String runPath, String gate, String token) {
        http.awaitPendingAction(runPath, gate);
        HttpResponse<String> response = http.decideGate(runPath, gate, token, "APPROVE", "drill review" + SIMULATED);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
    }

    private List<JsonNode> timelineOf(String runPath, String stage) {
        List<JsonNode> entries = new ArrayList<>();
        http.get(runPath + "/timeline", Tokens.AUDITOR).forEach(e -> {
            if (e.path("stageKey").asString().equals(stage)) {
                entries.add(e);
            }
        });
        return entries;
    }

    private List<String> decisionTypes(String runPath) {
        List<String> types = new ArrayList<>();
        http.get(runPath + "/decisions", Tokens.AUDITOR).forEach(d -> types.add(d.path("type").asString()));
        return types;
    }

    private Map<String, Object> record(String drill, String runPath, JsonNode run) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("drill", drill);
        record.put("runId", run.path("runId").asString());
        record.put("status", run.path("status").asString());
        record.put("readiness", run.path("readiness"));
        record.put("terminalReason", run.path("terminalReason"));
        record.put("stages", run.path("stages"));
        record.put("timeline", http.get(runPath + "/timeline", Tokens.AUDITOR));
        record.put("decisions", http.get(runPath + "/decisions", Tokens.AUDITOR));
        return record;
    }

    @Test
    @Order(1)
    void rdr03VerificationFailureAfterReleaseRollsBackAndSafeStops() {
        String run = submitWith("{\"faults\":[{\"stage\":\"RELEASE\",\"type\":\"VERIFICATION_FAILURE\"}]}");
        approve(run, "ARCHITECTURE_APPROVAL", Tokens.APPROVER);
        approve(run, "RELEASE_APPROVAL", Tokens.RELEASE_OWNER);
        JsonNode result = http.awaitTerminal(run);

        assertThat(result.path("status").asString()).isEqualTo("SAFE_STOPPED");
        assertThat(result.path("terminalReason").asString()).contains("RELEASE").contains("rolled back to released=false");
        HttpResponse<String> alias = http.send("POST", "/api/v1/links", Tokens.CONSUMER,
                "{\"url\":\"https://example.com/drill\",\"alias\":\"rdr-03-check\"}");
        assertThat(alias.statusCode()).as("capability withdrawn again").isEqualTo(422);
        assertThat(HttpDriver.json(alias).path("code").asString()).isEqualTo("CAPABILITY_NOT_AVAILABLE");
        assertThat(decisionTypes(run)).contains("SAFE_STOP");

        Map<String, Object> record = record("RDR-03 post-release verification failure, rollback, safe-stop", run, result);
        record.put("capabilityAfterRollback", "custom-alias not available: " + HttpDriver.json(alias).path("code").asString());
        evidence.json("RDR-03-rollback", record);
    }

    @Test
    @Order(2)
    void rdr01TransientTestingFailuresRecoverThroughBoundedRetry() {
        String run = submitWith("{\"faults\":[{\"stage\":\"TESTING\",\"type\":\"TRANSIENT_ERROR\",\"occurrences\":2}]}");
        approve(run, "ARCHITECTURE_APPROVAL", Tokens.APPROVER);
        approve(run, "RELEASE_APPROVAL", Tokens.RELEASE_OWNER);
        JsonNode result = http.awaitTerminal(run);

        assertThat(result.path("status").asString()).isEqualTo("COMPLETED");
        List<JsonNode> testing = timelineOf(run, "TESTING");
        assertThat(testing).extracting(e -> e.path("outcome").asString())
                .containsExactly("FAILED_TRANSIENT", "FAILED_TRANSIENT", "SUCCEEDED");
        assertThat(testing).extracting(e -> e.path("simulatedFault").asString(null))
                .containsExactly("TRANSIENT_ERROR", "TRANSIENT_ERROR", null);
        evidence.json("RDR-01-retry", record("RDR-01 transient TESTING failures recovered by bounded retry", run, result));
    }

    @Test
    @Order(3)
    void rdr02DocumentationFallbackLowersReadiness() {
        String run = submitWith("{\"faults\":[{\"stage\":\"DOCUMENTATION\",\"type\":\"PERMANENT_ERROR\"}]}");
        approve(run, "ARCHITECTURE_APPROVAL", Tokens.APPROVER);
        http.awaitPendingAction(run, "RELEASE_APPROVAL");
        assertThat(http.run(run).path("readiness").asString()).isEqualTo("READY_WITH_ACCEPTED_LIMITATIONS");
        approve(run, "RELEASE_APPROVAL", Tokens.RELEASE_OWNER);
        JsonNode result = http.awaitTerminal(run);

        assertThat(result.path("status").asString()).isEqualTo("COMPLETED");
        assertThat(timelineOf(run, "DOCUMENTATION")).extracting(e -> e.path("fallback").asBoolean()).containsExactly(false, true);
        assertThat(decisionTypes(run)).contains("FALLBACK_USED");
        JsonNode readiness = HttpDriver.content(http.currentArtifacts(run).get("READINESS_REPORT"));
        assertThat(readiness.path("limitations").toString()).contains("DOCUMENTATION").contains("degraded");

        Map<String, Object> record = record("RDR-02 documentation fallback, degraded, accepted limitation", run, result);
        record.put("readinessReport", readiness);
        evidence.json("RDR-02-fallback", record);
    }

    @Test
    @Order(4)
    void rdr04AnUnansweredGateSafeStopsAtItsDeadline() {
        String run = submitWith("{\"gateDeadlineSeconds\":2,\"faults\":[]}");
        http.awaitPendingAction(run, "ARCHITECTURE_APPROVAL");
        JsonNode result = http.awaitTerminal(run);

        assertThat(result.path("status").asString()).isEqualTo("SAFE_STOPPED");
        assertThat(result.path("terminalReason").asString()).contains("deadline").contains("not approved by default");
        HttpResponse<String> late = http.decideGate(run, "ARCHITECTURE_APPROVAL", Tokens.APPROVER, "APPROVE", "too late" + SIMULATED);
        assertThat(late.statusCode()).isEqualTo(409);
        assertThat(decisionTypes(run)).doesNotContain("GATE");

        Map<String, Object> record = record("RDR-04 gate deadline, escalation, safe-stop", run, result);
        record.put("lateDecision", Map.of("status", late.statusCode(), "code", HttpDriver.json(late).path("code").asString()));
        evidence.json("RDR-04-gate-deadline", record);
    }

    @Test
    @Order(5)
    void rdr06AFailedMandatoryPolicyNeedsAnApprovedExceptionAndARejectionStops() {
        String fault = "{\"faults\":[{\"stage\":\"COMPLIANCE_EVALUATION\",\"type\":\"POLICY_FAILURE\",\"policyId\":\"DOC-001\"}]}";
        String exceptionRequest = CanonicalJson.write(Map.of("policyId", "DOC-001",
                "reason", "the capability guide is published with the next documentation release (drill)",
                "scope", "this run only", "compensatingControl", "release notes link to the generated runbook until the guide ships",
                "expiresAt", Instant.now().plus(7, ChronoUnit.DAYS).toString()));

        String approved = submitWith(fault);
        approve(approved, "ARCHITECTURE_APPROVAL", Tokens.APPROVER);
        http.awaitPendingAction(approved, "COMPLIANCE_EVALUATION");
        assertThat(http.run(approved).path("readiness").asString()).isEqualTo("NOT_READY");
        String exceptionId = HttpDriver.json(http.send("POST", approved + "/policy-exceptions", Tokens.REQUESTER, exceptionRequest))
                .path("exceptionId").asString();
        HttpResponse<String> decision = http.send("POST", approved + "/policy-exceptions/" + exceptionId + "/decision", Tokens.APPROVER,
                CanonicalJson.write(Map.of("decision", "APPROVE", "rationale", "compensating control accepted" + SIMULATED)));
        assertThat(decision.statusCode()).as(decision.body()).isEqualTo(200);
        approve(approved, "RELEASE_APPROVAL", Tokens.RELEASE_OWNER);
        JsonNode completed = http.awaitTerminal(approved);
        assertThat(completed.path("status").asString()).isEqualTo("COMPLETED");
        assertThat(completed.path("readiness").asString()).isEqualTo("READY_WITH_ACCEPTED_LIMITATIONS");
        List<String> doc001 = new ArrayList<>();
        http.get(approved + "/policy-evaluations", Tokens.AUDITOR).forEach(e -> {
            if (e.path("policyId").asString().equals("DOC-001")) {
                doc001.add(e.path("outcome").asString());
            }
        });
        assertThat(doc001).containsExactly("FAIL", "EXCEPTION_REQUESTED");

        String rejected = submitWith(fault);
        approve(rejected, "ARCHITECTURE_APPROVAL", Tokens.APPROVER);
        http.awaitPendingAction(rejected, "COMPLIANCE_EVALUATION");
        String rejectedId = HttpDriver.json(http.send("POST", rejected + "/policy-exceptions", Tokens.REQUESTER, exceptionRequest))
                .path("exceptionId").asString();
        http.send("POST", rejected + "/policy-exceptions/" + rejectedId + "/decision", Tokens.APPROVER,
                CanonicalJson.write(Map.of("decision", "REJECT", "rationale", "compensating control insufficient" + SIMULATED)));
        JsonNode stopped = http.awaitTerminal(rejected);
        assertThat(stopped.path("status").asString()).isEqualTo("SAFE_STOPPED");

        Map<String, Object> record = new LinkedHashMap<>();
        record.put("drill", "RDR-06 failed mandatory policy: approved exception with compensating control; rejected exception");
        Map<String, Object> approvedRecord = record("approved exception", approved, completed);
        approvedRecord.put("policyEvaluations", http.get(approved + "/policy-evaluations", Tokens.AUDITOR));
        approvedRecord.put("exception", HttpDriver.json(decision));
        record.put("approvedExceptionRun", approvedRecord);
        record.put("rejectedExceptionRun", record("rejected exception", rejected, stopped));
        evidence.json("RDR-06-policy-exception", record);
    }

    @Test
    @Order(6)
    void rdr07OperatorPausesResumesAndSafeStops() {
        String run = submitWith("{\"faults\":[{\"stage\":\"DESIGN\",\"type\":\"DELAY\",\"delayMillis\":1500}]}");
        await().atMost(Duration.ofSeconds(30)).until(() -> !timelineOf(run, "DESIGN").isEmpty());

        HttpResponse<String> paused = http.operate(run, "pause", Tokens.RELEASE_OWNER, "maintenance window (drill)");
        assertThat(HttpDriver.json(paused).path("status").asString()).isEqualTo("PAUSED");
        await().atMost(Duration.ofSeconds(30)).until(() -> timelineOf(run, "DESIGN").get(0).path("outcome").asString().equals("SUCCEEDED"));
        assertThat(http.run(run).path("status").asString()).isEqualTo("PAUSED");
        assertThat(timelineOf(run, "IMPLEMENTATION")).isEmpty();

        HttpResponse<String> resumed = http.operate(run, "resume", Tokens.RELEASE_OWNER, "maintenance finished (drill)");
        assertThat(resumed.statusCode()).isEqualTo(200);
        http.awaitPendingAction(run, "ARCHITECTURE_APPROVAL");
        HttpResponse<String> stopped = http.operate(run, "safe-stop", Tokens.RELEASE_OWNER, "change freeze (drill)");
        assertThat(HttpDriver.json(stopped).path("status").asString()).isEqualTo("SAFE_STOPPED");
        assertThat(HttpDriver.json(stopped).path("terminalReason").asString()).contains("carol").contains("change freeze");
        assertThat(decisionTypes(run)).contains("OPERATOR_ACTION", "SAFE_STOP");

        evidence.json("RDR-07-operator-controls", record("RDR-07 pause, resume, safe-stop by the release owner", run,
                HttpDriver.json(stopped)));
    }
}
