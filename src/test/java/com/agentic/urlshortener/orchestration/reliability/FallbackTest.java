package com.agentic.urlshortener.orchestration.reliability;

import static com.agentic.urlshortener.orchestration.domain.StageType.DOCUMENTATION;
import static com.agentic.urlshortener.orchestration.domain.StageType.FINAL_SUMMARY;
import static com.agentic.urlshortener.orchestration.domain.StageType.RELEASE_APPROVAL;
import static com.agentic.urlshortener.orchestration.domain.StageType.TESTING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.agentic.urlshortener.orchestration.agent.AgentRegistry;
import com.agentic.urlshortener.orchestration.agent.DocumentationAgent;
import com.agentic.urlshortener.orchestration.agent.StageResult;
import com.agentic.urlshortener.orchestration.domain.AttemptOutcome;
import com.agentic.urlshortener.orchestration.domain.Decision;
import com.agentic.urlshortener.orchestration.domain.DecisionType;
import com.agentic.urlshortener.orchestration.domain.FailureClass;
import com.agentic.urlshortener.orchestration.domain.FailureEvent;
import com.agentic.urlshortener.orchestration.domain.RunStatus;
import com.agentic.urlshortener.orchestration.domain.StageAttempt;
import com.agentic.urlshortener.orchestration.domain.StageStatus;
import com.agentic.urlshortener.orchestration.domain.StageType;
import com.agentic.urlshortener.orchestration.engine.ArtifactStore;
import com.agentic.urlshortener.orchestration.repository.DecisionRepository;
import com.agentic.urlshortener.orchestration.repository.FailureEventRepository;
import com.agentic.urlshortener.orchestration.repository.StageAttemptRepository;
import com.agentic.urlshortener.orchestration.repository.StageNodeRepository;
import com.agentic.urlshortener.orchestration.repository.WorkflowRunRepository;
import com.agentic.urlshortener.orchestration.service.WorkflowService;
import com.agentic.urlshortener.support.GovernanceHarness;
import com.agentic.urlshortener.support.IntegrationTest;
import com.agentic.urlshortener.support.ScriptedAgent;
import com.agentic.urlshortener.support.Tokens;

/**
 * T069 (FR-REL-03, RDR-02): after a permanent failure or exhausted retries, a stage with a fallback
 * agent runs it once; the result is marked degraded and the switch is a recorded decision.
 * Verification stages have no fallback: they never degrade.
 */
@IntegrationTest
@Import(ScriptedAgent.Config.class)
@TestPropertySource(properties = "app.orchestration.stages.defaults.initial-backoff=PT0.05S")
@Tag("FR-REL-03")
@Tag("FR-RDY-01")
@Tag("RDR-02")
class FallbackTest {

    @Autowired private MockMvc mvc;
    @Autowired private WorkflowService workflows;
    @Autowired private WorkflowRunRepository runs;
    @Autowired private StageNodeRepository nodes;
    @Autowired private StageAttemptRepository attempts;
    @Autowired private DecisionRepository decisions;
    @Autowired private FailureEventRepository failures;
    @Autowired private ArtifactStore artifacts;
    @Autowired private AgentRegistry registry;
    @Autowired private ScriptedAgent.Scripts scripts;

    private GovernanceHarness harness;

    @BeforeEach
    void setUp() {
        harness = new GovernanceHarness(mvc, workflows, runs, nodes);
        scripts.reset();
    }

    private List<StageAttempt> attemptsOf(UUID runId, StageType stage) {
        return attempts.findByRunIdOrderByStartedAtAsc(runId).stream().filter(a -> a.getStageKey() == stage).toList();
    }

    private List<Decision> fallbackDecisions(UUID runId) {
        return decisions.findByRunIdOrderByCreatedAtAsc(runId).stream()
                .filter(d -> d.getDecisionType() == DecisionType.FALLBACK_USED).toList();
    }

    @Test
    void onlyDocumentationTheFinalSummaryAndImpactAnalysisHaveFallbacks() {
        assertThat(registry.fallback(DOCUMENTATION)).isPresent();
        assertThat(registry.fallback(FINAL_SUMMARY)).isPresent();
        assertThat(registry.fallback(StageType.IMPACT_ANALYSIS)).isPresent();
        for (StageType verification : List.of(TESTING, StageType.SECURITY_VERIFICATION, StageType.REGRESSION_TESTING,
                StageType.VALIDATION, StageType.COMPLIANCE_EVALUATION, StageType.RELEASE)) {
            assertThat(registry.fallback(verification)).as("fallback for %s", verification).isEmpty();
        }
    }

