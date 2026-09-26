package com.agentic.urlshortener.orchestration.engine;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import com.agentic.urlshortener.orchestration.agent.StageResult;
import com.agentic.urlshortener.orchestration.config.OrchestrationProperties;
import com.agentic.urlshortener.orchestration.domain.FailureClass;
import com.agentic.urlshortener.orchestration.reliability.FailureClassifier;

import jakarta.annotation.PreDestroy;

/**
 * Runs agents on a bounded pool (default 8 threads), outside any transaction or run lock. An agent
 * exception becomes a failed result classified by {@link FailureClassifier}. Each attempt has a
 * timeout (FR-REL-09): when it passes, the attempt is interrupted and reported as
 * {@link StageResult.TimedOut}; whatever the agent returns afterwards is handed to the late-result
 * callback, never applied.
 */
@Component
public class StageDispatcher {

    private static final Logger log = LoggerFactory.getLogger(StageDispatcher.class);

    private final ExecutorService executor;
    private final ScheduledExecutorService timeouts;
    private final FailureClassifier classifier;
    private volatile boolean closing;

    public StageDispatcher(OrchestrationProperties properties, FailureClassifier classifier) {
        this.classifier = classifier;
        this.executor = Executors.newFixedThreadPool(properties.stageExecutorThreads(), daemonThreads("stage-exec-"));
        this.timeouts = Executors.newSingleThreadScheduledExecutor(daemonThreads("stage-timeout-"));
    }

    /**
     * Executes the attempt asynchronously. Exactly one of the result and the timeout reaches
     * {@code onFinished}; a result that loses that race goes to {@code onLate}.
     */
    public void submit(Dispatch dispatch, Consumer<StageResult> onFinished, Consumer<StageResult> onLate) {
        AtomicBoolean reported = new AtomicBoolean();
        Future<?> running = executor.submit(() -> {
            MDC.put("runId", dispatch.context().runId().toString());
            MDC.put("stage", dispatch.context().stageType().name());
            MDC.put("attempt", String.valueOf(dispatch.attemptNo()));
            try {
                StageResult result = execute(dispatch);
                if (closing) {
                    log.info("Dropping the result of {} attempt {}: the application is stopping; recovery resumes it",
                            dispatch.context().stageType(), dispatch.attemptNo());
                } else if (reported.compareAndSet(false, true)) {
                    onFinished.accept(result);
                } else {
                    onLate.accept(result);
                }
            } catch (RuntimeException e) {
                log.error("Recording the result of {} attempt {} failed", dispatch.context().stageType(), dispatch.attemptNo(), e);
            } finally {
                MDC.remove("runId");
                MDC.remove("stage");
                MDC.remove("attempt");
            }
        });
        timeouts.schedule(() -> {
            if (!closing && reported.compareAndSet(false, true)) {
                running.cancel(true);
                try {
                    onFinished.accept(new StageResult.TimedOut(dispatch.timeout()));
                } catch (RuntimeException e) {
                    log.error("Recording the timeout of {} attempt {} failed", dispatch.context().stageType(), dispatch.attemptNo(), e);
                }
            }
        }, dispatch.timeout().toMillis(), TimeUnit.MILLISECONDS);
    }

    StageResult execute(Dispatch dispatch) {
        try {
            StageResult result = dispatch.agent().execute(dispatch.context());
            return result == null ? new StageResult.Failed(FailureClass.PERMANENT, "agent returned no result") : result;
        } catch (RuntimeException e) {
            FailureClass failureClass = classifier.classify(e);
            if (failureClass == FailureClass.PERMANENT) {
                log.warn("Agent {} failed", dispatch.agent().agentId(), e);
            }
            return new StageResult.Failed(failureClass, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private static ThreadFactory daemonThreads(String prefix) {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    /** Results produced after shutdown began are dropped, as a process kill would: the attempt stays open for recovery. */
    @PreDestroy
    void shutdown() {
        closing = true;
        timeouts.shutdownNow();
        executor.shutdownNow();
    }
}
