package com.agentic.urlshortener.orchestration.reliability;

import static com.agentic.urlshortener.orchestration.domain.StageType.ARCHITECTURE_APPROVAL;
import static com.agentic.urlshortener.orchestration.domain.StageType.DESIGN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.Instant;
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
import com.agentic.urlshortener.orchestration.agent.ArtifactDraft;
import com.agentic.urlshortener.orchestration.agent.StageResult;
import com.agentic.urlshortener.orchestration.audit.AuditService;
import com.agentic.urlshortener.orchestration.domain.ActorType;
import com.agentic.urlshortener.orchestration.domain.AttemptOutcome;
import com.agentic.urlshortener.orchestration.domain.AuditEvent;
import com.agentic.urlshortener.orchestration.domain.Decision;
import com.agentic.urlshortener.orchestration.domain.DecisionType;
import com.agentic.urlshortener.orchestration.domain.FailureClass;
import com.agentic.urlshortener.orchestration.domain.RunStatus;
import com.agentic.urlshortener.orchestration.domain.StageAttempt;
import com.agentic.urlshortener.orchestration.dto.RequirementSubmission;
import com.agentic.urlshortener.orchestration.engine.ArtifactStore;
import com.agentic.urlshortener.orchestration.governance.DeadlineSweeper;
import com.agentic.urlshortener.orchestration.repository.DecisionRepository;
import com.agentic.urlshortener.orchestration.repository.StageAttemptRepository;
import com.agentic.urlshortener.orchestration.repository.StageNodeRepository;
import com.agentic.urlshortener.orchestration.repository.WorkflowRunRepository;
import com.agentic.urlshortener.orchestration.service.WorkflowService;
import com.agentic.urlshortener.support.GovernanceHarness;
import com.agentic.urlshortener.support.IntegrationTest;
import com.agentic.urlshortener.support.ScriptedAgent;

/**
 * T071 (FR-REL-06, FR-ORC-17, NFR-REL-03, RDR-04, RDR-07): every safe-stop trigger ends the run in
 * exactly one terminal outcome, starts nothing afterwards, discards in-flight results, records the
 * trigger as a SAFE_STOP decision, and leaves a final summary. Covered triggers: exhausted retries
 * and fallback, gate deadline, operator request, compensation failure (CompensationCoordinatorTest).
 * Mandatory-policy rejection is covered by PolicyExceptionFlowTest (T076), clarification rounds by
 * the SCN-C tests (Phase 8); the autonomy budget (T074) is deferred by SD-1.
 */
@IntegrationTest
@Import(ScriptedAgent.Config.class)
@TestPropertySource(properties = "app.orchestration.stages.defaults.initial-backoff=PT0.05S")
@Tag("FR-REL-06")
@Tag("FR-ORC-17")
@Tag("NFR-REL-03")
@Tag("RDR-04")
@Tag("RDR-07")
class SafeStopServiceTest {

    @Autowired private MockMvc mvc;
    @Autowired private WorkflowService workflows;
    @Autowired private WorkflowRunRepository runs;
    @Autowired private StageNodeRepository nodes;
    @Autowired private StageAttemptRepository attempts;
    @Autowired private DecisionRepository decisions;
    @Autowired private ArtifactStore artifacts;
    @Autowired private SafeStopService safeStops;
    @Autowired private DeadlineSweeper sweeper;
    @Autowired private AuditService audit;
    @Autowired private ScriptedAgent.Scripts scripts;

    private GovernanceHarness harness;

    @BeforeEach
    void setUp() {
        harness = new GovernanceHarness(mvc, workflows, runs, nodes);
        scripts.reset();
    }

