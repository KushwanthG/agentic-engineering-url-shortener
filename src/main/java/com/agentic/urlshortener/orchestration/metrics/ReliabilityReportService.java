package com.agentic.urlshortener.orchestration.metrics;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.agentic.urlshortener.orchestration.audit.AuditService;
import com.agentic.urlshortener.orchestration.domain.AuditEvent;
import com.agentic.urlshortener.orchestration.domain.StageStatus;
import com.agentic.urlshortener.orchestration.domain.WorkflowRun;
import com.agentic.urlshortener.orchestration.metrics.ReliabilityCalculator.AttemptFact;
import com.agentic.urlshortener.orchestration.metrics.ReliabilityCalculator.FailureFact;
import com.agentic.urlshortener.orchestration.metrics.ReliabilityCalculator.Interval;
import com.agentic.urlshortener.orchestration.metrics.ReliabilityCalculator.RunFact;
import com.agentic.urlshortener.orchestration.repository.FailureEventRepository;
import com.agentic.urlshortener.orchestration.repository.StageAttemptRepository;
import com.agentic.urlshortener.orchestration.repository.WorkflowRunRepository;

/**
 * Reliability report (FR-AUD-04, SC-008, plan.md §7), computed on request from the database: every
 * run, attempt, and failure event at report time. Human wait comes from the runs' audit trails
 * (AWAITING_DECISION intervals), compensation from COMPENSATION_ACTION events, and rollbacks from
 * capability changes on the GLOBAL chain whose reason starts with {@code rollback:}.
 */
@Service
@Transactional(readOnly = true)
public class ReliabilityReportService {

    private static final String AWAITING = StageStatus.AWAITING_DECISION.name();

    private final WorkflowRunRepository runs;
    private final StageAttemptRepository attempts;
    private final FailureEventRepository failures;
    private final AuditService audit;
    private final Clock clock;

    public ReliabilityReportService(WorkflowRunRepository runs, StageAttemptRepository attempts, FailureEventRepository failures,
            AuditService audit, Clock clock) {
        this.runs = runs;
        this.attempts = attempts;
        this.failures = failures;
        this.audit = audit;
        this.clock = clock;
    }

    public ReliabilityCalculator.Report report() {
        List<RunFact> runFacts = new ArrayList<>();
        List<AttemptFact> attemptFacts = new ArrayList<>();
        for (WorkflowRun run : runs.findAllByOrderByCreatedAtDesc()) {
            runFacts.add(runFact(run));
            attempts.findByRunIdOrderByStartedAtAsc(run.getId()).forEach(a -> attemptFacts.add(new AttemptFact(a.getAttemptNo(), a.isFallback())));
        }
        List<FailureFact> failureFacts = failures.findAll().stream()
                .map(f -> new FailureFact(f.getRunId(), f.getStageKey().name(), f.getCause(), f.isSimulated(), f.getStatus(),
                        f.getDetectedAt(), f.getRecoveryCompletedAt(), f.getMechanism(), f.getExcludedReason()))
                .toList();
        int rollbacks = (int) audit.chain(AuditService.GLOBAL_CHAIN).stream()
                .filter(e -> "CAPABILITY_CHANGED".equals(e.getAction()) && "CHANGED".equals(e.getResult())
                        && e.getReason() != null && e.getReason().startsWith("rollback:"))
                .count();
        return ReliabilityCalculator.compute(runFacts, attemptFacts, failureFacts, rollbacks, clock.instant());
    }

    private RunFact runFact(WorkflowRun run) {
        List<AuditEvent> trail = audit.chain(run.getId().toString());
        List<Interval> waits = new ArrayList<>();
        Map<String, java.time.Instant> waitingSince = new HashMap<>();
        boolean compensated = false;
        for (AuditEvent event : trail) {
            if ("COMPENSATION_ACTION".equals(event.getAction())) {
                compensated = true;
            }
            if (!"STAGE_TRANSITION".equals(event.getAction())) {
                continue;
            }
            if (AWAITING.equals(event.getToState())) {
                waitingSince.putIfAbsent(event.getTarget(), event.getOccurredAt());
            } else if (AWAITING.equals(event.getFromState()) && waitingSince.containsKey(event.getTarget())) {
                waits.add(new Interval(waitingSince.remove(event.getTarget()), event.getOccurredAt()));
            }
        }
        waitingSince.values().forEach(since -> waits.add(new Interval(since, null)));
        return new RunFact(run.getId(), run.getTerminalOutcome(), run.getFaultPlan() != null, run.getStartedAt(), run.getCompletedAt(),
                waits, compensated);
    }
}
