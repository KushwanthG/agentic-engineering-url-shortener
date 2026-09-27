package com.agentic.urlshortener.orchestration.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.agentic.urlshortener.contract.OpenApiContract;
import com.agentic.urlshortener.orchestration.metrics.ReliabilityCalculator.AttemptFact;
import com.agentic.urlshortener.orchestration.metrics.ReliabilityCalculator.FailureFact;
import com.agentic.urlshortener.orchestration.metrics.ReliabilityCalculator.Interval;
import com.agentic.urlshortener.orchestration.metrics.ReliabilityCalculator.RunFact;
import com.agentic.urlshortener.support.IntegrationTest;
import com.agentic.urlshortener.support.Tokens;

/**
 * T104 (FR-AUD-04, SC-008, NFR-RCV-02; RDR-01..RDR-07): the reliability report of plan.md §7,
 * computed from fixed fixtures whose expected values are worked out by hand in the comments. MTTR
 * is the sum of recovered durations divided by the recovered count. Unrecovered events are listed
 * and kept out of the denominator. OPEN and superseded events are excluded, with counts. Latency
 * excludes human wait. The report states its population and its demonstration-data label. The
 * endpoint response is validated against the contract.
 */
@IntegrationTest
@Tag("FR-AUD-04")
@Tag("SC-008")
@Tag("NFR-RCV-02")
class ReliabilityReportServiceTest {

    private static final Instant T0 = Instant.parse("2026-09-27T10:00:00Z");
    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID C = UUID.fromString("00000000-0000-0000-0000-00000000000c");
    private static final UUID D = UUID.fromString("00000000-0000-0000-0000-00000000000d");
    private static final UUID E = UUID.fromString("00000000-0000-0000-0000-00000000000e");

    @Autowired private MockMvc mvc;

    private static Instant at(long millis) {
        return T0.plusMillis(millis);
    }

    private static FailureFact failure(UUID run, String status, long detected, Long completed, String mechanism, String excluded,
            boolean simulated) {
        return new FailureFact(run, "DESIGN", "AGENT_ERROR", simulated, status, at(detected), completed == null ? null : at(completed),
                mechanism, excluded);
    }

    private static ReliabilityCalculator.Report fixtureReport() {
        List<RunFact> runs = List.of(
                // A: completed in 10 000 ms, with two overlapping human waits covering 1 000..4 000 (3 000 ms) -> latency 7 000
                new RunFact(A, "COMPLETED", true, at(0), at(10_000),
                        List.of(new Interval(at(1_000), at(3_000)), new Interval(at(2_000), at(4_000))), false),
                // B: completed in 5 000 ms without waits -> latency 5 000
                new RunFact(B, "COMPLETED", false, at(0), at(5_000), List.of(), false),
                // C: safe-stopped with compensation
                new RunFact(C, "SAFE_STOPPED", true, at(0), at(2_000), List.of(), true),
                // D: rejected at a gate
                new RunFact(D, "REJECTED", false, at(0), at(1_000), List.of(), false),
                // E: still active
                new RunFact(E, null, false, at(0), null, List.of(), false));
        List<AttemptFact> attempts = List.of(new AttemptFact(1, false), new AttemptFact(2, false), new AttemptFact(3, false),
                new AttemptFact(1, false), new AttemptFact(2, true), new AttemptFact(1, false), new AttemptFact(1, false),
                new AttemptFact(1, false), new AttemptFact(1, false), new AttemptFact(1, false));
        List<FailureFact> failures = List.of(
                failure(A, "RECOVERED", 100, 400L, "RETRY", null, true),      // 300 ms
                failure(A, "RECOVERED", 1_000, 1_900L, "FALLBACK", null, true), // 900 ms
                failure(B, "RECOVERED", 0, 300L, "RETRY", null, false),        // 300 ms
                failure(C, "UNRECOVERED", 500, null, null, null, true),
                failure(C, "UNRECOVERED", 600, null, null, "re-planned after clarification", true),
                failure(E, "OPEN", 50, null, null, null, false));
        return ReliabilityCalculator.compute(runs, attempts, failures, 1, at(20_000));
    }

