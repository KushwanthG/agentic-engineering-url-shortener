package com.agentic.urlshortener.orchestration.governance;

import static com.agentic.urlshortener.orchestration.domain.StageType.CLARIFICATION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.agentic.urlshortener.common.util.CanonicalJson;
import com.agentic.urlshortener.contract.OpenApiContract;
import com.agentic.urlshortener.orchestration.domain.AwaitingType;
import com.agentic.urlshortener.orchestration.domain.Decision;
import com.agentic.urlshortener.orchestration.domain.DecisionType;
import com.agentic.urlshortener.orchestration.domain.RequirementVersion;
import com.agentic.urlshortener.orchestration.domain.RunStatus;
import com.agentic.urlshortener.orchestration.domain.StageAttempt;
import com.agentic.urlshortener.orchestration.domain.StageStatus;
import com.agentic.urlshortener.orchestration.domain.StageType;
import com.agentic.urlshortener.orchestration.dto.RequirementSubmission;
import com.agentic.urlshortener.orchestration.engine.ArtifactStore;
import com.agentic.urlshortener.orchestration.repository.DecisionRepository;
import com.agentic.urlshortener.orchestration.repository.RequirementVersionRepository;
import com.agentic.urlshortener.orchestration.repository.StageAttemptRepository;
import com.agentic.urlshortener.orchestration.repository.StageNodeRepository;
import com.agentic.urlshortener.orchestration.repository.WorkflowRunRepository;
import com.agentic.urlshortener.orchestration.service.WorkflowService;
import com.agentic.urlshortener.support.ArtifactSchemas;
import com.agentic.urlshortener.support.GovernanceHarness;
import com.agentic.urlshortener.support.IntegrationTest;
import com.agentic.urlshortener.support.Tokens;

import tools.jackson.databind.JsonNode;

/**
 * T091 (FR-GOV-08, FR-GOV-02, FR-REL-06, SCN-C): an ambiguous requirement suspends the run at the
 * clarification gate with questions, options, and impacts, and nothing downstream starts. An
 * approver's answers are recorded one decision per answer and produce requirement version 2 with
 * derived acceptance criteria, scope exclusions, and the decided parameter; the run resumes at
 * requirement analysis. Answers that resolve nothing lead to another round; more than three rounds
 * safe-stop the run. The answers are simulated human input of the demo approver {@code bob}.
 */
@IntegrationTest
@Tag("FR-GOV-08")
@Tag("FR-GOV-02")
@Tag("FR-REL-06")
@Tag("SCN-C")
class ClarificationFlowTest {

    /** Reference decisions D1..D4 of spec SCN-C, per ambiguity type (option ids of the ambiguity lexicon). */
    static final Map<String, String> REFERENCE_DECISIONS = Map.of("VAGUE_TERM", "B", "UNDEFINED_CONCEPT", "A", "CONFLICT", "A",
            "UNBOUNDED_SCOPE", "A", "MISSING_ACCEPTANCE_CRITERIA", "A", "UNSPECIFIED_TYPE", "B", "EXISTING_DATA", "A");

    private static final Set<StageType> DOWNSTREAM = EnumSet.of(StageType.DECOMPOSITION, StageType.THREAT_ASSESSMENT,
            StageType.IMPACT_ANALYSIS, StageType.DESIGN, StageType.IMPLEMENTATION);

    @Autowired private MockMvc mvc;
    @Autowired private WorkflowService workflows;
    @Autowired private WorkflowRunRepository runs;
    @Autowired private StageNodeRepository nodes;
    @Autowired private StageAttemptRepository attempts;
    @Autowired private DecisionRepository decisions;
    @Autowired private RequirementVersionRepository requirements;
    @Autowired private ArtifactStore artifacts;

    private GovernanceHarness harness() {
        return new GovernanceHarness(mvc, workflows, runs, nodes);
    }

    private UUID ambiguousRun() {
        UUID runId = workflows.submit(new RequirementSubmission("AMB-001", "Better link expiry.",
                "Links should expire after a while so old links don't pile up, but premium users' links should never expire. "
                        + "Also make the analytics better.", "UNSPECIFIED", List.of(), List.of(), null), GovernanceHarness.ALICE);
        awaitClarification(runId);
        return runId;
    }

