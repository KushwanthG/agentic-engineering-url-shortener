package com.agentic.urlshortener.orchestration.reliability;

import java.util.EnumSet;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import com.agentic.urlshortener.orchestration.domain.RunStatus;
import com.agentic.urlshortener.orchestration.domain.WorkflowRun;
import com.agentic.urlshortener.orchestration.engine.RunCoordinator;
import com.agentic.urlshortener.orchestration.repository.WorkflowRunRepository;

/**
 * Resumes unfinished runs when the application starts (FR-REL-08, NFR-RCV-01, plan.md §6): interrupted
 * attempts return to the retry path, due retries are dispatched, and waiting gates keep their
 * deadlines. Paused runs stay paused until an operator resumes them. Runs are never left in
 * {@code COMPENSATING}: compensation and the terminal transition commit in one transaction.
 */
@Service
public class RecoveryService {

    private static final Logger log = LoggerFactory.getLogger(RecoveryService.class);

    private final WorkflowRunRepository runs;
    private final RunCoordinator coordinator;

    public RecoveryService(WorkflowRunRepository runs, RunCoordinator coordinator) {
        this.runs = runs;
        this.coordinator = coordinator;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void resumeUnfinishedRuns() {
        for (WorkflowRun run : runs.findByStatusIn(EnumSet.of(RunStatus.RUNNING, RunStatus.AWAITING_HUMAN, RunStatus.PAUSED))) {
            UUID runId = run.getId();
            int interrupted = coordinator.recoverInterrupted(runId);
            coordinator.advance(runId);
            log.info("Recovered run {} ({} interrupted attempt(s))", runId, interrupted);
        }
    }
}
