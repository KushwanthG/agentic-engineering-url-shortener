package com.agentic.urlshortener.orchestration.reliability;

import static com.agentic.urlshortener.orchestration.domain.StageType.DESIGN;
import static com.agentic.urlshortener.orchestration.domain.StageType.RELEASE_APPROVAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.agentic.urlshortener.orchestration.agent.StageResult;
import com.agentic.urlshortener.orchestration.agent.TransientStageException;
import com.agentic.urlshortener.orchestration.audit.AuditService;
import com.agentic.urlshortener.orchestration.domain.AttemptOutcome;
import com.agentic.urlshortener.orchestration.domain.FailureClass;
import com.agentic.urlshortener.orchestration.domain.FailureEvent;
import com.agentic.urlshortener.orchestration.domain.RunStatus;
import com.agentic.urlshortener.orchestration.domain.StageAttempt;
import com.agentic.urlshortener.orchestration.domain.StageStatus;
import com.agentic.urlshortener.orchestration.repository.FailureEventRepository;
import com.agentic.urlshortener.orchestration.repository.StageAttemptRepository;
import com.agentic.urlshortener.orchestration.repository.StageNodeRepository;
import com.agentic.urlshortener.orchestration.repository.WorkflowRunRepository;
import com.agentic.urlshortener.orchestration.service.WorkflowService;
import com.agentic.urlshortener.support.GovernanceHarness;
import com.agentic.urlshortener.support.IntegrationTest;
import com.agentic.urlshortener.support.ScriptedAgent;

/**
 * T067 (FR-REL-01, FR-REL-02, FR-REL-09, RDR-01): transient failures are retried with backoff up to
 * the maximum attempts; permanent failures are not retried; a timed-out attempt is a transient failure
 * and its late result is discarded. Backoff and timeout are shortened for the test.
 */
@IntegrationTest
@Import(ScriptedAgent.Config.class)
@TestPropertySource(properties = {
        "app.orchestration.stages.defaults.initial-backoff=PT0.1S",
        "app.orchestration.stages.defaults.timeout=PT1S" })
@Tag("FR-REL-01")
@Tag("FR-REL-02")
@Tag("FR-REL-09")
@Tag("NFR-REL-02")
@Tag("RDR-01")
class StageRetryTimeoutTest {

    @Autowired private MockMvc mvc;
    @Autowired private WorkflowService workflows;
    @Autowired private WorkflowRunRepository runs;
    @Autowired private StageNodeRepository nodes;
    @Autowired private StageAttemptRepository attempts;
    @Autowired private FailureEventRepository failures;
    @Autowired private AuditService audit;
    @Autowired private ScriptedAgent.Scripts scripts;

    private GovernanceHarness harness;

    @BeforeEach
    void setUp() {
        harness = new GovernanceHarness(mvc, workflows, runs, nodes);
        scripts.reset();
    }

    private List<StageAttempt> designAttempts(UUID runId) {
        return attempts.findByRunIdOrderByStartedAtAsc(runId).stream().filter(a -> a.getStageKey() == DESIGN).toList();
    }

    private void failTransientlyTimes(int failures) {
        AtomicInteger calls = new AtomicInteger();
        scripts.set(DESIGN, context -> calls.incrementAndGet() <= failures
                ? new StageResult.Failed(FailureClass.TRANSIENT, "simulated transient failure " + calls.get())
                : ScriptedAgent.defaultSuccess(DESIGN));
    }

    @Test
    void twoTransientFailuresRecoverOnTheThirdAttemptAfterBackoff() {
        failTransientlyTimes(2);
        UUID runId = harness.submit(GovernanceHarness.ALICE);
        harness.awaitGate(runId, RELEASE_APPROVAL);

        List<StageAttempt> design = designAttempts(runId);
        assertThat(design).extracting(StageAttempt::getOutcome)
                .containsExactly(AttemptOutcome.FAILED_TRANSIENT, AttemptOutcome.FAILED_TRANSIENT, AttemptOutcome.SUCCEEDED);
        assertThat(Duration.between(design.get(0).getFinishedAt(), design.get(1).getStartedAt()))
                .isGreaterThanOrEqualTo(Duration.ofMillis(100));
        assertThat(Duration.between(design.get(1).getFinishedAt(), design.get(2).getStartedAt()))
                .isGreaterThanOrEqualTo(Duration.ofMillis(200));
        assertThat(harness.node(runId, DESIGN).getStatus()).isEqualTo(StageStatus.SUCCEEDED);
        assertThat(audit.chain(runId.toString())).filteredOn(e -> e.getAction().equals("RETRY_SCHEDULED")).hasSize(2);

        FailureEvent event = failures.findByRunId(runId).stream().filter(f -> f.getStageKey() == DESIGN).findFirst().orElseThrow();
        assertThat(event.getStatus()).isEqualTo(FailureEvent.RECOVERED);
        assertThat(event.getMechanism()).isEqualTo("RETRY");
        assertThat(event.getDetectedAt()).isEqualTo(design.get(0).getFinishedAt());
        assertThat(event.getRecoveryCompletedAt()).isEqualTo(design.get(2).getFinishedAt());
    }

