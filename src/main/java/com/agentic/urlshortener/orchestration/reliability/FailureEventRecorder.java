package com.agentic.urlshortener.orchestration.reliability;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.agentic.urlshortener.orchestration.domain.AttemptOutcome;
import com.agentic.urlshortener.orchestration.domain.FailureClass;
import com.agentic.urlshortener.orchestration.domain.FailureEvent;
import com.agentic.urlshortener.orchestration.domain.FaultPlan;
import com.agentic.urlshortener.orchestration.domain.StageType;
import com.agentic.urlshortener.orchestration.repository.FailureEventRepository;

/**
 * Records the failure events behind the MTTR metric (plan.md §7): one event per stage generation,
 * opened at the end of its first failed attempt ({@code detected_at}), recovery started by the next
 * attempt (retry, fallback, or resume), recovered by the successful attempt, and unrecovered when the
 * stage fails for good or the run ends first. Called by the engine inside its transaction.
 */
@Component
public class FailureEventRecorder {

    public static final String RETRY = "RETRY";
    public static final String FALLBACK = "FALLBACK";
    public static final String RESUME = "RESUME";
    /** Cause codes of data-model.md ({@code failure_event.cause}). */
    public static final String AGENT_ERROR = "AGENT_ERROR";
    public static final String TIMEOUT = "TIMEOUT";
    public static final String PROCESS_INTERRUPTION = "PROCESS_INTERRUPTION";
    public static final String VERIFICATION_FAILURE = "VERIFICATION_FAILURE";
    public static final String COMPENSATION_ERROR = "COMPENSATION_ERROR";

    /** The cause code of a failed attempt, from its outcome, simulated fault, and reason. */
    public static String causeOf(AttemptOutcome outcome, String simulatedFault, String reason) {
        if (outcome == AttemptOutcome.TIMED_OUT) {
            return TIMEOUT;
        }
        if (outcome == AttemptOutcome.INTERRUPTED) {
            return PROCESS_INTERRUPTION;
        }
        if (FaultPlan.VERIFICATION_FAILURE.equals(simulatedFault) || (reason != null && reason.contains("verification failed"))) {
            return VERIFICATION_FAILURE;
        }
        return AGENT_ERROR;
    }

    private final FailureEventRepository events;

    public FailureEventRecorder(FailureEventRepository events) {
        this.events = events;
    }

    public void attemptFailed(UUID runId, StageType stage, int generation, FailureClass failureClass, String cause, boolean simulated,
            Instant detectedAt) {
        if (open(runId, stage, generation) == null) {
            events.save(FailureEvent.open(runId, stage, generation, failureClass, cause, simulated, detectedAt));
        }
    }

    public void recoveryStarted(UUID runId, StageType stage, int generation, String mechanism, Instant at) {
        FailureEvent event = open(runId, stage, generation);
        if (event != null) {
            event.recoveryStarted(at, PROCESS_INTERRUPTION.equals(event.getCause()) ? RESUME : mechanism);
        }
    }

    public void stageSucceeded(UUID runId, StageType stage, int generation, Instant at) {
        FailureEvent event = open(runId, stage, generation);
        if (event != null) {
            event.recovered(at);
        }
    }

    public void stageFailed(UUID runId, StageType stage, int generation) {
        FailureEvent event = open(runId, stage, generation);
        if (event != null) {
            event.unrecovered();
        }
    }

    /** Every event still open when the run ends was not recovered. */
    public void runTerminated(UUID runId) {
        events.findByRunId(runId).stream().filter(e -> FailureEvent.OPEN.equals(e.getStatus())).forEach(FailureEvent::unrecovered);
    }

    /** The stage generation was replaced (approval invalidated, re-plan) before it recovered: excluded from MTTR. */
    public void generationInvalidated(UUID runId, StageType stage, int generation, String reason) {
        FailureEvent event = open(runId, stage, generation);
        if (event != null) {
            event.exclude(reason);
            event.unrecovered();
        }
    }

    private FailureEvent open(UUID runId, StageType stage, int generation) {
        return events.findFirstByRunIdAndStageKeyAndGenerationAndStatus(runId, stage, generation, FailureEvent.OPEN).orElse(null);
    }
}
