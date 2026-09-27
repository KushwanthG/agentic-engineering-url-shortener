package com.agentic.urlshortener.orchestration.metrics;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The reliability metrics of plan.md §7 as a pure function of recorded facts, so every figure can be
 * reproduced by hand (docs/assessment/mttr-validation.md). Rates with an empty denominator are 0;
 * MTTR and latency percentiles are absent when there is nothing to measure.
 */
public final class ReliabilityCalculator {

    public static final String LABEL = "DEMONSTRATION DATA - not production statistics";
    public static final String MTTR_FORMULA =
            "MTTR = sum(recovery_completed_at - detected_at) over RECOVERED failure events / number of RECOVERED failure events";
    static final String EXCLUDED_OPEN = "OPEN failure events of active runs";
    static final String EXCLUDED_SUPERSEDED = "stage generation superseded by replanning before recovery";
    static final List<String> LIMITATIONS = List.of(
            "Synthetic workloads and injected faults (simulation options) on a single machine; not production statistics.",
            "Runs share one in-process engine and one H2 database; timings include test-harness and scheduling overhead.",
            "Human wait is taken from AWAITING_DECISION intervals in the audit trail; paused time is counted as latency.",
            "Rollbacks count capability release changes whose reason starts with 'rollback:' on the GLOBAL audit chain.");

    private ReliabilityCalculator() {
    }

    /** One run: terminal outcome ({@code null} while active), simulation flag, timing, human waits, compensation. */
    public record RunFact(UUID runId, String terminalOutcome, boolean simulated, Instant startedAt, Instant completedAt,
            List<Interval> humanWaits, boolean compensated) {
    }

    public record Interval(Instant from, Instant to) {
    }

    public record AttemptFact(int attemptNo, boolean fallback) {
    }

    public record FailureFact(UUID runId, String stageKey, String cause, boolean simulated, String status, Instant detectedAt,
            Instant recoveryCompletedAt, String mechanism, String excludedReason) {
    }

    public record Report(Instant generatedAt, String label, Population population, Runs runs, Stages stages,
            Compensations compensations, Mttr mttr, Unrecovered unrecoveredFailures, List<Exclusion> exclusions, Latency latency,
            List<String> limitations) {
    }

    public record Population(int runsConsidered, int terminalRuns, int activeRuns, int simulatedRuns) {
    }

    public record Runs(int completed, int rejected, int safeStopped, double successRate, double failureRate) {
    }

    public record Stages(int attempts, int retries, double retryFrequency, int fallbacks) {
    }

    public record Compensations(int runsWithCompensation, double compensationFrequency, int rollbacks) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Mttr(int recoveredEvents, long totalRecoveryMillis, Double mttrMillis, List<Long> recoveryDurationsMillis,
            Map<String, Integer> byMechanism, String formula) {
    }

    public record Unrecovered(int count, List<UnrecoveredEvent> events) {
    }

    public record UnrecoveredEvent(String runId, String stageKey, String cause, boolean simulated, Instant detectedAt) {
    }

    public record Exclusion(String reason, int count) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Latency(int sampleSize, Long minMillis, Long p50Millis, Long p95Millis, Long maxMillis, boolean excludesHumanWait) {
    }

