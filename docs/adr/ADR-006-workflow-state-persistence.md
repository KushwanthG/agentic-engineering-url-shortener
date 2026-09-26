# ADR-006: Relational Workflow State with Per-Run Coordination and Optimistic Locking

## Status

Proposed (2026-09-26) — pending acceptance at Gate G4.

## Context

Run and stage state must be persisted at every transition so runs can be inspected and resumed
after interruption (FR-ORC-08, FR-REL-08). Attempts complete asynchronously on worker threads,
gate decisions arrive over HTTP concurrently, and retries fire from a scheduler. All of these
mutate the same run.

## Decision Drivers

- No lost or conflicting updates; exactly one gate decision wins (edge case in spec)
- Every transition persisted before its side effect
- Simple recovery semantics after a crash
- Single-instance scope (ASM-01) without closing the door to multiple instances

## Options Considered

| Option | Advantages | Disadvantages / risks |
|--------|------------|-----------------------|
| **Relational rows (run, stage, attempt) + in-JVM per-run lock + JPA `@Version`** | simple, inspectable with SQL, transactional | the in-JVM lock is single-instance only |
| Event sourcing (append events, derive state) | full history by construction | more code (projections, snapshots); the audit trail already provides history |
| Database row locks (`SELECT … FOR UPDATE`) only | multi-instance safe | lock waits under H2 file mode; more complex error handling |

## Decision

- Tables per data-model.md. Every coordinator step runs in a short `TransactionTemplate`
  transaction under a `ReentrantLock` held per run id (a striped lock map).
- `@Version` on `WorkflowRun` and `StageNode` detects any write outside the lock, such as a
  concurrent gate decision. The loser gets `409 CONCURRENT_DECISION` and nothing is persisted.
- Transition order: persist the new state (for example `RUNNING` + attempt row) → commit →
  dispatch the side effect (the agent execution). A crash therefore leaves an attempt without
  `finished_at`, which recovery detects.
- Attempt results carry `(generation, attemptNo)`; the coordinator discards mismatches.

## Rationale

The chosen model is the smallest one that makes every transition durable and serialized per run,
with SQL-inspectable state for reviewers.

## Consequences

- **Positive**: deterministic recovery; clear audit of state; simple queries for the API.
- **Negative**: horizontal scaling of the control plane would need distributed coordination
  (leases or row locks), backlog BL-05.
- **Operational**: state inspectable directly in H2 if needed.
- **Testing**: concurrent decision tests; crash-simulation (restart) tests.
- **Governance**: no state change without an audit event, because each transition writes both in
  the same transaction.

## Risks and Mitigations

| Risk | Mitigation |
|------|------------|
| Two processes on one database | H2 file lock prevents it (fails fast) |
| Long transactions blocking redirects | engine transactions are short; agents run outside transactions |

## Reversibility

Medium: moving to database-level leases for multi-instance operation changes only the
coordinator's locking strategy.

## Traceability

- Requirements: FR-ORC-08, FR-ORC-11, FR-REL-08, FR-REL-09, FR-GOV-03 (concurrency), NFR-OBS-01
- Plan: §3 control flow; §6 idempotency; data-model.md
- Tasks: orchestration persistence and coordinator tasks

## Validation

`ConcurrentGateDecisionTest`, `StaleAttemptResultTest`, `RestartResumeTest`, reconstruction test
(NFR-OBS-01).