    /** The common guarantees of every safe-stop, whatever the trigger. */
    private void assertSafelyStopped(UUID runId, String trigger) {
        assertThat(runs.findById(runId).orElseThrow().getStatus()).isEqualTo(RunStatus.SAFE_STOPPED);
        List<AuditEvent> chain = audit.chain(runId.toString());
        List<AuditEvent> terminated = chain.stream().filter(e -> e.getAction().equals("RUN_TERMINATED")).toList();
        assertThat(terminated).as("exactly one terminal outcome").hasSize(1);
        long terminatedSeq = terminated.get(0).getSeq();
        assertThat(chain).as("no dispatch after termination")
                .noneMatch(e -> e.getAction().equals("ATTEMPT_STARTED") && e.getSeq() > terminatedSeq);
        Decision stop = decisions.findByRunIdOrderByCreatedAtAsc(runId).stream()
                .filter(d -> d.getDecisionType() == DecisionType.SAFE_STOP).findFirst().orElseThrow();
        assertThat(CanonicalJson.parse(stop.getPayload()).path("trigger").asString()).isEqualTo(trigger);
        assertThat(artifacts.current(runId)).as("final summary produced").containsKey("FINAL_SUMMARY");
        assertThat(chain).anyMatch(e -> e.getAction().equals("COMPENSATION_ACTION"));
    }

    @Test
    void exhaustedRetriesWithoutFallbackSafeStop() {
        scripts.set(DESIGN, context -> new StageResult.Failed(FailureClass.TRANSIENT, "design store unavailable"));
        UUID runId = harness.submit(GovernanceHarness.ALICE);
        harness.awaitStatus(runId, RunStatus.SAFE_STOPPED);

        assertSafelyStopped(runId, "STAGE_FAILED");
        assertThat(runs.findById(runId).orElseThrow().getTerminalReason()).contains("DESIGN").contains("retries exhausted");
    }

    @Test
    void aPassedGateDeadlineSafeStops() {
        scripts.set(DESIGN, context -> new StageResult.Succeeded(List.of(ArtifactDraft.json("DESIGN",
                "{\"materialChange\":true,\"materialReasons\":[\"public API change\"]}")), "scripted material design"));
        UUID runId = harness.submit(GovernanceHarness.ALICE, new RequirementSubmission.SimulationOptions(1, List.of()));
        harness.awaitGate(runId, ARCHITECTURE_APPROVAL);
        Instant deadline = harness.node(runId, ARCHITECTURE_APPROVAL).getDecisionDeadline();
        await().atMost(Duration.ofSeconds(5)).until(() -> Instant.now().isAfter(deadline));

        sweeper.sweep();

        assertSafelyStopped(runId, "GATE_DEADLINE");
    }

    @Test
    void anOperatorRequestStopsARunningStageAndItsLateResultIsDiscarded() {
        scripts.sleepThenSucceed(DESIGN, 1500);
        UUID runId = harness.submit(GovernanceHarness.ALICE);
        await().atMost(Duration.ofSeconds(10)).until(() -> attempts.findByRunIdOrderByStartedAtAsc(runId).stream()
                .anyMatch(a -> a.getStageKey() == DESIGN));

        assertThat(safeStops.stop(runId, SafeStopService.Trigger.OPERATOR_REQUEST, "operator safe-stop by carol: test",
                ActorType.HUMAN, "carol")).isTrue();

        assertSafelyStopped(runId, "OPERATOR_REQUEST");
        await().atMost(Duration.ofSeconds(10)).until(() -> attempts.findByRunIdOrderByStartedAtAsc(runId).stream()
                .filter(a -> a.getStageKey() == DESIGN).allMatch(StageAttempt::isFinished));
        assertThat(attempts.findByRunIdOrderByStartedAtAsc(runId)).filteredOn(a -> a.getStageKey() == DESIGN)
                .extracting(StageAttempt::getOutcome).containsExactly(AttemptOutcome.DISCARDED);
    }

    @Test
    void aSecondStopOfATerminalRunChangesNothing() {
        scripts.sleepThenSucceed(DESIGN, 1000);
        UUID runId = harness.submit(GovernanceHarness.ALICE);
        assertThat(safeStops.stop(runId, SafeStopService.Trigger.OPERATOR_REQUEST, "first", ActorType.HUMAN, "carol")).isTrue();

        assertThat(safeStops.stop(runId, SafeStopService.Trigger.OPERATOR_REQUEST, "second", ActorType.HUMAN, "carol")).isFalse();

        assertSafelyStopped(runId, "OPERATOR_REQUEST");
        assertThat(runs.findById(runId).orElseThrow().getTerminalReason()).isEqualTo("first");
    }
}
