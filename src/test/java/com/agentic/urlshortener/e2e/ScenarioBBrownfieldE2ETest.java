package com.agentic.urlshortener.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
import com.agentic.urlshortener.support.GovernanceInvariants;
import com.agentic.urlshortener.support.EvidenceExporter;
import com.agentic.urlshortener.support.HttpDriver;
import com.agentic.urlshortener.support.Tokens;

import tools.jackson.databind.JsonNode;

/**
 * T089 — SCN-B brownfield end to end over HTTP with the real agents: BF-001 (click-limited links)
 * changes existing redirect, analytics, and creation behavior. Written before the click-limit code:
 * its first run must show the orchestrator refusing the undelivered capability at IMPLEMENTATION
 * (FR-ORC-15). Gate decisions by {@code bob} and {@code carol} are <strong>simulated human input</strong>.
 * Exports E-B1..E-B9 to {@code target/evidence/scn-b/}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Tag("SCN-B")
@Tag("FR-ORC-14")
@Tag("FR-ORC-15")
@Tag("FR-CAP-03")
@Tag("SC-002")
class ScenarioBBrownfieldE2ETest {

    private static final String SIMULATED = " (simulated human input: automated test acting as a labeled demo principal)";

    @Autowired private Environment environment;
    @Autowired private AuditService audit;

    @Test
    void clickLimitsReachReleaseWithoutChangingExistingLinks() {
        HttpDriver http = new HttpDriver(Integer.parseInt(Objects.requireNonNull(environment.getProperty("local.server.port"))));

        // A consumer link that exists before the change (AC-4).
        HttpResponse<String> existing = http.send("POST", "/api/v1/links", Tokens.CONSUMER, "{\"url\":\"https://example.com/before\"}");
        assertThat(existing.statusCode()).isEqualTo(201);
        String existingCode = HttpDriver.json(existing).path("code").asString();

        String runPath = http.submit(HttpDriver.scenario("scn-b-brownfield.json"));
        String runId = runPath.substring(runPath.lastIndexOf('/') + 1);

        http.awaitPendingAction(runPath, "ARCHITECTURE_APPROVAL");
        Map<String, JsonNode> atApproval = http.currentArtifacts(runPath);
        JsonNode design = HttpDriver.content(atApproval.get("DESIGN"));
        assertThat(design.path("schemaChanges").toString()).contains("V4__click_limit.sql");
        assertThat(design.path("rollbackPlan").asString()).contains("stored limits stay enforced");
        HttpResponse<String> architecture = http.decideGate(runPath, "ARCHITECTURE_APPROVAL", Tokens.APPROVER, "APPROVE",
                "impact analysis, migration and rollback reviewed" + SIMULATED);
        assertThat(architecture.statusCode()).as(architecture.body()).isEqualTo(200);

        http.awaitPendingAction(runPath, "RELEASE_APPROVAL");
        HttpResponse<String> release = http.decideGate(runPath, "RELEASE_APPROVAL", Tokens.RELEASE_OWNER, "APPROVE",
                "readiness, regression and compliance reviewed" + SIMULATED);
        assertThat(release.statusCode()).as(release.body()).isEqualTo(200);

        JsonNode run = http.awaitTerminal(runPath);
        GovernanceInvariants.assertHold(http, runPath);
        assertThat(run.path("status").asString()).as("terminal reason: %s", run.path("terminalReason")).isEqualTo("COMPLETED");
        assertThat(run.path("readiness").asString()).isEqualTo("READY");
        assertThat(run.path("classification").asString()).isEqualTo("CHANGE_TO_EXISTING");

        // Brownfield stages run in parallel with their siblings (same scheduling cycle).
        Map<String, Long> cycles = schedulingCycles(runId);
        assertThat(Stream.of(cycles.get("IMPACT_ANALYSIS"), cycles.get("DECOMPOSITION"), cycles.get("THREAT_ASSESSMENT")).distinct())
                .as("impact analysis ‖ decomposition ‖ threat assessment: %s", cycles).hasSize(1).doesNotContainNull();
        assertThat(Stream.of(cycles.get("REGRESSION_TESTING"), cycles.get("TESTING"), cycles.get("SECURITY_VERIFICATION")).distinct())
                .as("regression ‖ testing ‖ security: %s", cycles).hasSize(1).doesNotContainNull();

        Map<String, JsonNode> artifacts = http.currentArtifacts(runPath);
        JsonNode impact = HttpDriver.content(artifacts.get("IMPACT_ANALYSIS"));
        assertThat(impact.path("method").asString()).isEqualTo("SOURCE_SCAN");
        JsonNode regression = HttpDriver.content(artifacts.get("REGRESSION_REPORT"));
        assertThat(regression.path("failed").asInt()).isZero();
        assertThat(regression.path("results").size()).isEqualTo(6);
        JsonNode testReport = HttpDriver.content(artifacts.get("TEST_REPORT"));
        assertThat(testReport.path("unverifiedCriteria").size()).isZero();
        JsonNode evaluations = http.get(runPath + "/policy-evaluations", Tokens.AUDITOR);
        evaluations.forEach(e -> assertThat(e.path("outcome").asString()).as(e.path("policyId").asString()).isNotEqualTo("FAIL"));

        // The released capability works; the link created before the release stays unlimited.
        HttpResponse<String> limited = http.send("POST", "/api/v1/links", Tokens.CONSUMER,
                "{\"url\":\"https://example.com/limited\",\"maxClicks\":2}");
        assertThat(limited.statusCode()).as(limited.body()).isEqualTo(201);
        assertThat(HttpDriver.json(limited).path("maxClicks").asLong()).isEqualTo(2);
        String limitedCode = HttpDriver.json(limited).path("code").asString();
        assertThat(List.of(status(http, limitedCode), status(http, limitedCode), status(http, limitedCode))).containsExactly(302, 302, 410);
        assertThat(List.of(status(http, existingCode), status(http, existingCode), status(http, existingCode))).containsOnly(302);

        AuditVerification verification = audit.verify(runId);
        assertThat(verification.valid()).as(verification.message()).isTrue();

        export(runId, run, artifacts, impact, regression, design, testReport, evaluations, http.get(runPath + "/decisions", Tokens.AUDITOR),
                http.get(runPath + "/timeline", Tokens.AUDITOR), cycles, verification, existingCode, limitedCode);
    }

    private static int status(HttpDriver http, String code) {
        return http.send("GET", "/" + code, null, null).statusCode();
    }

    private Map<String, Long> schedulingCycles(String runId) {
        Map<String, Long> cycles = new LinkedHashMap<>();
        for (AuditEvent event : audit.chain(runId)) {
            if (event.getAction().equals("ATTEMPT_STARTED")) {
                cycles.putIfAbsent(event.getTarget(), CanonicalJson.parse(event.getDetails()).path("schedulingCycle").asLong());
            }
        }
        return cycles;
    }

    private void export(String runId, JsonNode run, Map<String, JsonNode> artifacts, JsonNode impact, JsonNode regression, JsonNode design,
            JsonNode testReport, JsonNode evaluations, JsonNode decisions, JsonNode timeline, Map<String, Long> cycles,
            AuditVerification verification, String existingCode, String limitedCode) {
        EvidenceExporter evidence = new EvidenceExporter("scn-b", getClass(), true);
        evidence.json("E-B1-impact-analysis", impact);
        evidence.json("E-B2-regression-results", regression);
        Map<String, Object> compatibility = new LinkedHashMap<>();
        compatibility.put("schemaChanges", design.path("schemaChanges"));
        compatibility.put("rollbackPlan", design.path("rollbackPlan"));
        compatibility.put("rollout", impact.path("rollout"));
        compatibility.put("rollback", impact.path("rollback"));
        compatibility.put("linkCreatedBeforeRelease", Map.of("code", existingCode, "afterRelease", "3 of 3 resolutions redirected"));
        compatibility.put("limitedLinkAfterRelease", Map.of("code", limitedCode, "maxClicks", 2, "statuses", List.of(302, 302, 410)));
        evidence.json("E-B3-data-compatibility-and-rollback", compatibility);
        evidence.json("E-B4-plan-and-timeline", Map.of("stages", run.path("stages"), "timeline", timeline, "schedulingCycles", cycles));
        evidence.json("E-B5-decisions", decisions);
        evidence.json("E-B6-acceptance-results", testReport);
        evidence.json("E-B7-policy-outcomes", evaluations);
        evidence.json("E-B8-audit-trail", Map.of("verification", verification, "events", audit.chain(runId).stream()
                .map(e -> Map.of("action", e.getAction(), "target", e.getTarget(), "actorId", e.getActorId()))
                .collect(Collectors.toList())));
        evidence.markdown("E-B9-final-summary", HttpDriver.content(artifacts.get("FINAL_SUMMARY")).asString());
    }
}
