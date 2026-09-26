# ADR-009: Classified Failures, Bounded Retries, Declared Fallbacks, and Deterministic Safe-Stop

## Status

Proposed (2026-09-26) — pending acceptance at Gate G4.

## Context

The orchestration must include bounded retries, timeouts, fallback, and safe-stop controls with
deterministic terminal outcomes (A§4.4; FR-REL-01..11; constitution VIII).

## Decision Drivers

- Boundedness (no unlimited retries or unbounded agent effort)
- Correct classification (retry only what can succeed on retry)
- Visible degradation (fallbacks must not hide quality loss)
- Deterministic, auditable stop behavior

## Options Considered

| Option | Advantages | Disadvantages / risks |
|--------|------------|-----------------------|
| **Per-stage policy (attempts, timeout, backoff) + exception classification + fallback registry + safe-stop service + run autonomy budget** | explicit, configurable, testable | configuration surface |
| Global retry policy (e.g. Spring Retry on everything) | little code | retries permanent failures; hides degradation |
| No retries; fail fast | simplest | cannot demonstrate recovery or MTTR |

## Decision

- Classification (`FailureClassifier`): transient = `TransientStageException`, timeouts,
  `TransientDataAccessException`, lock acquisition failures; everything else permanent (a safe
  default).
- Retry: per-stage `maxAttempts` (default 3), exponential backoff (200 ms doubling, capped at
  5 s), scheduled via `RETRY_WAIT` with a persisted `next_attempt_at`.
- Timeout: per-attempt `CompletableFuture` timeout; the worker is interrupted, any late result is
  discarded, and the timeout counts as transient.
- Fallback: registry per stage type (`IMPACT_ANALYSIS` → catalog-only; `DOCUMENTATION` → template;
  `FINAL_SUMMARY` → minimal). Used after a permanent failure or retry exhaustion. Records a
  `FALLBACK_USED` decision, marks the stage `degraded`, and yields at best
  `READY_WITH_ACCEPTED_LIMITATIONS`. Verification stages have **no** fallback.
- Safe-stop triggers (FR-REL-06): a mandatory policy failure without an exception (on rejection or
  deadline), retries and fallback exhausted, a gate deadline, compensation failure, the autonomy
  budget (60 attempts, 10 minutes of processing per run), more than 3 clarification rounds, or an
  operator request. Procedure in plan §6.

## Rationale

Explicit classification and per-stage policies make every retry justified and bounded;
degradation stays visible all the way to the release decision.

## Consequences

- **Positive**: recovery evidence with MTTR data points; no silent quality loss.
- **Negative**: tuning values are assumptions (PVT-11/12/17) until ratified.
- **Testing**: retry-then-succeed, exhaustion, permanent-no-retry, timeout-with-late-result,
  fallback-degraded, budget exhaustion, each safe-stop trigger.
- **Governance**: fallback use is a recorded decision; readiness reflects it.

## Risks and Mitigations

| Risk | Mitigation |
|------|------------|
| Interrupted agent threads leave partial side effects | only `RELEASE` and probe stages have side effects; both are idempotent and compensable |
| A misclassified permanent error retried needlessly | default is permanent; transient requires an explicit exception type |

## Reversibility

High: policies are configuration.

## Traceability

- Requirements: FR-REL-01..03, FR-REL-06, FR-REL-09..11, FR-ORC-18, NFR-REL-02/03, NFR-AUT-02
- Plan: §6; stage specification table (§3)
- Tasks: reliability task group; drills RDR-01, RDR-02, RDR-04, RDR-07

## Validation

`RetryPolicyTest`, `StageTimeoutTest`, `FallbackTest`, `SafeStopTriggersTest`,
`AutonomyBudgetTest`, drill end-to-end tests.
