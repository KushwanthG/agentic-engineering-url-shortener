# MTTR validation (T105)

> **DEMONSTRATION DATA - not production statistics.** Every figure below comes from one automated
> drill run on a developer machine, with injected faults and simulated human input. The figures show
> that the metric is computed correctly and can be reproduced. They say nothing about how reliable
> the system would be in production.

## 1. How to regenerate

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21.0.12.1"
.\mvnw.cmd -B -ntp test "-Dtest=ReliabilityDrillsE2ETest"
# -> target/evidence/reliability/reliability-report.json  (provenance: git commit, JDK, time, simulatedInput=true)
```

The last ordered step of the drill suite reads `GET /api/v1/reliability/report` and asserts three
things:
- The report's MTTR equals its own listed durations divided by the recovered count.
- The retry and fallback drills appear as recovery mechanisms.
- The RDR-03 verification failure is listed as unrecovered, not averaged in.

Timings change on every run. The values below are from the run recorded on 2026-09-27 (report
`generatedAt` 2026-09-27T04:04:42Z, commit `14b7b82` plus the Phase 9 working tree).

## 2. Definitions (plan.md §7)

| Item | Definition |
|---|---|
| MTTR | Σ(`recovery_completed_at` − `detected_at`) over **RECOVERED** failure events ÷ number of RECOVERED events |
| `detected_at` | end of the first failed attempt of a stage generation |
| `recovery_started_at` | start of the next attempt (retry, fallback, or resume) |
| `recovery_completed_at` | end of the successful attempt |
| Unrecovered | status `UNRECOVERED` (the stage failed for good or the run ended first); listed, excluded from the denominator |
| Exclusions | `OPEN` events of active runs; events whose stage generation was superseded by replanning before recovery |
| Population | every run, attempt, and failure event in the database at report time |

Episode recording is verified by `FailureEventRecorderTest`. The formula is verified with fixed
fixtures and hand-computed expectations in `ReliabilityReportServiceTest`.

## 3. Inputs: the drill population

The drill suite runs in one application context, with SCN-A as the base requirement:

| Drill | Injected condition (simulated) | Expected failure episode |
|---|---|---|
| RDR-03 | post-release verification failure | RELEASE: unrecovered (rollback, safe-stop) |
| RDR-01 | transient TESTING errors | TESTING: recovered by RETRY |
| RDR-02 | permanent DOCUMENTATION error | DOCUMENTATION: recovered by FALLBACK |
| RDR-04 | unanswered gate, deadline passes | none (a governance stop, not a stage failure) |
| RDR-06 | failed mandatory policy; exception approved in one run, rejected in another | none (a policy block, not a stage failure) |
| RDR-07 | operator pause, resume, safe-stop | none |

Population in the report: 7 runs considered, all 7 terminal and all 7 simulated (RDR-06 submits two
runs). There were 76 stage attempts: 3 retries and 1 fallback.

## 4. Recovered-event population and the hand calculation

| # | Mechanism | Recovery duration (ms) |
|---|---|---|
| 1 | RETRY (RDR-01) | 236 |
| 2 | FALLBACK (RDR-02) | 89 |

- Σ durations = 236 + 89 = **325 ms** (report `totalRecoveryMillis` = 325)
- Recovered count = **2** (report `recoveredEvents` = 2)
- MTTR = 325 ÷ 2 = **162.5 ms** (report `mttrMillis` = 162.5) ✔

The 3 retry attempts in the report break down as follows (checked against the drill timelines in
`target/evidence/drills/`):
- TESTING attempts 2 and 3 of RDR-01. Both belong to one stage generation, so they form a single
  RETRY episode.
- COMPLIANCE_EVALUATION attempt 2 of RDR-06. It is a re-evaluation after the exception decision, not
  a recovery from a failure, so it has no failure episode.

Retry frequency counts every attempt after the first that is not a fallback (plan.md §7, "retry
attempts ÷ all attempts"), so that re-evaluation is included in it.

## 5. Unrecovered failures (not in the denominator)

| Run | Stage | Cause | Simulated |
|---|---|---|---|
| `2fde2b74-…` (RDR-03) | RELEASE | VERIFICATION_FAILURE | yes |

## 6. Exclusions

| Reason | Count |
|---|---|
| OPEN failure events of active runs | 0 |
| stage generation superseded by replanning before recovery | 0 |

Neither exclusion occurs in the drills. Both are exercised with fixtures in
`ReliabilityReportServiceTest` and `FailureEventRecorderTest`.

## 7. Other rates in the same report

| Metric | Value | Hand check |
|---|---|---|
| Success rate | 0.4286 | 3 completed ÷ 7 terminal |
| Failure rate | 0.5714 | 4 safe-stopped ÷ 7 terminal |
| Retry frequency | 0.0395 | 3 retry attempts ÷ 76 attempts (see §4) |
| Compensation frequency | 0.5714 | 4 runs with compensation ÷ 7 terminal |
| Rollbacks | 1 | RDR-03 release rollback (GLOBAL chain, reason `rollback: …`) |
| Latency, completed runs, human wait excluded | min 583, p50 712, p95 1116, max 1116 ms (n = 3) | nearest rank over 3 samples |

## 8. Limitations

- **Synthetic data only.** The workloads are synthetic, the faults are injected, the machine is
  single, and one H2 database is shared. The durations include scheduling and test-harness overhead
  (for example, the retry backoff is shortened to 50 ms in the drill suite).
- **RESUME is missing from this population.** Recovery after a process restart (RDR-05) runs in its
  own application context (`RestartResumeTest`), so its RESUME episode does not appear in this
  report. `FailureEventRecorderTest` verifies the RESUME mechanism.
- **Latency.** Human wait is derived from the `AWAITING_DECISION` intervals in the audit trail. Time
  spent paused is still counted as latency.
- **Sample size.** A population this small (2 recovered events) supports no statistical claim.