    @Test
    void anAgentExceptionIsClassifiedAndATransientOneIsRetried() {
        AtomicInteger calls = new AtomicInteger();
        scripts.set(DESIGN, context -> {
            if (calls.incrementAndGet() == 1) {
                throw new TransientStageException("store busy");
            }
            return ScriptedAgent.defaultSuccess(DESIGN);
        });
        UUID runId = harness.submit(GovernanceHarness.ALICE);
        harness.awaitGate(runId, RELEASE_APPROVAL);

        assertThat(designAttempts(runId)).extracting(StageAttempt::getOutcome)
                .containsExactly(AttemptOutcome.FAILED_TRANSIENT, AttemptOutcome.SUCCEEDED);
    }

    @Test
    void aPermanentFailureIsNotRetriedAndSafeStopsTheRun() {
        scripts.set(DESIGN, context -> new StageResult.Failed(FailureClass.PERMANENT, "design rejected by validation"));
        UUID runId = harness.submit(GovernanceHarness.ALICE);
        harness.awaitStatus(runId, RunStatus.SAFE_STOPPED);

        assertThat(designAttempts(runId)).extracting(StageAttempt::getOutcome).containsExactly(AttemptOutcome.FAILED_PERMANENT);
        assertThat(harness.node(runId, DESIGN).getStatus()).isEqualTo(StageStatus.FAILED);
        assertThat(failures.findByRunId(runId)).extracting(FailureEvent::getStatus).containsExactly(FailureEvent.UNRECOVERED);
    }

    @Test
    void exhaustedRetriesFailTheStageAfterExactlyTheMaximumAttempts() {
        failTransientlyTimes(Integer.MAX_VALUE);
        UUID runId = harness.submit(GovernanceHarness.ALICE);
        harness.awaitStatus(runId, RunStatus.SAFE_STOPPED);

        assertThat(designAttempts(runId)).hasSize(3).allMatch(a -> a.getOutcome() == AttemptOutcome.FAILED_TRANSIENT);
        assertThat(harness.node(runId, DESIGN).getStatus()).isEqualTo(StageStatus.FAILED);
        assertThat(harness.node(runId, DESIGN).getLastFailureReason()).contains("retries exhausted after 3 attempts");
        assertThat(runs.findById(runId).orElseThrow().getTerminalReason()).contains("DESIGN");
    }

    @Test
    void aTimedOutAttemptIsRetriedAndItsLateResultIsDiscarded() {
        AtomicInteger calls = new AtomicInteger();
        scripts.set(DESIGN, context -> {
            if (calls.incrementAndGet() == 1) {
                long until = System.nanoTime() + Duration.ofSeconds(3).toNanos();
                while (System.nanoTime() < until) {
                    // ignores interruption on purpose: a misbehaving agent that returns late
                    Thread.onSpinWait();
                }
            }
            return ScriptedAgent.defaultSuccess(DESIGN);
        });
        UUID runId = harness.submit(GovernanceHarness.ALICE);
        harness.awaitGate(runId, RELEASE_APPROVAL);

        List<StageAttempt> design = designAttempts(runId);
        assertThat(design).extracting(StageAttempt::getOutcome).containsExactly(AttemptOutcome.TIMED_OUT, AttemptOutcome.SUCCEEDED);
        assertThat(design.get(0).getFailureClass()).isEqualTo(FailureClass.TRANSIENT);
        await().atMost(Duration.ofSeconds(10)).until(() -> audit.chain(runId.toString()).stream()
                .anyMatch(e -> e.getAction().equals("ATTEMPT_DISCARDED") && e.getTarget().equals("DESIGN")
                        && e.getReason().contains("after timeout")));
    }
}
