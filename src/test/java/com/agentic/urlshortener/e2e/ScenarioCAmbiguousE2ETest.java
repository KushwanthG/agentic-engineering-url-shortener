package com.agentic.urlshortener.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

import com.agentic.urlshortener.common.util.CanonicalJson;
import com.agentic.urlshortener.orchestration.audit.AuditService;
import com.agentic.urlshortener.orchestration.audit.AuditVerification;
import com.agentic.urlshortener.support.ArtifactSchemas;
import com.agentic.urlshortener.support.GovernanceInvariants;
import com.agentic.urlshortener.support.EvidenceExporter;
import com.agentic.urlshortener.support.HttpDriver;
import com.agentic.urlshortener.support.Tokens;

import tools.jackson.databind.JsonNode;

/**
 * T098 — SCN-C ambiguous requirement end to end over HTTP with the real agents: AMB-001 ("better
 * link expiry") stops at clarification before any design or implementation. The approver
 * {@code bob} answers with the reference decisions D1..D4 of the spec. These answers, like the gate
 * decisions of {@code bob} and {@code carol}, are <strong>simulated human input</strong>; a live
 * walkthrough uses the candidate's own answers. The answers produce requirement version 2 and plan
 * version 2 (the change modifies existing link creation, so impact analysis and regression testing are
 * added), the run resumes at requirement analysis, and default expiry is released with 30 days.
 * Exports the 17 evidence items of spec SCN-C to {@code target/evidence/scn-c/}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Tag("SCN-C")
@Tag("FR-GOV-08")
@Tag("FR-RPL-01")
@Tag("FR-RPL-02")
@Tag("FR-CAP-04")
@Tag("FR-ORC-15")
@Tag("SC-002")
class ScenarioCAmbiguousE2ETest {

    private static final String SIMULATED = " (simulated human input: automated test acting as a labeled demo principal)";
    /** Reference decisions D1..D4 (spec SCN-C) per ambiguity type, as option ids of the ambiguity lexicon. */
    private static final Map<String, String> REFERENCE_DECISIONS = Map.of("VAGUE_TERM", "B", "UNDEFINED_CONCEPT", "A", "CONFLICT", "A",
            "UNBOUNDED_SCOPE", "A", "MISSING_ACCEPTANCE_CRITERIA", "A", "UNSPECIFIED_TYPE", "B", "EXISTING_DATA", "A");
    private static final List<String> DOWNSTREAM = List.of("DECOMPOSITION", "THREAT_ASSESSMENT", "IMPACT_ANALYSIS", "DESIGN",
            "IMPLEMENTATION");

    @Autowired private Environment environment;
    @Autowired private AuditService audit;

    @Test
    void anAmbiguousRequirementIsClarifiedReplannedAndDelivered() {
        HttpDriver http = new HttpDriver(Integer.parseInt(Objects.requireNonNull(environment.getProperty("local.server.port"))));
        String input = HttpDriver.scenario("scn-c-ambiguous.json");
        HttpResponse<String> existing = http.send("POST", "/api/v1/links", Tokens.CONSUMER, "{\"url\":\"https://example.com/old\"}");
        String existingCode = HttpDriver.json(existing).path("code").asString();

        String runPath = http.submit(input);
        String runId = runPath.substring(runPath.lastIndexOf('/') + 1);

        // Detection and suspension.
        http.awaitPendingAction(runPath, "CLARIFICATION");
        JsonNode waiting = http.run(runPath);
        assertThat(waiting.path("status").asString()).isEqualTo("AWAITING_HUMAN");
        Map<String, JsonNode> atClarification = http.currentArtifacts(runPath);
        JsonNode normalizedV1 = HttpDriver.content(atClarification.get("NORMALIZED_REQUIREMENT"));
        JsonNode request = HttpDriver.content(atClarification.get("CLARIFICATION_REQUEST"));
        List<String> types = new ArrayList<>();
        normalizedV1.path("ambiguities").forEach(a -> types.add(a.path("type").asString()));
        assertThat(types).contains("VAGUE_TERM", "UNDEFINED_CONCEPT", "CONFLICT", "UNBOUNDED_SCOPE", "MISSING_ACCEPTANCE_CRITERIA",
                "UNSPECIFIED_TYPE");
        JsonNode timelineBefore = http.get(runPath + "/timeline", Tokens.AUDITOR);
        timelineBefore.forEach(e -> assertThat(DOWNSTREAM).doesNotContain(e.path("stageKey").asString()));

        // Simulated answers D1..D4.
        Map<String, String> typeById = new LinkedHashMap<>();
        normalizedV1.path("ambiguities").forEach(a -> typeById.put(a.path("id").asString(), a.path("type").asString()));
        List<Map<String, Object>> answers = new ArrayList<>();
        request.path("questions").forEach(q -> {
            String type = typeById.getOrDefault(q.path("ambiguityId").asString(), q.path("ambiguityId").asString());
            answers.add(Map.of("questionId", q.path("questionId").asString(), "optionId", REFERENCE_DECISIONS.get(type)));
        });
        HttpResponse<String> answered = http.send("POST", runPath + "/clarifications", Tokens.APPROVER,
                CanonicalJson.write(Map.of("answers", answers, "rationale", "reference decisions D1-D4" + SIMULATED)));
        assertThat(answered.statusCode()).as(answered.body()).isEqualTo(200);
        assertThat(HttpDriver.json(answered).path("requirementVersion").asInt()).isEqualTo(2);
        assertThat(HttpDriver.json(answered).path("planVersion").asInt()).isEqualTo(2);

        // Re-planned brownfield path to release.
        http.awaitPendingAction(runPath, "ARCHITECTURE_APPROVAL");
        assertThat(http.decideGate(runPath, "ARCHITECTURE_APPROVAL", Tokens.APPROVER, "APPROVE", "design reviewed" + SIMULATED).statusCode())
                .isEqualTo(200);
        http.awaitPendingAction(runPath, "RELEASE_APPROVAL");
        assertThat(http.decideGate(runPath, "RELEASE_APPROVAL", Tokens.RELEASE_OWNER, "APPROVE", "readiness reviewed" + SIMULATED)
                .statusCode()).isEqualTo(200);
        JsonNode run = http.awaitTerminal(runPath);
        GovernanceInvariants.assertHold(http, runPath);
        assertThat(ArtifactSchemas.assertRunArtifactsValid(http, runPath)).isGreaterThan(10);
        assertThat(run.path("status").asString()).as("terminal reason: %s", run.path("terminalReason")).isEqualTo("COMPLETED");
        assertThat(run.path("readiness").asString()).isEqualTo("READY");

        JsonNode requirementVersions = http.get(runPath + "/requirement-versions", Tokens.AUDITOR);
        JsonNode v2 = CanonicalJson.parse(requirementVersions.get(1).path("content").asString());
        assertThat(requirementVersions.get(1).path("source").asString()).isEqualTo("CLARIFIED");
        assertThat(v2.path("parameters").path("defaultExpiryDays").asInt()).isEqualTo(30);
        JsonNode planVersions = http.get(runPath + "/plan-versions", Tokens.AUDITOR);
        JsonNode planV2 = planVersions.get(1);
        assertThat(planV2.path("trigger").asString()).isEqualTo("CLARIFICATION");
        assertThat(planV2.path("diff").path("added").toString()).contains("IMPACT_ANALYSIS", "REGRESSION_TESTING");
        JsonNode timeline = http.get(runPath + "/timeline", Tokens.AUDITOR);
        List<String> ingestion = new ArrayList<>();
        List<String> analysis = new ArrayList<>();
        timeline.forEach(e -> {
            if (e.path("stageKey").asString().equals("REQUIREMENT_INGESTION")) {
                ingestion.add(e.path("outcome").asString());
            }
            if (e.path("stageKey").asString().equals("REQUIREMENT_ANALYSIS")) {
                analysis.add(e.path("outcome").asString());
            }
        });
        assertThat(ingestion).as("ingestion is not repeated").hasSize(1);
        assertThat(analysis).as("resumed at requirement analysis").hasSize(2);

        Map<String, JsonNode> artifacts = http.currentArtifacts(runPath);
        JsonNode release = HttpDriver.content(artifacts.get("RELEASE_RECORD"));
        assertThat(release.path("parameters").path("defaultExpiryDays").asInt()).isEqualTo(30);

        // The released default: a new link without an expiry expires after 30 days; the old link is unchanged.
        Instant before = Instant.now();
        HttpResponse<String> created = http.send("POST", "/api/v1/links", Tokens.CONSUMER, "{\"url\":\"https://example.com/new\"}");
        Instant expiresAt = Instant.parse(HttpDriver.json(created).path("expiresAt").asString());
        assertThat(Duration.between(before, expiresAt)).isBetween(Duration.ofDays(30).minusMinutes(1), Duration.ofDays(30).plusMinutes(1));
        JsonNode old = http.get("/api/v1/links/" + existingCode, Tokens.CONSUMER);
        assertThat(old.path("expiresAt").isMissingNode() || old.path("expiresAt").isNull()).as("existing link unchanged (D4)").isTrue();

        AuditVerification verification = audit.verify(runId);
        assertThat(verification.valid()).as(verification.message()).isTrue();

        EvidenceExporter evidence = new EvidenceExporter("scn-c", getClass(), true);
        evidence.json("C-01-original-input", CanonicalJson.parse(input));
        evidence.json("C-02-detected-ambiguities", normalizedV1.path("ambiguities"));
        evidence.json("C-03-affected-requirements-and-components", Map.of("capabilities", normalizedV1.path("capabilities"),
                "affects", normalizedV1.path("ambiguities").findValues("affects")));
        evidence.json("C-04-state-before-detection", timelineBefore);
        evidence.json("C-05-transition-to-waiting", Map.of("status", waiting.path("status"), "stages", waiting.path("stages"),
                "pendingActions", waiting.path("pendingActions")));
        evidence.json("C-06-why-implementation-cannot-continue", Map.of("reason", request.path("reason"),
                "clarificationRationale", normalizedV1.path("clarificationRationale")));
        evidence.json("C-07-clarification-request", request);
        evidence.json("C-08-human-decisions", http.get(runPath + "/decisions", Tokens.AUDITOR));
        evidence.json("C-09-updated-requirement", requirementVersions);
        evidence.json("C-10-downstream-impact-analysis", HttpDriver.content(artifacts.get("IMPACT_ANALYSIS")));
        evidence.json("C-11-replanning-event", planVersions);
        List<Map<String, Object>> allArtifacts = new ArrayList<>();
        http.get(runPath + "/artifacts", Tokens.AUDITOR).forEach(a -> allArtifacts.add(Map.of("type", a.path("type").asString(),
                "version", a.path("version").asInt(), "generation", a.path("generation").asInt(), "superseded",
                a.path("superseded").asBoolean(), "stageKey", a.path("stageKey").asString())));
        evidence.json("C-12-invalidated-and-regenerated-artifacts", allArtifacts);
        evidence.json("C-13-resumed-state", Map.of("requirementIngestionAttempts", ingestion.size(), "requirementAnalysisAttempts",
                analysis.size(), "timeline", timeline));
        evidence.json("C-14-final-validation", Map.of("validation", HttpDriver.content(artifacts.get("VALIDATION_REPORT")),
                "acceptance", HttpDriver.content(artifacts.get("TEST_REPORT")), "regression",
                HttpDriver.content(artifacts.get("REGRESSION_REPORT"))));
        evidence.json("C-15-audit-trail", Map.of("verification", verification, "events", audit.chain(runId).size()));
        evidence.json("C-16-terminal-outcome", Map.of("status", run.path("status"), "readiness", run.path("readiness"),
                "releasedParameters", release.path("parameters")));
        evidence.markdown("C-17-final-summary", HttpDriver.content(artifacts.get("FINAL_SUMMARY")).asString());
    }
}
