package com.agentic.urlshortener.orchestration.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import com.agentic.urlshortener.orchestration.domain.AttemptOutcome;
import com.agentic.urlshortener.orchestration.domain.Classification;
import com.agentic.urlshortener.orchestration.domain.FailureClass;
import com.agentic.urlshortener.orchestration.domain.FailureEvent;
import com.agentic.urlshortener.orchestration.domain.FaultPlan;
import com.agentic.urlshortener.orchestration.domain.StageType;
import com.agentic.urlshortener.orchestration.domain.WorkflowRun;
import com.agentic.urlshortener.orchestration.reliability.FailureEventRecorder;
import com.agentic.urlshortener.orchestration.repository.FailureEventRepository;
import com.agentic.urlshortener.orchestration.repository.WorkflowRunRepository;
import com.agentic.urlshortener.support.IntegrationTest;

/**
 * T103 (FR-AUD-04, NFR-RCV-02; RDR-01, RDR-02, RDR-05): the MTTR timestamps of a failure episode.
 * It opens at the first failed attempt ({@code detected_at}); recovery starts at the next attempt;
 * it completes at the stage's success. The mechanism is RETRY, FALLBACK, or RESUME. Terminal failure
 * leaves it UNRECOVERED, and a superseded generation is excluded. Verification test: the recorder
 * was implemented with the engine in Phase 6 (see tdd-evidence.md).
 */
@IntegrationTest
@Tag("FR-AUD-04")
@Tag("NFR-RCV-02")
@Tag("RDR-01")
@Tag("RDR-02")
@Tag("RDR-05")
class FailureEventRecorderTest {

    private static final Instant T0 = Instant.parse("2026-09-27T10:00:00Z");

    @Autowired private FailureEventRecorder recorder;
    @Autowired private FailureEventRepository events;
    @Autowired private WorkflowRunRepository runs;
    @Autowired private TransactionTemplate tx;

    private UUID runId;

    @BeforeEach
    void setUp() {
        runId = runs.saveAndFlush(WorkflowRun.create(UUID.randomUUID(), "GF-001", "MTTR", "alice", Classification.NEW_CAPABILITY,
                "1.0.0", T0)).getId();
    }

    private void inTx(Runnable action) {
        tx.executeWithoutResult(status -> action.run());
    }

    private List<FailureEvent> recorded() {
        return events.findByRunId(runId);
    }

    @Test
    void aRetriedStageIsOneRecoveredEpisodeFromDetectionToSuccess() {
        inTx(() -> recorder.attemptFailed(runId, StageType.DESIGN, 1, FailureClass.TRANSIENT, FailureEventRecorder.AGENT_ERROR, true,
                T0.plusMillis(100)));
        inTx(() -> recorder.recoveryStarted(runId, StageType.DESIGN, 1, FailureEventRecorder.RETRY, T0.plusMillis(300)));
        // a second failure within the same generation does not open a second episode or move the recovery start
        inTx(() -> recorder.attemptFailed(runId, StageType.DESIGN, 1, FailureClass.TRANSIENT, FailureEventRecorder.AGENT_ERROR, true,
                T0.plusMillis(400)));
        inTx(() -> recorder.recoveryStarted(runId, StageType.DESIGN, 1, FailureEventRecorder.RETRY, T0.plusMillis(800)));
        inTx(() -> recorder.stageSucceeded(runId, StageType.DESIGN, 1, T0.plusMillis(900)));

        assertThat(recorded()).singleElement().satisfies(e -> {
            assertThat(e.getStatus()).isEqualTo(FailureEvent.RECOVERED);
            assertThat(e.getDetectedAt()).isEqualTo(T0.plusMillis(100));
            assertThat(e.getRecoveryStartedAt()).isEqualTo(T0.plusMillis(300));
            assertThat(e.getRecoveryCompletedAt()).isEqualTo(T0.plusMillis(900));
            assertThat(e.getMechanism()).isEqualTo(FailureEventRecorder.RETRY);
            assertThat(e.getCause()).isEqualTo(FailureEventRecorder.AGENT_ERROR);
            assertThat(e.isSimulated()).isTrue();
            assertThat(e.getExcludedReason()).isNull();
        });
    }