    private void awaitClarification(UUID runId) {
        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(50)).until(() -> {
            assertThat(runs.findById(runId).orElseThrow().getStatus().isTerminal()).as("run ended early").isFalse();
            return nodes.findByRunIdAndStageKey(runId, CLARIFICATION).orElseThrow().getStatus() == StageStatus.AWAITING_DECISION;
        });
    }

    /** The open questions with the ambiguity type each one asks about. */
    private Map<String, String> openQuestions(UUID runId) {
        JsonNode request = CanonicalJson.parse(artifacts.current(runId).get("CLARIFICATION_REQUEST").getContent());
        Map<String, String> typeById = new LinkedHashMap<>();
        CanonicalJson.parse(artifacts.current(runId).get("NORMALIZED_REQUIREMENT").getContent()).path("ambiguities")
                .forEach(a -> typeById.put(a.path("id").asString(), a.path("type").asString()));
        Map<String, String> questions = new LinkedHashMap<>();
        request.path("questions").forEach(q -> questions.put(q.path("questionId").asString(),
                typeById.getOrDefault(q.path("ambiguityId").asString(), q.path("ambiguityId").asString())));
        return questions;
    }

    private String answers(Map<String, String> questions, Map<String, String> optionByType) {
        List<Map<String, Object>> answers = new ArrayList<>();
        questions.forEach((id, type) -> {
            Map<String, Object> answer = new LinkedHashMap<>();
            answer.put("questionId", id);
            String option = optionByType.get(type);
            if (option != null && option.startsWith("text:")) {
                answer.put("answer", option.substring(5));
            } else {
                answer.put("optionId", option);
            }
            answers.add(answer);
        });
        return CanonicalJson.write(Map.of("answers", answers, "rationale", "reference decisions D1-D4" + GovernanceHarness.SIMULATED));
    }

    private ResultActions answer(UUID runId, String token, String body) throws Exception {
        return mvc.perform(post("/api/v1/workflows/" + runId + "/clarifications").header(HttpHeaders.AUTHORIZATION, Tokens.bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private List<StageAttempt> attemptsOf(UUID runId, StageType stage) {
        return attempts.findByRunIdOrderByStartedAtAsc(runId).stream().filter(a -> a.getStageKey() == stage).toList();
    }

    @Test
    void anAmbiguousRequirementSuspendsTheRunWithQuestionsAndNothingDownstreamStarts() throws Exception {
        UUID runId = ambiguousRun();

        assertThat(harness().node(runId, CLARIFICATION).getAwaiting()).isEqualTo(AwaitingType.CLARIFICATION);
        harness().awaitStatus(runId, RunStatus.AWAITING_HUMAN);
        String request = artifacts.current(runId).get("CLARIFICATION_REQUEST").getContent();
        ArtifactSchemas.assertValid("clarification-request", request);
        CanonicalJson.parse(request).path("questions").forEach(q -> {
            assertThat(q.path("options").size()).isPositive();
            q.path("options").forEach(o -> assertThat(o.path("impact").asString()).isNotBlank());
        });
        assertThat(openQuestions(runId).values()).contains("VAGUE_TERM", "UNDEFINED_CONCEPT", "CONFLICT", "UNBOUNDED_SCOPE",
                "MISSING_ACCEPTANCE_CRITERIA");
        Thread.sleep(300);
        assertThat(attempts.findByRunIdOrderByStartedAtAsc(runId)).noneMatch(a -> DOWNSTREAM.contains(a.getStageKey()));

        harness().decide(runId, CLARIFICATION, Tokens.APPROVER, "APPROVE").andExpect(status().isConflict());
        answer(runId, Tokens.REQUESTER, answers(openQuestions(runId), REFERENCE_DECISIONS)).andExpect(status().isForbidden());
    }

    @Test
    void answersBecomeDecisionsAndRequirementVersionTwoAndTheRunResumesAtAnalysis() throws Exception {
        UUID runId = ambiguousRun();
        Map<String, String> questions = openQuestions(runId);

        MvcResult result = answer(runId, Tokens.APPROVER, answers(questions, REFERENCE_DECISIONS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requirementVersion").value(2))
                .andExpect(jsonPath("$.planVersion").value(2))
                .andReturn();
        OpenApiContract.assertResponseMatches("POST", "/api/v1/workflows/" + runId + "/clarifications", result);

        List<Decision> answersRecorded = decisions.findByRunIdOrderByCreatedAtAsc(runId).stream()
                .filter(d -> d.getDecisionType() == DecisionType.CLARIFICATION_ANSWER).toList();
        assertThat(answersRecorded).hasSize(questions.size()).allSatisfy(d -> {
            assertThat(d.getActorId()).isEqualTo("bob");
            assertThat(d.getActorRole()).isEqualTo("APPROVER");
        });

        RequirementVersion v2 = requirements.findByRunIdOrderByVersionAsc(runId).get(1);
        assertThat(v2.getVersion()).isEqualTo(2);
        assertThat(v2.getSource()).isEqualTo("CLARIFIED");
        ArtifactSchemas.assertValid("requirement-document", v2.getContent());
        JsonNode document = CanonicalJson.parse(v2.getContent());
        assertThat(document.path("type").asString()).isEqualTo("CHANGE_TO_EXISTING");
        assertThat(document.path("parameters").path("defaultExpiryDays").asInt()).isEqualTo(30);
        assertThat(document.path("acceptanceCriteria").size()).isGreaterThanOrEqualTo(3);
        document.path("acceptanceCriteria").forEach(ac -> assertThat(ac.path("origin").asString()).isEqualTo("DERIVED_FROM_DECISION"));
        assertThat(document.path("acceptanceCriteria").toString()).contains("30 days");
        assertThat(document.path("scopeExclusions").toString()).contains("premium users").contains("analytics");
        assertThat(document.path("clarifications").size()).isEqualTo(questions.size());

        await().atMost(Duration.ofSeconds(30)).until(() -> harness().node(runId, CLARIFICATION).getStatus() == StageStatus.SKIPPED);
        assertThat(attemptsOf(runId, StageType.REQUIREMENT_INGESTION)).hasSize(1);
        assertThat(attemptsOf(runId, StageType.REQUIREMENT_ANALYSIS)).hasSize(2);
        JsonNode reanalyzed = CanonicalJson.parse(artifacts.current(runId).get("NORMALIZED_REQUIREMENT").getContent());
        assertThat(reanalyzed.path("clarificationRequired").asBoolean()).isFalse();
        assertThat(reanalyzed.path("classification").asString()).isEqualTo("CHANGE_TO_EXISTING");
        assertThat(nodes.findByRunIdAndStageKey(runId, StageType.IMPACT_ANALYSIS)).isPresent();
        assertThat(nodes.findByRunIdAndStageKey(runId, StageType.REGRESSION_TESTING)).isPresent();
    }

    @Test
    void incompleteOrInvalidAnswersAreRejected() throws Exception {
        UUID runId = ambiguousRun();
        Map<String, String> questions = openQuestions(runId);
        Map<String, String> firstOnly = new LinkedHashMap<>();
        Map.Entry<String, String> first = questions.entrySet().iterator().next();
        firstOnly.put(first.getKey(), first.getValue());

        answer(runId, Tokens.APPROVER, answers(firstOnly, REFERENCE_DECISIONS))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        Map<String, String> wrongOption = new LinkedHashMap<>(REFERENCE_DECISIONS);
        wrongOption.put("VAGUE_TERM", "Z");
        answer(runId, Tokens.APPROVER, answers(questions, wrongOption))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        assertThat(requirements.findByRunIdOrderByVersionAsc(runId)).hasSize(1);
    }

    @Test
    void answersThatResolveNothingLeadToAnotherRoundAndMoreThanThreeRoundsSafeStop() throws Exception {
        UUID runId = ambiguousRun();
        Map<String, String> vague = new LinkedHashMap<>(REFERENCE_DECISIONS);
        vague.put("VAGUE_TERM", "text:whenever it seems right");

        for (int round = 1; round <= 3; round++) {
            Map<String, String> questions = openQuestions(runId);
            assertThat(questions.values()).contains("VAGUE_TERM");
            answer(runId, Tokens.APPROVER, answers(questions, vague)).andExpect(status().isOk());
            await().atMost(Duration.ofSeconds(30)).until(() -> {
                var status = runs.findById(runId).orElseThrow().getStatus();
                return status.isTerminal() || (harness().node(runId, CLARIFICATION).getStatus() == StageStatus.AWAITING_DECISION
                        && harness().node(runId, StageType.REQUIREMENT_ANALYSIS).getStatus() == StageStatus.SUCCEEDED
                        && attemptsOf(runId, StageType.REQUIREMENT_ANALYSIS).size() > 1);
            });
        }

        harness().awaitStatus(runId, RunStatus.SAFE_STOPPED);
        assertThat(runs.findById(runId).orElseThrow().getTerminalReason()).contains("clarification").contains("3");
        assertThat(attempts.findByRunIdOrderByStartedAtAsc(runId)).noneMatch(a -> DOWNSTREAM.contains(a.getStageKey()));
    }
}
