package com.agentic.urlshortener.orchestration.reliability;

import static com.agentic.urlshortener.orchestration.domain.StageType.REQUIREMENT_INGESTION;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.agentic.urlshortener.common.util.CanonicalJson;
import com.agentic.urlshortener.orchestration.audit.AuditService;
import com.agentic.urlshortener.orchestration.domain.AuditEvent;
import com.agentic.urlshortener.orchestration.domain.Decision;
import com.agentic.urlshortener.orchestration.domain.DecisionType;
import com.agentic.urlshortener.orchestration.domain.RunStatus;
import com.agentic.urlshortener.orchestration.domain.StageAttempt;
import com.agentic.urlshortener.orchestration.domain.WorkflowRun;
import com.agentic.urlshortener.orchestration.engine.ArtifactStore;
import com.agentic.urlshortener.orchestration.repository.DecisionRepository;
import com.agentic.urlshortener.orchestration.repository.StageAttemptRepository;
import com.agentic.urlshortener.orchestration.repository.StageNodeRepository;
import com.agentic.urlshortener.orchestration.repository.WorkflowRunRepository;
import com.agentic.urlshortener.orchestration.service.WorkflowService;
import com.agentic.urlshortener.support.GovernanceHarness;
import com.agentic.urlshortener.support.IntegrationTest;
import com.agentic.urlshortener.support.ScriptedAgent;

/**
 * T074 (FR-ORC-18, NFR-AUT-02, PVT-17): a run whose autonomy budget (stage attempts or processing
 * time, excluding time waiting for humans) is spent starts no further attempt and safe-stops with
 * trigger {@code AUTONOMY_BUDGET_EXCEEDED} and a reason naming the exhausted limit. The limits are
 * lowered from 60 attempts and 10 minutes so the test stays fast; the check is the same.
 */
@IntegrationTest
@Import(ScriptedAgent.Config.class)
@TestPropertySource(properties = {
        "app.orchestration.autonomy.max-attempts=3",
        "app.orchestration.autonomy.max-processing-time=PT1S"})
@Tag("FR-ORC-18")
@Tag("NFR-AUT-02")
class AutonomyBudgetTest {

    @Autowired private MockMvc mvc;
    @Autowired private WorkflowService workflows;
    @Autowired private WorkflowRunRepository runs;
    @Autowired private StageNodeRepository nodes;
    @Autowired private StageAttemptRepository attempts;
    @Autowired private DecisionRepository decisions;
    @Autowired private ArtifactStore artifacts;
    @Autowired private AuditService audit;
    @Autowired private AutonomyBudget budget;
    @Autowired private ScriptedAgent.Scripts scripts;

    private GovernanceHarness harness;

    @BeforeEach
    void setUp() {
        harness = new GovernanceHarness(mvc, workflows, runs, nodes);
        scripts.reset();
    }

    @Test
    void theBudgetIsSpentAtEitherLimit() {
        assertThat(budget.exhaustion(2, 999)).isEmpty();
        assertThat(budget.exhaustion(3, 0)).hasValueSatisfying(reason -> assertThat(reason).contains("3 of 3 stage attempts"));
        assertThat(budget.exhaustion(0, 1000)).hasValueSatisfying(reason -> assertThat(reason).contains("processing time"));
    }

    @Test
    void exhaustedAttemptsSafeStopTheRun() {
        UUID runId = harness.submit(GovernanceHarness.ALICE);
        harness.awaitStatus(runId, RunStatus.SAFE_STOPPED);

        WorkflowRun run = assertSafelyStoppedByBudget(runId);
        assertThat(run.getAttemptsUsed()).isEqualTo(3);
        assertThat(run.getTerminalReason()).contains("autonomy budget").contains("3 of 3 stage attempts");
    }

    @Test
    void exhaustedProcessingTimeSafeStopsTheRun() {
        scripts.sleepThenSucceed(REQUIREMENT_INGESTION, 1200);
        UUID runId = harness.submit(GovernanceHarness.ALICE);
        harness.awaitStatus(runId, RunStatus.SAFE_STOPPED);

        WorkflowRun run = assertSafelyStoppedByBudget(runId);
        assertThat(run.getAttemptsUsed()).isEqualTo(1);
        assertThat(run.getProcessingMillis()).isGreaterThanOrEqualTo(1000);
        assertThat(run.getTerminalReason()).contains("autonomy budget").contains("processing time");
    }

    private WorkflowRun assertSafelyStoppedByBudget(UUID runId) {
        WorkflowRun run = runs.findById(runId).orElseThrow();
        assertThat(run.getStatus()).isEqualTo(RunStatus.SAFE_STOPPED);
        List<AuditEvent> chain = audit.chain(runId.toString());
        List<AuditEvent> terminated = chain.stream().filter(e -> e.getAction().equals("RUN_TERMINATED")).toList();
        assertThat(terminated).as("exactly one terminal outcome").hasSize(1);
        long terminatedSeq = terminated.get(0).getSeq();
        assertThat(chain).as("no dispatch after termination")
                .noneMatch(e -> e.getAction().equals("ATTEMPT_STARTED") && e.getSeq() > terminatedSeq);
        assertThat(chain.stream().filter(e -> e.getAction().equals("ATTEMPT_STARTED")).count())
                .as("attempts started never exceed the budget").isEqualTo(run.getAttemptsUsed());
        assertThat(attempts.findByRunIdOrderByStartedAtAsc(runId)).as("no attempt left open").allMatch(StageAttempt::isFinished);
        Decision stop = decisions.findByRunIdOrderByCreatedAtAsc(runId).stream()
                .filter(d -> d.getDecisionType() == DecisionType.SAFE_STOP).findFirst().orElseThrow();
        assertThat(CanonicalJson.parse(stop.getPayload()).path("trigger").asString()).isEqualTo("AUTONOMY_BUDGET_EXCEEDED");
        assertThat(artifacts.current(runId)).as("final summary produced").containsKey("FINAL_SUMMARY");
        return run;
    }
}
