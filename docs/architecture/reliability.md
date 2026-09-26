# Reliability: retry, fallback, compensation, safe-stop, recovery

How a run behaves when things go wrong. Decisions: ADR-009 (retry, fallback, safe-stop), ADR-010
(rollback versus compensation), ADR-006 (persisted workflow state). All ADRs are **Proposed**,
pending the candidate's acceptance. Drill evidence: [drills.md](../scenarios/drills.md). Operator
procedures: [runbook](../operations/runbook.md).

## 1. Failure taxonomy

| Failure | Class | Handling | Failure-event cause |
|---|---|---|---|
| `TransientStageException`, transient data-access failure (lock, query timeout, pool) | TRANSIENT | bounded retry with backoff | `AGENT_ERROR` |
| Attempt timeout | TRANSIENT | attempt interrupted, late result discarded, retry | `TIMEOUT` |
| `PermanentStageException`, invalid input, exit criteria unmet, permission denied | PERMANENT | fallback if the stage has one, else stage `FAILED` → safe-stop | `AGENT_ERROR` |
| Post-release verification failure | PERMANENT | release state rolled back inside `RELEASE`, then safe-stop | `VERIFICATION_FAILURE` |
| Process interruption | TRANSIENT | on restart the attempt becomes `INTERRUPTED` and is retried | `PROCESS_INTERRUPTION` |
| Compensation failure | — | each action tried 3 times, then `SAFE_STOPPED` with `manualInterventionRequired` | — |
| Gate deadline, rejected policy exception, operator request | governance stop | safe-stop | — |
| Policy block, human rejection | governance outcomes, not failures | exception decision / `REJECTED` | — |

`FailureClassifier` classifies exceptions: a failure is transient if it, or any of its causes, is
transient. Everything else is permanent, so a failure is retried only when it is known to be
retryable.

## 2. Retry and timeout (FR-REL-01, FR-REL-02, FR-REL-09)

Retry and timeout settings live per stage under `app.orchestration.stages`. `defaults` applies to
every stage, and `overrides.<STAGE_TYPE>` replaces values for one stage:

| Setting | Default | Source |
|---|---|---|
| `max-attempts` | 3 | PVT-11 |
| `initial-backoff` | 200 ms, doubling after each failure | PVT-11 |
| `max-backoff` | 5 s | PVT-11 |
| `timeout` | 30 s per attempt | PVT-12 |

- **Transient failure with attempts left:** the stage goes `RUNNING → RETRY_WAIT` with a persisted
  `next_attempt_at`. The coordinator schedules a wake-up on the `TaskScheduler`. The retry is
  audited as `RETRY_SCHEDULED` with the backoff.
- **Timeout:** the dispatcher interrupts the attempt and reports it as `TIMED_OUT`. Whatever the
  agent returns afterwards is recorded as `ATTEMPT_DISCARDED` and never applied.
- **Duplicate protection:** every result carries `(generation, attemptNo)`. A result for any other
  pair is discarded.
- **Query bound:** a JDBC query timeout (15 s) bounds database calls made through the port (review
  RC-6).

## 3. Fallback (FR-REL-03)

| Stage | Fallback agent | Degraded output |
|---|---|---|
| `DOCUMENTATION` | `template-documenter@1.0` | template document; repository check still real; marker `degraded=true` |
| `FINAL_SUMMARY` | `minimal-summarizer@1.0` | artifact list with fingerprints |
| verification stages (`TESTING`, `SECURITY_VERIFICATION`, `REGRESSION_TESTING`, `VALIDATION`, `COMPLIANCE_EVALUATION`, `RELEASE`) | none | never degrade |

A permanent failure, or exhausted retries, switches the stage to its fallback **once**. The switch
is recorded as a `FALLBACK_USED` decision with the primary failure. The stage is marked `degraded`.
Validation lists the degraded stage, and readiness becomes `READY_WITH_ACCEPTED_LIMITATIONS`, so
degradation is never silent. With a fallback, a stage runs at most `max-attempts + 1` times.

## 4. Rollback versus compensation (ADR-010)

| Side effect | Mechanism | Where |
|---|---|---|
| Capability release flag | **rollback** to the previous state, only while the flag still holds this run's value (`changed_by_run`); otherwise the conflict is recorded and the flag is kept | inside `RELEASE` after a failed verification; `CompensationCoordinator` at safe-stop |
| Synthetic probe links | **compensation**: delete the rows of this run (`synthetic_run_id`) | after every probe section; `CompensationCoordinator` at safe-stop and rejection |
| Audit events, decisions | none: append-only; decisions are superseded or invalidated, never deleted | — |
| Consumer data | never touched by automation | — |

