package com.agentic.urlshortener.orchestration.governance;

import static com.agentic.urlshortener.orchestration.domain.StageType.ARCHITECTURE_APPROVAL;
import static com.agentic.urlshortener.orchestration.domain.StageType.DESIGN;
import static com.agentic.urlshortener.orchestration.domain.StageType.DOCUMENTATION;
import static com.agentic.urlshortener.orchestration.domain.StageType.IMPLEMENTATION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;

import com.agentic.urlshortener.orchestration.agent.ArtifactDraft;
import com.agentic.urlshortener.orchestration.agent.StageResult;
import com.agentic.urlshortener.orchestration.domain.Classification;
import com.agentic.urlshortener.orchestration.domain.RunStatus;
import com.agentic.urlshortener.orchestration.domain.StageStatus;
import com.agentic.urlshortener.orchestration.planning.PlanFactory;
import com.agentic.urlshortener.orchestration.planning.PlanGraph;
import com.agentic.urlshortener.orchestration.planning.StageSpec;
import com.agentic.urlshortener.orchestration.repository.StageAttemptRepository;
import com.agentic.urlshortener.orchestration.repository.StageNodeRepository;
import com.agentic.urlshortener.orchestration.repository.WorkflowRunRepository;
import com.agentic.urlshortener.orchestration.service.WorkflowService;
import com.agentic.urlshortener.support.GovernanceHarness;
import com.agentic.urlshortener.support.IntegrationTest;
import com.agentic.urlshortener.support.ScriptedAgent;

/**
 * T063 (FR-GOV-02): while a gate waits, stages that depend on it do not start and stages that do not
 * depend on it continue. The standard plans have no stage beside a gate, so this test uses a plan in
 * which DOCUMENTATION depends on DESIGN only.
 */
@IntegrationTest
@Import({ ScriptedAgent.Config.class, WaitingGateIndependenceTest.IndependentDocumentationPlan.class })
@Tag("FR-GOV-02")
class WaitingGateIndependenceTest {

    @Autowired private MockMvc mvc;
    @Autowired private WorkflowService workflows;
    @Autowired private WorkflowRunRepository runs;
    @Autowired private StageNodeRepository nodes;
    @Autowired private StageAttemptRepository attempts;
    @Autowired private ScriptedAgent.Scripts scripts;

    private GovernanceHarness harness;

    @BeforeEach
    void materialDesignAndSlowDocumentation() {
        harness = new GovernanceHarness(mvc, workflows, runs, nodes);
        scripts.reset();
        scripts.set(DESIGN, context -> new StageResult.Succeeded(List.of(ArtifactDraft.json("DESIGN",
                "{\"materialChange\":true,\"materialReasons\":[\"public API change\"]}")), "scripted material design"));
        scripts.sleepThenSucceed(DOCUMENTATION, 300);
    }

    @Test
    void anIndependentStageFinishesWhileTheGateWaitsAndDependentsDoNotStart() {
        UUID runId = harness.submit(GovernanceHarness.ALICE);
        harness.awaitGate(runId, ARCHITECTURE_APPROVAL);

        await().atMost(Duration.ofSeconds(20)).until(() -> harness.node(runId, DOCUMENTATION).getStatus() == StageStatus.SUCCEEDED);
        harness.awaitStatus(runId, RunStatus.AWAITING_HUMAN);

        assertThat(harness.node(runId, ARCHITECTURE_APPROVAL).getStatus()).isEqualTo(StageStatus.AWAITING_DECISION);
        assertThat(harness.node(runId, IMPLEMENTATION).getStatus()).isEqualTo(StageStatus.PENDING);
        assertThat(attempts.findByRunIdOrderByStartedAtAsc(runId)).noneMatch(a -> a.getStageKey() == IMPLEMENTATION);
    }

    /** The standard plan with DOCUMENTATION depending on DESIGN instead of the architecture gate. */
    @TestConfiguration
    static class IndependentDocumentationPlan {

        @Bean
        @Primary
        PlanFactory independentDocumentationPlanFactory() {
            return new PlanFactory() {
                @Override
                public PlanGraph create(Classification classification) {
                    return new PlanGraph(super.create(classification).stages().stream()
                            .map(spec -> spec.key() == DOCUMENTATION ? StageSpec.of(DOCUMENTATION, List.of(DESIGN)) : spec)
                            .toList());
                }
            };
        }
    }
}