    @Test
    void theMechanismIsTheOneThatRecoveredAndAnInterruptionResumes() {
        inTx(() -> recorder.attemptFailed(runId, StageType.DOCUMENTATION, 1, FailureClass.PERMANENT, FailureEventRecorder.AGENT_ERROR,
                false, T0));
        inTx(() -> recorder.recoveryStarted(runId, StageType.DOCUMENTATION, 1, FailureEventRecorder.FALLBACK, T0.plusMillis(50)));
        inTx(() -> recorder.stageSucceeded(runId, StageType.DOCUMENTATION, 1, T0.plusMillis(70)));

        inTx(() -> recorder.attemptFailed(runId, StageType.TESTING, 1, FailureClass.TRANSIENT,
                FailureEventRecorder.PROCESS_INTERRUPTION, false, T0));
        inTx(() -> recorder.recoveryStarted(runId, StageType.TESTING, 1, FailureEventRecorder.RETRY, T0.plusSeconds(2)));
        inTx(() -> recorder.stageSucceeded(runId, StageType.TESTING, 1, T0.plusSeconds(3)));

        assertThat(recorded()).extracting(FailureEvent::getStageKey, FailureEvent::getMechanism, FailureEvent::getStatus)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(StageType.DOCUMENTATION, FailureEventRecorder.FALLBACK, FailureEvent.RECOVERED),
                        org.assertj.core.groups.Tuple.tuple(StageType.TESTING, FailureEventRecorder.RESUME, FailureEvent.RECOVERED));
    }

    @Test
    void terminalFailureAndRunEndLeaveEpisodesUnrecovered() {
        inTx(() -> recorder.attemptFailed(runId, StageType.DESIGN, 1, FailureClass.PERMANENT, FailureEventRecorder.AGENT_ERROR, false, T0));
        inTx(() -> recorder.stageFailed(runId, StageType.DESIGN, 1));
        inTx(() -> recorder.attemptFailed(runId, StageType.TESTING, 1, FailureClass.TRANSIENT, FailureEventRecorder.TIMEOUT, false, T0));
        inTx(() -> recorder.runTerminated(runId));

        assertThat(recorded()).allSatisfy(e -> {
            assertThat(e.getStatus()).isEqualTo(FailureEvent.UNRECOVERED);
            assertThat(e.getRecoveryCompletedAt()).isNull();
        });
        // a success after the episode was closed does not reopen or recover it
        inTx(() -> recorder.stageSucceeded(runId, StageType.DESIGN, 1, T0.plusSeconds(1)));
        assertThat(recorded()).extracting(FailureEvent::getStatus).containsOnly(FailureEvent.UNRECOVERED);
    }

    @Test
    void aSupersededGenerationIsExcludedAndTheNextGenerationIsAFreshEpisode() {
        inTx(() -> recorder.attemptFailed(runId, StageType.DESIGN, 1, FailureClass.TRANSIENT, FailureEventRecorder.AGENT_ERROR, false, T0));
        inTx(() -> recorder.generationInvalidated(runId, StageType.DESIGN, 1, "re-planned after clarification"));
        inTx(() -> recorder.attemptFailed(runId, StageType.DESIGN, 2, FailureClass.TRANSIENT, FailureEventRecorder.AGENT_ERROR, false,
                T0.plusSeconds(5)));

        assertThat(recorded()).hasSize(2);
        assertThat(recorded()).filteredOn(e -> e.getGeneration() == 1).singleElement().satisfies(e -> {
            assertThat(e.getStatus()).isEqualTo(FailureEvent.UNRECOVERED);
            assertThat(e.getExcludedReason()).isEqualTo("re-planned after clarification");
        });
        assertThat(recorded()).filteredOn(e -> e.getGeneration() == 2).singleElement()
                .extracting(FailureEvent::getStatus).isEqualTo(FailureEvent.OPEN);
    }

    @Test
    void causeCodesFollowTheAttemptOutcome() {
        assertThat(FailureEventRecorder.causeOf(AttemptOutcome.TIMED_OUT, null, "x")).isEqualTo(FailureEventRecorder.TIMEOUT);
        assertThat(FailureEventRecorder.causeOf(AttemptOutcome.INTERRUPTED, null, null)).isEqualTo(FailureEventRecorder.PROCESS_INTERRUPTION);
        assertThat(FailureEventRecorder.causeOf(AttemptOutcome.FAILED_PERMANENT, FaultPlan.VERIFICATION_FAILURE, null))
                .isEqualTo(FailureEventRecorder.VERIFICATION_FAILURE);
        assertThat(FailureEventRecorder.causeOf(AttemptOutcome.FAILED_PERMANENT, null, "post-release verification failed (x)"))
                .isEqualTo(FailureEventRecorder.VERIFICATION_FAILURE);
        assertThat(FailureEventRecorder.causeOf(AttemptOutcome.FAILED_TRANSIENT, FaultPlan.TRANSIENT_ERROR, "boom"))
                .isEqualTo(FailureEventRecorder.AGENT_ERROR);
    }
}