`CompensationCoordinator` runs its actions in reverse completion order: release first, then synthetic
data. It tries each action up to 3 times (PVT-16) and audits every action as `COMPENSATION_ACTION`
with `OK`, `CONFLICT`, or `FAILED`.

**Limitation:** the release record does not store the parameters in effect before the release. So
a capability that was *already released* before the run cannot be rolled back automatically: this
is reported as `FAILED` and requires manual intervention.

## 5. Safe-stop (FR-REL-06)

One procedure serves every trigger (`STAGE_FAILED`, `GATE_DEADLINE`, `OPERATOR_REQUEST`,
`POLICY_EXCEPTION_REJECTED`, `CLARIFICATION_ROUNDS_EXCEEDED`). It runs under the run lock in one
transaction:

1. Mark the run as stopping, so no new dispatch happens.
2. Cancel open stages. In-flight attempts see their cancellation signal, and their late results are
   discarded.
3. Move the run to `COMPENSATING` and run compensation (section 4).
4. Terminate the run `SAFE_STOPPED`. If compensation failed, set `manualInterventionRequired`.
5. Record a `SAFE_STOP` decision with the trigger and the compensation actions.
6. Produce the final summary (the fallback is used if the primary agent fails).
7. Emit `RUN_TERMINATED`.

A second trigger on a terminal run changes nothing, so every run has exactly one terminal outcome.

## 6. Operator controls (FR-REL-07)

`RELEASE_OWNER` only; every action is recorded as an `OPERATOR_ACTION` decision.

- **pause:** from running or waiting. No new stage starts, and in-flight attempts finish.
- **resume:** from paused.
- **safe-stop:** from running, waiting, or paused.

## 7. Recovery after restart (FR-REL-08, NFR-RCV-01)

State is persisted before every side effect. On `ApplicationReadyEvent`, `RecoveryService` handles
each non-terminal run:
- Attempts without `finished_at` become `INTERRUPTED`: a transient failure with cause
  `PROCESS_INTERRUPTION`, which counts toward the stage's attempts.
- The stage is retried, and the failure event records the mechanism `RESUME`.
- Due retries are dispatched.
- Waiting gates keep their deadlines.
- Paused runs stay paused.
- Completed stages are never repeated.

When the dispatcher shuts down, it drops results instead of applying them, as a process kill would,
so the attempt stays open for recovery. `COMPENSATING` is never committed on its own, because
compensation and the terminal transition share one transaction.

## 8. Fault injection (FR-REL-11)

Faults are accepted only when `app.orchestration.fault-injection.enabled` is true (demo and test
profiles). Otherwise the request is refused with `400 FAULT_INJECTION_DISABLED`. Invalid fault
specifications are refused with `400 VALIDATION_FAILED`.

| Fault | Effect |
|---|---|
| `TRANSIENT_ERROR` / `PERMANENT_ERROR` | the attempt fails with that class instead of calling the agent |
| `DELAY` | the agent runs after `delayMillis` |
| `TIMEOUT` | the attempt hangs until the timeout interrupts it |
| `VERIFICATION_FAILURE` | every redirect the stage's probes observe fails (probe stages only) |
| `POLICY_FAILURE` | the named policy fails in every evaluation of the run (`COMPLIANCE_EVALUATION` only) |
| `COMPENSATION_FAILURE` | the synthetic-data compensation fails `occurrences` times |

Each fault affects the first `occurrences` attempts of its stage. Attempts, failure events, and
policy evaluations caused by a fault are flagged simulated, and the run view shows
`simulated: true`.

## 9. Known limitations

- **In-process locks.** Run locks, the probe mutex (`SyntheticScope`), retry wake-ups, and the
  deadline sweeper are in-process. That is valid for the single-process H2 deployment (ADR-003,
  backlog BL-02), not for several instances.
- **No autonomy budget yet.** The autonomy budget (T074: 60 attempts or 10 minutes per run) is
  deferred by scope decision SD-1. Policy AUT-001 reports budget usage as an advisory only.
- **Scheduler restarts.** Wake-ups are scheduled in memory. After a restart, `RecoveryService`
  re-schedules them.
