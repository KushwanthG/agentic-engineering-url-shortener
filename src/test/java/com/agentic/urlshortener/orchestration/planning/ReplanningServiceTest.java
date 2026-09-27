package com.agentic.urlshortener.orchestration.planning;

import static com.agentic.urlshortener.orchestration.domain.StageType.ARCHITECTURE_APPROVAL;
import static com.agentic.urlshortener.orchestration.domain.StageType.DECOMPOSITION;
import static com.agentic.urlshortener.orchestration.domain.StageType.DESIGN;
import static com.agentic.urlshortener.orchestration.domain.StageType.IMPLEMENTATION;
import static com.agentic.urlshortener.orchestration.domain.StageType.RELEASE_APPROVAL;
import static com.agentic.urlshortener.orchestration.domain.StageType.REQUIREMENT_ANALYSIS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.agentic.urlshortener.common.exception.ApiException;
import com.agentic.urlshortener.common.util.CanonicalJson;
import com.agentic.urlshortener.orchestration.agent.ArtifactDraft;
import com.agentic.urlshortener.orchestration.agent.StageResult;
import com.agentic.urlshortener.orchestration.domain.ActorType;
import com.agentic.urlshortener.orchestration.domain.AttemptOutcome;
import com.agentic.urlshortener.orchestration.domain.Classification;
import com.agentic.urlshortener.orchestration.domain.Decision;
import com.agentic.urlshortener.orchestration.domain.PlanVersion;
import com.agentic.urlshortener.orchestration.domain.StageAttempt;
import com.agentic.urlshortener.orchestration.domain.StageStatus;
import com.agentic.urlshortener.orchestration.domain.StageType;
import com.agentic.urlshortener.orchestration.engine.ArtifactStore;
import com.agentic.urlshortener.orchestration.engine.RunCoordinator;
import com.agentic.urlshortener.orchestration.engine.RunLocks;
import com.agentic.urlshortener.orchestration.reliability.SafeStopService;
import com.agentic.urlshortener.orchestration.repository.DecisionRepository;
import com.agentic.urlshortener.orchestration.repository.PlanVersionRepository;
import com.agentic.urlshortener.orchestration.repository.StageAttemptRepository;
import com.agentic.urlshortener.orchestration.repository.StageNodeRepository;
import com.agentic.urlshortener.orchestration.repository.WorkflowRunRepository;
import com.agentic.urlshortener.orchestration.service.WorkflowService;
import com.agentic.urlshortener.support.GovernanceHarness;
import com.agentic.urlshortener.support.IntegrationTest;
import com.agentic.urlshortener.support.ScriptedAgent;
import com.agentic.urlshortener.support.Tokens;

/**
 * T093 (FR-RPL-01, FR-RPL-02, FR-RPL-05, FR-GOV-05): re-planning re-opens the stage where changed
 * input enters and its downstream closure as a new generation with superseded artifacts, and records a
 * plan version with trigger and diff. A re-opened stage whose input fingerprint is unchanged is reused
 * without executing its agent; a changed one runs again. An approval stays valid only while its bound
 * artifacts are unchanged. A terminal run cannot be re-planned.
 */
@IntegrationTest
@Import(ScriptedAgent.Config.class)
@Tag("FR-RPL-01")
@Tag("FR-RPL-02")
@Tag("FR-RPL-05")
@Tag("FR-GOV-05")
class ReplanningServiceTest {

    @Autowired private MockMvc mvc;
    @Autowired private WorkflowService workflows;
    @Autowired private WorkflowRunRepository runs;
    @Autowired private StageNodeRepository nodes;
    @Autowired private StageAttemptRepository attempts;
    @Autowired private PlanVersionRepository plans;
    @Autowired private DecisionRepository decisions;
    @Autowired private ArtifactStore artifacts;
    @Autowired private ReplanningService replanning;
    @Autowired private RunCoordinator coordinator;
    @Autowired private RunLocks locks;
    @Autowired private SafeStopService safeStops;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private ScriptedAgent.Scripts scripts;

    private final AtomicInteger designCalls = new AtomicInteger();
    private GovernanceHarness harness;

    @BeforeEach
    void setUp() {
        harness = new GovernanceHarness(mvc, workflows, runs, nodes);
        scripts.reset();
        designCalls.set(0);
        // Every agent's output depends on its input, so a changed input propagates and an unchanged one does not.
        for (StageType stage : List.of(REQUIREMENT_ANALYSIS, DECOMPOSITION, StageType.THREAT_ASSESSMENT)) {
            scripts.set(stage, context -> {
                String basis = context.inputs().values().stream().map(i -> i.fingerprint()).sorted().toList().toString();
                List<ArtifactDraft> drafts = stage.outputArtifactTypes().stream().map(type -> ArtifactDraft.json(type,
                        type.equals("NORMALIZED_REQUIREMENT")
                                ? "{\"clarificationRequired\":false,\"clarificationRationale\":\"scripted\",\"basis\":\"" + basis + "\"}"
                                : "{\"basis\":\"" + basis + "\"}")).toList();
                return new StageResult.Succeeded(drafts, "scripted");
            });
        }
        scripts.set(DESIGN, context -> {
            designCalls.incrementAndGet();
            String basis = context.inputJson("NORMALIZED_REQUIREMENT").path("basis").asString();
            return new StageResult.Succeeded(List.of(ArtifactDraft.json("DESIGN",
                    "{\"materialChange\":true,\"materialReasons\":[\"public API change\"],\"basis\":\"" + basis + "\"}")), "scripted");
        });
    }

