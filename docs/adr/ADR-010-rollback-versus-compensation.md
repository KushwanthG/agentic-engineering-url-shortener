# ADR-010: Rollback Only for System-Owned State; Compensation for Everything Else

## Status

Proposed (2026-09-26) — pending acceptance at Gate G4. Builds on Clarifications Q4 (provisional,
pending G3).

## Context

The constitution forbids claiming rollback where it is not technically feasible (VIII), and the
specification lists the cases where rollback is impossible (spec, Edge Cases; FR-REL-04/05/10,
FR-RDY-03). Runs have two kinds of side effects: capability release flags, which the system fully
owns, and data visible in the live store (synthetic probe links, click events, consumers' use of
a released capability).

## Decision Drivers

- Truthful mechanism labeling in evidence
- Never harming consumer data
- Idempotent, retryable undo operations

## Options Considered

| Option | Advantages | Disadvantages / risks |
|--------|------------|-----------------------|
| **Rollback of flag state; compensation for synthetic data and exposure; consumer data untouched** | truthful; safe | exposure window remains (documented) |
| Treat everything as rollback (delete links created during exposure) | "clean" state | destroys consumer data; irreversible; violates Q4 and FR-GOV-01 |
| No undo | simplest | violates FR-REL-04 and FR-RDY-03 |

## Decision

| Side effect | Mechanism | Operation |
|-------------|-----------|-----------|
| Capability release flag | **Rollback of release state** | restore the previous flag value and parameters (set, idempotent); stored per-link behavior keeps being honored |
| Synthetic probe data | **Compensation** | delete rows where `synthetic_run_id = run` (idempotent); performed after each probe attempt and re-checked by the finalizer |
| Capability exposure to consumers | **Compensation** | withdraw the capability for new use; record the exposure window in the release record and audit |
| Audit events, decisions | none | append-only; superseded, never removed |

`CompensationCoordinator` runs compensations in reverse completion order, retries each up to 3
times (PVT-16), records `COMPENSATION_EXECUTED` audit events plus a decision per action, and on
final failure safe-stops with `manualInterventionRequired = true`. The port offers no operation on
non-synthetic consumer data, so automated destruction is structurally impossible.

## Rationale

The chosen split labels each mechanism truthfully and keeps consumer data inviolable while still
undoing every system-owned side effect.

## Consequences

- **Positive**: the evidence states exactly what was undone and how.
- **Negative**: links created by consumers during an exposure window keep working after
  withdrawal, by design.
- **Testing**: RDR-03 (release verification failure → rollback → safe-stop), compensation-failure
  test, reverse-order test, synthetic-cleanup test.

## Risks and Mitigations

| Risk | Mitigation |
|------|------------|
| Rollback of a flag another run changed meanwhile | optimistic version on `capability_release`; rollback restores only if the flag still has this run's value, otherwise it records a conflict and flags manual intervention |

## Reversibility

High.

## Traceability

- Requirements: FR-REL-04, FR-REL-05, FR-REL-10, FR-RDY-03, FR-GOV-01, FR-CAP-01, FR-ORC-16
- Spec: Clarifications Q4; Edge Cases (rollback impossible)
- Plan: §6 rollback vs. compensation; §3 `RELEASE` stage
- Tasks: compensation task group; RDR-03

## Validation

`CompensationCoordinatorTest`, `ReleaseRollbackTest`, `SyntheticDataCleanupTest`, RDR-03
end-to-end test.