    @Test
    void aPermanentDocumentationFailureRunsTheTemplateFallbackAndDegradesTheStage() {
        scripts.set(DOCUMENTATION, context -> new StageResult.Failed(FailureClass.PERMANENT, "documentation generator crashed"));
        UUID runId = harness.submit(GovernanceHarness.ALICE);
        harness.awaitGate(runId, RELEASE_APPROVAL);

        List<StageAttempt> documentation = attemptsOf(runId, DOCUMENTATION);
        assertThat(documentation).extracting(StageAttempt::getOutcome)
                .containsExactly(AttemptOutcome.FAILED_PERMANENT, AttemptOutcome.SUCCEEDED);
        assertThat(documentation.get(1).isFallback()).isTrue();
        assertThat(documentation.get(1).getAgentId()).isEqualTo(registry.fallback(DOCUMENTATION).orElseThrow().agentId());
        assertThat(harness.node(runId, DOCUMENTATION).getStatus()).isEqualTo(StageStatus.SUCCEEDED);
        assertThat(harness.node(runId, DOCUMENTATION).isDegraded()).isTrue();
        assertThat(DocumentationAgent.isDegraded(artifacts.current(runId).get("DOCUMENTATION").getContent())).isTrue();

        assertThat(fallbackDecisions(runId)).singleElement().satisfies(d -> {
            assertThat(d.getStageKey()).isEqualTo(DOCUMENTATION);
            assertThat(d.getRationale()).contains("documentation generator crashed");
        });
        FailureEvent event = failures.findByRunId(runId).stream().filter(f -> f.getStageKey() == DOCUMENTATION).findFirst().orElseThrow();
        assertThat(event.getStatus()).isEqualTo(FailureEvent.RECOVERED);
        assertThat(event.getMechanism()).isEqualTo("FALLBACK");
    }

    @Test
    void exhaustedRetriesSwitchToTheFallbackAfterTheLastAttempt() {
        scripts.set(DOCUMENTATION, context -> new StageResult.Failed(FailureClass.TRANSIENT, "documentation store unavailable"));
        UUID runId = harness.submit(GovernanceHarness.ALICE);
        harness.awaitGate(runId, RELEASE_APPROVAL);

        assertThat(attemptsOf(runId, DOCUMENTATION)).extracting(StageAttempt::getOutcome).containsExactly(
                AttemptOutcome.FAILED_TRANSIENT, AttemptOutcome.FAILED_TRANSIENT, AttemptOutcome.FAILED_TRANSIENT,
                AttemptOutcome.SUCCEEDED);
        assertThat(fallbackDecisions(runId)).singleElement()
                .satisfies(d -> assertThat(d.getRationale()).contains("retries exhausted after 3 attempts"));
    }

    @Test
    void aVerificationStageNeverDegrades() {
        scripts.set(TESTING, context -> new StageResult.Failed(FailureClass.PERMANENT, "acceptance probe failed"));
        UUID runId = harness.submit(GovernanceHarness.ALICE);
        harness.awaitStatus(runId, RunStatus.SAFE_STOPPED);

        assertThat(attemptsOf(runId, TESTING)).extracting(StageAttempt::getOutcome).containsExactly(AttemptOutcome.FAILED_PERMANENT);
        assertThat(harness.node(runId, TESTING).getStatus()).isEqualTo(StageStatus.FAILED);
        assertThat(fallbackDecisions(runId)).isEmpty();
    }

    @Test
    void aFailedFinalSummaryFallsBackToTheMinimalSummaryAndTheRunCompletes() throws Exception {
        scripts.set(FINAL_SUMMARY, context -> new StageResult.Failed(FailureClass.PERMANENT, "summary renderer failed"));
        UUID runId = harness.submit(GovernanceHarness.ALICE);
        harness.awaitGate(runId, RELEASE_APPROVAL);
        harness.decide(runId, RELEASE_APPROVAL, Tokens.RELEASE_OWNER, "APPROVE").andExpect(status().isOk());
        harness.awaitStatus(runId, RunStatus.COMPLETED);

        assertThat(harness.node(runId, FINAL_SUMMARY).isDegraded()).isTrue();
        assertThat(artifacts.current(runId).get("FINAL_SUMMARY").getContent()).contains("degraded").contains(runId.toString());
    }
}