    public static Report compute(List<RunFact> runs, List<AttemptFact> attempts, List<FailureFact> failures, int rollbacks,
            Instant generatedAt) {
        int terminal = (int) runs.stream().filter(r -> r.terminalOutcome() != null).count();
        int completed = count(runs, "COMPLETED");
        int rejected = count(runs, "REJECTED");
        int safeStopped = count(runs, "SAFE_STOPPED");
        Population population = new Population(runs.size(), terminal, runs.size() - terminal,
                (int) runs.stream().filter(RunFact::simulated).count());

        int retries = (int) attempts.stream().filter(a -> a.attemptNo() > 1 && !a.fallback()).count();
        int fallbacks = (int) attempts.stream().filter(AttemptFact::fallback).count();
        int compensated = (int) runs.stream().filter(r -> r.terminalOutcome() != null && r.compensated()).count();

        List<Long> durations = new ArrayList<>();
        Map<String, Integer> byMechanism = new LinkedHashMap<>();
        List<UnrecoveredEvent> unrecovered = new ArrayList<>();
        int open = 0;
        int superseded = 0;
        for (FailureFact f : failures) {
            if (f.excludedReason() != null) {
                superseded++;
            } else if ("OPEN".equals(f.status())) {
                open++;
            } else if ("RECOVERED".equals(f.status())) {
                durations.add(Duration.between(f.detectedAt(), f.recoveryCompletedAt()).toMillis());
                byMechanism.merge(f.mechanism() == null ? "UNKNOWN" : f.mechanism(), 1, Integer::sum);
            } else {
                unrecovered.add(new UnrecoveredEvent(f.runId().toString(), f.stageKey(), f.cause(), f.simulated(), f.detectedAt()));
            }
        }
        long total = durations.stream().mapToLong(Long::longValue).sum();
        Mttr mttr = new Mttr(durations.size(), total, durations.isEmpty() ? null : (double) total / durations.size(), durations,
                byMechanism, MTTR_FORMULA);

        return new Report(generatedAt, LABEL, population,
                new Runs(completed, rejected, safeStopped, ratio(completed, terminal), ratio(safeStopped, terminal)),
                new Stages(attempts.size(), retries, ratio(retries, attempts.size()), fallbacks),
                new Compensations(compensated, ratio(compensated, terminal), rollbacks),
                mttr, new Unrecovered(unrecovered.size(), unrecovered),
                List.of(new Exclusion(EXCLUDED_OPEN, open), new Exclusion(EXCLUDED_SUPERSEDED, superseded)),
                latency(runs), LIMITATIONS);
    }

    private static int count(List<RunFact> runs, String outcome) {
        return (int) runs.stream().filter(r -> outcome.equals(r.terminalOutcome())).count();
    }

    private static double ratio(int numerator, int denominator) {
        return denominator == 0 ? 0.0 : (double) numerator / denominator;
    }

    /** Completed runs only: wall time minus the union of human-wait intervals inside it; nearest-rank percentiles. */
    private static Latency latency(List<RunFact> runs) {
        List<Long> samples = runs.stream()
                .filter(r -> "COMPLETED".equals(r.terminalOutcome()) && r.startedAt() != null && r.completedAt() != null)
                .map(r -> Duration.between(r.startedAt(), r.completedAt()).toMillis() - waitMillis(r))
                .sorted().toList();
        if (samples.isEmpty()) {
            return new Latency(0, null, null, null, null, true);
        }
        return new Latency(samples.size(), samples.getFirst(), rank(samples, 0.50), rank(samples, 0.95), samples.getLast(), true);
    }

    private static long rank(List<Long> sorted, double percentile) {
        int index = (int) Math.ceil(percentile * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
    }

    private static long waitMillis(RunFact run) {
        List<Interval> clipped = run.humanWaits().stream()
                .map(i -> new Interval(max(i.from(), run.startedAt()), i.to() == null ? run.completedAt() : min(i.to(), run.completedAt())))
                .filter(i -> i.from().isBefore(i.to()))
                .sorted(Comparator.comparing(Interval::from)).toList();
        long waited = 0;
        Instant start = null;
        Instant end = null;
        for (Interval i : clipped) {
            if (end == null || i.from().isAfter(end)) {
                if (end != null) {
                    waited += Duration.between(start, end).toMillis();
                }
                start = i.from();
                end = i.to();
            } else if (i.to().isAfter(end)) {
                end = i.to();
            }
        }
        if (end != null) {
            waited += Duration.between(start, end).toMillis();
        }
        return waited;
    }

    private static Instant max(Instant a, Instant b) {
        return a.isAfter(b) ? a : b;
    }

    private static Instant min(Instant a, Instant b) {
        return a.isBefore(b) ? a : b;
    }
}