    private UUID runAtReleaseApproval() throws Exception {
        UUID runId = harness.submit(GovernanceHarness.ALICE);
        harness.awaitGate(runId, ARCHITECTURE_APPROVAL);
        harness.decide(runId, ARCHITECTURE_APPROVAL, Tokens.APPROVER, "APPROVE").andExpect(status().isOk());
        harness.awaitGate(runId, RELEASE_APPROVAL);
        return runId;
    }

    private int replanFrom(UUID runId, StageType from) {
        return locks.withLock(runId, () -> new TransactionTemplate(transactionManager).execute(status ->
                replanning.replan(runId, Classification.NEW_CAPABILITY, from, "CHANGE_REQUEST", "test re-plan from " + from, "test",
                        Instant.now())));
    }

    private void changeRequirement(UUID runId) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> artifacts.store(runId, StageType.CLARIFICATION, 1, 0,
                "test", List.of(ArtifactDraft.json("REQUIREMENT", "{\"title\":\"changed requirement\"}")), Map.of(), Instant.now()));
    }

    private List<StageAttempt> attemptsOf(UUID runId, StageType stage) {
        return attempts.findByRunIdOrderByStartedAtAsc(runId).stream().filter(a -> a.getStageKey() == stage).toList();
    }

    private Decision architectureApproval(UUID runId) {
        return decisions.findByRunIdOrderByCreatedAtAsc(runId).stream().filter(d -> d.getStageKey() == ARCHITECTURE_APPROVAL).findFirst()
                .orElseThrow();
    }

    @Test
    void theDownstreamClosureIsReopenedWithANewPlanVersion() throws Exception {
        UUID runId = runAtReleaseApproval();

        int version = replanFrom(runId, DESIGN);

        assertThat(version).isEqualTo(2);
        assertThat(harness.node(runId, DESIGN).getGeneration()).isEqualTo(2);
        assertThat(harness.node(runId, IMPLEMENTATION).getGeneration()).isEqualTo(2);
        assertThat(harness.node(runId, RELEASE_APPROVAL).getStatus()).isEqualTo(StageStatus.PENDING);
        assertThat(harness.node(runId, DECOMPOSITION).getGeneration()).isEqualTo(1);
        assertThat(artifacts.current(runId)).doesNotContainKeys("DESIGN", "CHANGE_SET").containsKey("TASK_GRAPH");
        PlanVersion plan = plans.findByRunIdOrderByVersionAsc(runId).get(1);
        assertThat(plan.getTriggerType()).isEqualTo("CHANGE_REQUEST");
        assertThat(CanonicalJson.parse(plan.getDiff()).path("invalidated").toString()).contains("DESIGN", "IMPLEMENTATION", "RELEASE_APPROVAL");
    }

    @Test
    void unchangedInputsAreReusedWithoutRunningTheAgentAndTheApprovalCarriesOver() throws Exception {
        UUID runId = runAtReleaseApproval();
        int callsBefore = designCalls.get();

        replanFrom(runId, DESIGN);
        coordinator.advance(runId);
        harness.awaitGate(runId, RELEASE_APPROVAL);

        assertThat(designCalls.get()).as("DESIGN agent not executed again").isEqualTo(callsBefore);
        assertThat(attemptsOf(runId, DESIGN)).extracting(StageAttempt::getOutcome)
                .containsExactly(AttemptOutcome.SUCCEEDED, AttemptOutcome.REUSED);
        assertThat(attemptsOf(runId, IMPLEMENTATION)).extracting(StageAttempt::getOutcome)
                .containsExactly(AttemptOutcome.SUCCEEDED, AttemptOutcome.REUSED);
        assertThat(architectureApproval(runId).isValid()).as("bound design unchanged").isTrue();
        assertThat(harness.node(runId, ARCHITECTURE_APPROVAL).getStatus()).isEqualTo(StageStatus.SUCCEEDED);
    }

    @Test
    void changedInputsRunAgainAndAChangedBoundArtifactInvalidatesTheApproval() throws Exception {
        UUID runId = runAtReleaseApproval();
        int callsBefore = designCalls.get();

        replanFrom(runId, REQUIREMENT_ANALYSIS);
        // As clarification and change requests do: the changed requirement is recorded after re-planning.
        changeRequirement(runId);
        coordinator.advance(runId);
        harness.awaitGate(runId, ARCHITECTURE_APPROVAL);

        assertThat(attemptsOf(runId, REQUIREMENT_ANALYSIS)).extracting(StageAttempt::getOutcome)
                .containsExactly(AttemptOutcome.SUCCEEDED, AttemptOutcome.SUCCEEDED);
        assertThat(designCalls.get()).as("DESIGN ran again with the changed requirement").isEqualTo(callsBefore + 1);
        Decision approval = architectureApproval(runId);
        assertThat(approval.isValid()).isFalse();
        assertThat(approval.getInvalidatedReason()).contains("DESIGN");
    }

    @Test
    void aTerminalRunCannotBeReplanned() throws Exception {
        UUID runId = runAtReleaseApproval();
        safeStops.stop(runId, SafeStopService.Trigger.OPERATOR_REQUEST, "stopped for the test", ActorType.HUMAN, "carol");

        assertThatThrownBy(() -> replanFrom(runId, DESIGN)).isInstanceOf(ApiException.class).hasMessageContaining("SAFE_STOPPED");
    }
}
