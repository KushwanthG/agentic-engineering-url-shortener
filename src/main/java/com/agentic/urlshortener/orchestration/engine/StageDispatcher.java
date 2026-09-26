package com.agentic.urlshortener.orchestration.engine;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Component;

import com.agentic.urlshortener.orchestration.agent.AgentPermissionDeniedException;
import com.agentic.urlshortener.orchestration.agent.StageResult;
import com.agentic.urlshortener.orchestration.config.OrchestrationProperties;
import com.agentic.urlshortener.orchestration.domain.FailureClass;

import jakarta.annotation.PreDestroy;

/**
 * Runs agents on a bounded pool (default 8 threads), outside any transaction or run lock. An agent
 * exception becomes a failed result: data-access hiccups are transient, everything else (including a
 * permission violation) is permanent.
 */
@Component
public class StageDispatcher {

    private static final Logger log = LoggerFactory.getLogger(StageDispatcher.class);

    private final ExecutorService executor;

    public StageDispatcher(OrchestrationProperties properties) {
        AtomicInteger counter = new AtomicInteger();
        ThreadFactory threads = runnable -> {
            Thread thread = new Thread(runnable, "stage-exec-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        this.executor = Executors.newFixedThreadPool(properties.stageExecutorThreads(), threads);
    }

    /** Executes the attempt asynchronously and hands its result to {@code onFinished}. */
    public void submit(Dispatch dispatch, Consumer<StageResult> onFinished) {
        executor.execute(() -> {
            MDC.put("runId", dispatch.context().runId().toString());
            MDC.put("stage", dispatch.context().stageType().name());
            try {
                onFinished.accept(execute(dispatch));
            } catch (RuntimeException e) {
                log.error("Recording the result of {} attempt {} failed", dispatch.context().stageType(), dispatch.attemptNo(), e);
            } finally {
                MDC.remove("runId");
                MDC.remove("stage");
            }
        });
    }

    static StageResult execute(Dispatch dispatch) {
        try {
            StageResult result = dispatch.agent().execute(dispatch.context());
            return result == null ? new StageResult.Failed(FailureClass.PERMANENT, "agent returned no result") : result;
        } catch (AgentPermissionDeniedException e) {
            return new StageResult.Failed(FailureClass.PERMANENT, e.getMessage());
        } catch (TransientDataAccessException e) {
            return new StageResult.Failed(FailureClass.TRANSIENT, "transient data access failure: " + e.getClass().getSimpleName());
        } catch (RuntimeException e) {
            log.warn("Agent {} failed", dispatch.agent().agentId(), e);
            return new StageResult.Failed(FailureClass.PERMANENT, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
