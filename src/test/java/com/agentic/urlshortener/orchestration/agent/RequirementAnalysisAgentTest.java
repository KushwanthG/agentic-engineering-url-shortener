package com.agentic.urlshortener.orchestration.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.agentic.urlshortener.common.util.CanonicalJson;
import com.agentic.urlshortener.orchestration.domain.FailureClass;
import com.agentic.urlshortener.orchestration.domain.StageType;
import com.agentic.urlshortener.orchestration.knowledge.AmbiguityLexicon;
import com.agentic.urlshortener.orchestration.knowledge.CapabilityCatalog;
import com.agentic.urlshortener.support.ArtifactSchemas;
import com.agentic.urlshortener.support.RequirementFixtures;

import tools.jackson.databind.JsonNode;

/** T046: requirement ingestion and analysis (quality checks, classification, ambiguity) for the three scenarios. */
@Tag("FR-ORC-01")
@Tag("FR-ORC-13")
@Tag("SCN-A")
@Tag("SCN-C")
class RequirementAnalysisAgentTest {

    private final RequirementIngestionAgent ingestion = new RequirementIngestionAgent();
    private final RequirementAnalysisAgent analysis = new RequirementAnalysisAgent(CapabilityCatalog.load(), AmbiguityLexicon.load());

    private JsonNode analyze(String requirement) {
        StageResult ingested = ingestion.execute(RequirementFixtures.context(StageType.REQUIREMENT_INGESTION, requirement, Map.of(), null));
        assertThat(ingested).isInstanceOf(StageResult.Succeeded.class);
        String requirementArtifact = ((StageResult.Succeeded) ingested).artifacts().getFirst().content();
        ArtifactSchemas.assertValid("requirement-document", requirementArtifact);

        StageResult result = analysis.execute(RequirementFixtures.context(StageType.REQUIREMENT_ANALYSIS, requirement,
                Map.of("REQUIREMENT", requirementArtifact), null));
        assertThat(result).isInstanceOf(StageResult.Succeeded.class);
        List<ArtifactDraft> drafts = ((StageResult.Succeeded) result).artifacts();
        String normalized = drafts.stream().filter(d -> d.type().equals("NORMALIZED_REQUIREMENT")).findFirst().orElseThrow().content();
        ArtifactSchemas.assertValid("normalized-requirement", normalized);
        drafts.stream().filter(d -> d.type().equals("CLARIFICATION_REQUEST"))
                .forEach(d -> ArtifactSchemas.assertValid("clarification-request", d.content()));
        return CanonicalJson.parse(normalized);
    }

    private static String check(JsonNode normalized, String name) {
        for (JsonNode check : normalized.path("qualityChecks")) {
            if (check.path("check").asString().equals(name)) {
                return check.path("result").asString();
            }
        }
        throw new AssertionError("no check " + name);
    }

    @Test
    void greenfieldRequirementPassesEveryQualityCheckWithoutClarification() {
        JsonNode normalized = analyze(RequirementFixtures.gf001());

        assertThat(normalized.path("classification").asString()).isEqualTo("NEW_CAPABILITY");
        assertThat(normalized.path("capabilities").findValuesAsString("id")).contains("custom-alias");
        for (String name : List.of("COMPLETENESS", "CONSISTENCY", "TESTABILITY", "POLICY", "ARCHITECTURE_BOUNDARY")) {
            assertThat(check(normalized, name)).as(name).isEqualTo("PASS");
        }
        assertThat(normalized.path("ambiguities").size()).isZero();
        assertThat(normalized.path("clarificationRequired").asBoolean()).isFalse();
        assertThat(normalized.path("clarificationRationale").asString()).contains("quality checks passed").contains("custom-alias");
        assertThat(normalized.path("acceptanceCriteria").size()).isEqualTo(6);
        assertThat(normalized.path("acceptanceCriteria").get(0).path("testable").asBoolean()).isTrue();
        assertThat(normalized.path("acceptanceCriteria").get(0).path("then").asString()).contains("spring-sale");
    }

