package com.agentic.urlshortener.orchestration.reliability;

import java.time.Duration;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.agentic.urlshortener.orchestration.config.OrchestrationProperties;
import com.agentic.urlshortener.orchestration.domain.WorkflowRun;

/**
 * The run autonomy budget (FR-ORC-18, NFR-AUT-02, PVT-17): at most {@code max-attempts} stage attempts
 * and {@code max-processing-time} of attempt processing per run; time waiting for humans is not
 * counted. The coordinator checks it before each dispatch; a spent budget safe-stops the run with
 * trigger {@code AUTONOMY_BUDGET_EXCEEDED}.
 */
@Component
public class AutonomyBudget {

    private final int maxAttempts;
    private final Duration maxProcessingTime;

    public AutonomyBudget(OrchestrationProperties properties) {
        this.maxAttempts = properties.autonomy().maxAttempts();
        this.maxProcessingTime = properties.autonomy().maxProcessingTime();
    }

    /** Why {@code run} may not start another attempt, or empty while budget remains. */
    public Optional<String> exhaustion(WorkflowRun run) {
        return exhaustion(run.getAttemptsUsed(), run.getProcessingMillis());
    }

    /** Why a run with this usage may not start another attempt, or empty while budget remains. */
    public Optional<String> exhaustion(int attemptsUsed, long processingMillis) {
        if (attemptsUsed >= maxAttempts) {
            return Optional.of("autonomy budget exceeded: " + attemptsUsed + " of " + maxAttempts + " stage attempts used");
        }
        if (processingMillis >= maxProcessingTime.toMillis()) {
            return Optional.of("autonomy budget exceeded: " + processingMillis + " ms of " + maxProcessingTime.toMillis()
                    + " ms processing time used");
        }
        return Optional.empty();
    }
}
