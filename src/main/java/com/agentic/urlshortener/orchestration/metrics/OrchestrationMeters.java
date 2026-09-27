package com.agentic.urlshortener.orchestration.metrics;

import java.time.Duration;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.agentic.urlshortener.orchestration.domain.StageStatus;
import com.agentic.urlshortener.orchestration.domain.StageType;
import com.agentic.urlshortener.orchestration.repository.StageNodeRepository;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * Control-plane meters of plan.md §7 (NFR-OBS-02). Tags are bounded enumerations only (stage,
 * outcome, result); run ids never become tags. Meters are recorded after the surrounding transaction
 * commits, so a rolled-back state change is not counted; outside a transaction they are recorded at
 * once.
 */
@Component
public class OrchestrationMeters {

    private final MeterRegistry registry;
    private final Counter runsStarted;

    public OrchestrationMeters(MeterRegistry registry, StageNodeRepository nodes) {
        this.registry = registry;
        this.runsStarted = Counter.builder("sdlc.runs.started").description("Workflow runs submitted").register(registry);
        Gauge.builder("sdlc.gates.waiting", nodes, n -> n.countByStatus(StageStatus.AWAITING_DECISION))
                .description("Stages waiting for a human decision").register(registry);
    }

    public void runStarted() {
        afterCommit(runsStarted::increment);
    }

    /** {@code outcome}: COMPLETED, REJECTED, or SAFE_STOPPED. */
    public void runTerminated(String outcome) {
        afterCommit(() -> Counter.builder("sdlc.runs.terminated").tag("outcome", outcome).description("Workflow runs that ended")
                .register(registry).increment());
    }

    /** An attempt was dispatched: counted as a retry when it follows an attempt of the primary agent, or as a fallback. */
    public void attemptStarted(StageType stage, int attemptNo, boolean fallback) {
        afterCommit(() -> {
            if (fallback) {
                Counter.builder("sdlc.stage.fallbacks").tag("stage", stage.name()).register(registry).increment();
            } else if (attemptNo > 1) {
                Counter.builder("sdlc.stage.retries").tag("stage", stage.name()).register(registry).increment();
            }
        });
    }

    public void attemptFinished(StageType stage, String outcome, Duration duration) {
        afterCommit(() -> {
            Counter.builder("sdlc.stage.attempts").tag("stage", stage.name()).tag("outcome", outcome).register(registry).increment();
            Timer.builder("sdlc.stage.duration").tag("stage", stage.name()).register(registry).record(duration);
        });
    }

    /** {@code result}: OK, CONFLICT, or FAILED (compensation action results). */
    public void compensation(String result) {
        afterCommit(() -> Counter.builder("sdlc.compensations").tag("result", result).register(registry).increment());
    }

    private static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