    @Test
    void brownfieldRequirementIsAChangeToExistingClickLimit() {
        JsonNode normalized = analyze(RequirementFixtures.bf001());
        assertThat(normalized.path("classification").asString()).isEqualTo("CHANGE_TO_EXISTING");
        assertThat(normalized.path("capabilities").findValuesAsString("id")).contains("click-limit").doesNotContain("default-expiry");
        assertThat(normalized.path("clarificationRequired").asBoolean()).isFalse();
    }

    @Test
    void ambiguousRequirementYieldsTheSixExpectedAmbiguities() {
        JsonNode normalized = analyze(RequirementFixtures.amb001());

        assertThat(normalized.path("classification").asString()).isEqualTo("UNDETERMINED");
        Map<String, String> severities = new java.util.HashMap<>();
        Map<String, Boolean> blocking = new java.util.HashMap<>();
        for (JsonNode ambiguity : normalized.path("ambiguities")) {
            severities.put(ambiguity.path("type").asString(), ambiguity.path("severity").asString());
            blocking.put(ambiguity.path("type").asString(), ambiguity.path("blocking").asBoolean());
        }
        assertThat(severities).containsExactlyInAnyOrderEntriesOf(Map.of(
                "VAGUE_TERM", "HIGH", "UNDEFINED_CONCEPT", "HIGH", "CONFLICT", "HIGH", "UNBOUNDED_SCOPE", "MEDIUM",
                "MISSING_ACCEPTANCE_CRITERIA", "HIGH", "UNSPECIFIED_TYPE", "LOW"));
        assertThat(blocking).containsEntry("UNBOUNDED_SCOPE", true).containsEntry("UNSPECIFIED_TYPE", false);
        assertThat(normalized.path("clarificationRequired").asBoolean()).isTrue();
        assertThat(check(normalized, "COMPLETENESS")).isEqualTo("FAIL");
        assertThat(check(normalized, "CONSISTENCY")).isEqualTo("FAIL");
    }

    @Test
    void ambiguousRequirementProducesAClarificationRequestWithOptions() {
        String requirement = RequirementFixtures.amb001();
        String ingested = ((StageResult.Succeeded) ingestion.execute(
                RequirementFixtures.context(StageType.REQUIREMENT_INGESTION, requirement, Map.of(), null))).artifacts().getFirst().content();
        StageResult.Succeeded result = (StageResult.Succeeded) analysis.execute(
                RequirementFixtures.context(StageType.REQUIREMENT_ANALYSIS, requirement, Map.of("REQUIREMENT", ingested), null));
        JsonNode request = CanonicalJson.parse(result.artifacts().stream()
                .filter(d -> d.type().equals("CLARIFICATION_REQUEST")).findFirst().orElseThrow().content());
        assertThat(request.path("round").asInt()).isEqualTo(1);
        assertThat(request.path("questions").size()).isGreaterThanOrEqualTo(6);
        for (JsonNode question : request.path("questions")) {
            assertThat(question.path("options").size()).isPositive();
        }
    }

    @Test
    void unknownCapabilityFailsTheArchitectureBoundaryCheck() {
        JsonNode normalized = analyze(RequirementFixtures.document("OUT-1", "Payroll export", "NEW_CAPABILITY",
                "Integrate with the payroll system and send monthly invoices.",
                List.of("Given a month has ended, when the export runs, then an invoice is sent."), List.of()));
        assertThat(check(normalized, "ARCHITECTURE_BOUNDARY")).isEqualTo("FAIL");
        assertThat(normalized.path("ambiguities").findValuesAsString("type")).contains("UNKNOWN_CAPABILITY");
        assertThat(normalized.path("clarificationRequired").asBoolean()).isTrue();
    }

    @Test
    void ingestionRejectsADocumentWithoutTitle() {
        StageResult result = ingestion.execute(RequirementFixtures.context(StageType.REQUIREMENT_INGESTION,
                "{\"narrative\":\"x\",\"type\":\"UNSPECIFIED\"}", Map.of(), null));
        assertThat(result).isInstanceOfSatisfying(StageResult.Failed.class, f -> assertThat(f.failureClass()).isEqualTo(FailureClass.PERMANENT));
    }
}