    @Test
    void mttrCountsOnlyRecoveredEventsAndListsTheUnrecovered() {
        ReliabilityCalculator.Report report = fixtureReport();
        // (300 + 900 + 300) / 3 = 500
        assertThat(report.mttr().recoveredEvents()).isEqualTo(3);
        assertThat(report.mttr().totalRecoveryMillis()).isEqualTo(1_500);
        assertThat(report.mttr().mttrMillis()).isEqualTo(500.0);
        assertThat(report.mttr().recoveryDurationsMillis()).containsExactly(300L, 900L, 300L);
        assertThat(report.mttr().byMechanism()).isEqualTo(Map.of("RETRY", 2, "FALLBACK", 1));
        assertThat(report.mttr().formula()).contains("over RECOVERED failure events / number of RECOVERED");

        assertThat(report.unrecoveredFailures().count()).isEqualTo(1);
        assertThat(report.unrecoveredFailures().events()).singleElement().satisfies(e -> {
            assertThat(e.runId()).isEqualTo(C.toString());
            assertThat(e.simulated()).isTrue();
        });
        assertThat(report.exclusions()).extracting(ReliabilityCalculator.Exclusion::reason, ReliabilityCalculator.Exclusion::count)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("OPEN failure events of active runs", 1),
                        org.assertj.core.groups.Tuple.tuple("stage generation superseded by replanning before recovery", 1));
    }

    @Test
    void runStageAndCompensationRatesUseTerminalRunsAndAllAttempts() {
        ReliabilityCalculator.Report report = fixtureReport();
        assertThat(report.population().runsConsidered()).isEqualTo(5);
        assertThat(report.population().terminalRuns()).isEqualTo(4);
        assertThat(report.population().activeRuns()).isEqualTo(1);
        assertThat(report.population().simulatedRuns()).isEqualTo(2);
        // 2 completed / 4 terminal; 1 safe-stopped / 4 terminal; rejected reported separately
        assertThat(report.runs().completed()).isEqualTo(2);
        assertThat(report.runs().rejected()).isEqualTo(1);
        assertThat(report.runs().safeStopped()).isEqualTo(1);
        assertThat(report.runs().successRate()).isEqualTo(0.5);
        assertThat(report.runs().failureRate()).isEqualTo(0.25);
        // retries: attempt numbers above 1 that are not fallbacks = 2 of 10 attempts; 1 fallback
        assertThat(report.stages().attempts()).isEqualTo(10);
        assertThat(report.stages().retries()).isEqualTo(2);
        assertThat(report.stages().retryFrequency()).isEqualTo(0.2);
        assertThat(report.stages().fallbacks()).isEqualTo(1);
        // 1 run with compensation / 4 terminal runs; 1 rollback supplied
        assertThat(report.compensations().runsWithCompensation()).isEqualTo(1);
        assertThat(report.compensations().compensationFrequency()).isEqualTo(0.25);
        assertThat(report.compensations().rollbacks()).isEqualTo(1);
    }

    @Test
    void latencyOfCompletedRunsExcludesHumanWaitAndTheReportIsLabeled() {
        ReliabilityCalculator.Report report = fixtureReport();
        // completed runs only: A = 10 000 - 3 000 (merged waits) = 7 000; B = 5 000
        assertThat(report.latency().sampleSize()).isEqualTo(2);
        assertThat(report.latency().excludesHumanWait()).isTrue();
        assertThat(report.latency().minMillis()).isEqualTo(5_000L);
        assertThat(report.latency().p50Millis()).isEqualTo(5_000L);
        assertThat(report.latency().p95Millis()).isEqualTo(7_000L);
        assertThat(report.latency().maxMillis()).isEqualTo(7_000L);
        assertThat(report.label()).isEqualTo("DEMONSTRATION DATA - not production statistics");
        assertThat(report.generatedAt()).isEqualTo(at(20_000));
        assertThat(report.limitations()).anyMatch(l -> l.contains("injected faults")).anyMatch(l -> l.contains("single"));
    }

    @Test
    void anEmptyPopulationHasNoRatesToDivideByZero() {
        ReliabilityCalculator.Report report = ReliabilityCalculator.compute(List.of(), List.of(), List.of(), 0, T0);
        assertThat(report.runs().successRate()).isZero();
        assertThat(report.stages().retryFrequency()).isZero();
        assertThat(report.mttr().mttrMillis()).isNull();
        assertThat(report.latency().sampleSize()).isZero();
        assertThat(report.latency().p50Millis()).isNull();
    }

    @Test
    void theReportEndpointMatchesTheContractForEveryReadRole() throws Exception {
        MvcResult result = mvc.perform(get("/api/v1/reliability/report").header(HttpHeaders.AUTHORIZATION, Tokens.bearer(Tokens.AUDITOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label").value("DEMONSTRATION DATA - not production statistics"))
                .andExpect(jsonPath("$.mttr.formula").isString())
                .andReturn();
        OpenApiContract.assertResponseMatches("GET", "/api/v1/reliability/report", result);
        mvc.perform(get("/api/v1/reliability/report").header(HttpHeaders.AUTHORIZATION, Tokens.bearer(Tokens.REQUESTER)))
                .andExpect(status().isOk());
    }
}
