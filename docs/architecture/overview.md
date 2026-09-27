# Architecture overview

This overview covers the system's components, its orchestration model and control flow, the key
decisions (every ADR is still **Proposed**; see [the ADR index](../adr/README.md)), and the scaling
path. For depth, see the following documents:
- [orchestration.md](orchestration.md): engine, stage table, state machines, scheduling, re-planning
- [governance.md](governance.md): gates, roles, approval binding, invariants, evidence API
- [reliability.md](reliability.md): retry, fallback, compensation, safe-stop, recovery,
  observability

## 1. Two planes in one deployable (ADR-001)

| Plane | Package | Responsibility |
|---|---|---|
| Application plane | `com.agentic.urlshortener.shortener` | The URL shortener: create, redirect, stats, URL safety policy, rate limits, idempotency, click analytics, and three **governed capabilities** (`custom-alias`, `click-limit`, `default-expiry`) that stay unreleased until a run releases them (ADR-018). |
| Control plane | `com.agentic.urlshortener.orchestration` | Takes a requirement through a persisted, gated, dependency-aware workflow of agents and releases capabilities of the application plane. |
| Shared | `com.agentic.urlshortener.common` | Security (hashed bearer tokens and roles), problem-details errors, correlation ids, canonical JSON. |

The control plane reaches the shortener **only** through `ApplicationPlanePort`, which is
implemented in `orchestration.integration`. Each agent receives a `PermissionScopedPort` that
allows only its declared permissions (FR-GOV-01). `ArchitectureTest` enforces these rules:
- `shortener` does not depend on `orchestration`;
- only `integration` calls the shortener;
- agents cannot reach governance, security, repositories, or the coordinator;
- there are no package cycles.

## 2. Control-plane components

| Component | Role |
|---|---|
| `WorkflowService` / `WorkflowController` | Accept a requirement, create the run, the requirement version 1, and the plan version 1 (`PlanFactory`, validated by `PlanValidator`). |
| `RunCoordinator` | The engine. For each run it holds a lock and runs a *scheduling cycle* in one transaction: it evaluates entry and exit criteria and conditions, opens gates, and starts attempts. It dispatches agents after commit and applies their results under the lock, using `(generation, attemptNo)` staleness checks. |
| `StageDispatcher` | A bounded agent pool with a timeout per attempt and one `sdlc.stage` observation per attempt. |
| Agents (`orchestration.agent`) | Deterministic, knowledge-driven workers (ADR-017), one or more per stage type. Their inputs are declared artifacts and the knowledge base; their output is schema-validated artifacts. Fallback agents exist for documentation, summary, and impact analysis. |
| Knowledge (`orchestration.knowledge`) | Capability catalog, ambiguity lexicon, and codebase scanner (an import graph, for brownfield impact analysis). |
| Governance | `GateService` (roles, separation of duties, deadlines, fingerprint-bound approvals), `ClarificationService`, `ChangeRequestService`, `DeadlineSweeper`. |
| Policy (`orchestration.policy`) | A versioned YAML policy set with a Java rule per policy (ADR-019), plus the readiness evaluator and policy exceptions. |
| Planning | `ReplanningService` (plan versions and diffs, closure re-open, change gate) and `InputFingerprinter` (content-addressed reuse, ADR-011). |
| Reliability | `RetryPolicy`, `FailureClassifier`, `FaultInjector` (simulation only), `CompensationCoordinator`, `SafeStopService`, `RecoveryService` (restart), `FailureEventRecorder` (MTTR). |
| Audit and evidence | `AuditService` (SHA-256 hash chain per run plus a GLOBAL chain), `LineageService`, `EvidenceQueryService`, `ReliabilityReportService`, `OrchestrationMeters`. |

## 3. Orchestration model and control flow (ADR-005, ADR-007)

A run is a **persisted DAG of stage nodes**, and the plan graph is versioned. For a new capability:

```text
REQUIREMENT_INGESTION → REQUIREMENT_ANALYSIS → CLARIFICATION? ─┬→ DECOMPOSITION ─────┐
                                                               ├→ THREAT_ASSESSMENT ─┼→ DESIGN → ARCHITECTURE_APPROVAL?
                                                               └→ IMPACT_ANALYSIS* ──┘               │
        ┌──────────────────────────────────────────────────────────────────────────────────────┘
        ├→ IMPLEMENTATION ─┬→ TESTING ────────────┐
        │                  ├→ REGRESSION_TESTING* ├→ VALIDATION → COMPLIANCE_EVALUATION → RELEASE_APPROVAL → RELEASE → FINAL_SUMMARY
        │                  └→ SECURITY_VERIFICATION┤
        └→ DOCUMENTATION ─────────────────────────┘
   ? conditional gate (skipped when its condition is false)    * changes to existing behavior only
```

The control flow of one run:
1. **Start.** The submission commits the run and its plan. The coordinator then schedules every
   stage that is ready; independent stages run in parallel (for example, testing, security
   verification, and documentation).
2. **Gates.** A gate waits for a human decision. Only the gate's downstream closure waits; the run
   status is `AWAITING_HUMAN`. A decision is bound to the fingerprints of the artifacts reviewed,
   and it becomes invalid if those artifacts change.
3. **Failures.** A transient failure is retried with bounded backoff. A permanent failure, or
   exhausted retries, switch to the stage's declared fallback, which lowers readiness. Otherwise the
   stage fails and the run is **safe-stopped**: stages are cancelled, synthetic data and release
   state are compensated, a `SAFE_STOP` decision is recorded, and the final summary is produced.
4. **Compliance.** The pinned policy set is evaluated. A failed mandatory policy blocks the release
   unless a human approves a time-bound exception with a compensating control.
5. **Release.** The release agent sets the capability's release flag with the designed parameters,
   verifies it with a smoke probe, and rolls it back if that verification fails.
6. **Change.** Clarifications and change requests create new requirement and plan versions. Only
   the affected closure is re-opened, and stages whose input fingerprints are unchanged are reused.

Every state change and its audit event commit together.

## 4. Key decisions

All ADRs are Proposed and await G4.

| Decision | ADR | Why |
|---|---|---|
| Modular monolith with an enforced port between the planes | [ADR-001](../adr/ADR-001-application-architecture.md) | one deployable for the timebox; the boundary can later become a network API |
| Custom persisted DAG engine instead of Temporal or Camunda | [ADR-005](../adr/ADR-005-orchestration-model.md) | explicit gates, fingerprints, and evidence without a second platform |
| Relational state, a per-run lock, dispatch after commit | [ADR-006](../adr/ADR-006-workflow-state-persistence.md) | crash-consistent state; recovery resumes interrupted attempts |
| Fingerprint-bound human approvals, separation of duties, deadlines | [ADR-008](../adr/ADR-008-human-approval-model.md) | no approval survives a change to what was approved |
| Classified failures, bounded retries, fallbacks, safe-stop | [ADR-009](../adr/ADR-009-retry-fallback-safe-stop.md), [ADR-010](../adr/ADR-010-rollback-versus-compensation.md) | predictable failure behavior, and honest wording for rollback versus compensation |
| Content-addressed re-planning | [ADR-011](../adr/ADR-011-dynamic-replanning.md) | re-plan without redoing unaffected work |
| Hash-chained audit; metrics computed from evidence | [ADR-012](../adr/ADR-012-observability-and-audit.md) | tamper evidence; reproducible MTTR |
| Deterministic, knowledge-driven agents | [ADR-017](../adr/ADR-017-deterministic-agents.md) | reproducible runs and tests; no model dependency |
| Governed capability flags with in-process preview | [ADR-018](../adr/ADR-018-capability-release.md) | agents verify unreleased behavior without exposing it |

## 5. Stateless request tier and the horizontal-scaling path (NFR-SCA-01)

**What already supports scaling:**
- The REST tier holds no session state. Every request carries a bearer token.
- All run state lives in the database: runs, nodes, attempts, artifacts, decisions, and audit.

**What pins the prototype to one process** (ADR-003, backlog BL-02):
- the per-run locks;
- the scheduler (retry wake-ups, deadline sweeper);
- the probe mutex;
- the embedded H2 file database.

**Path to several instances:**
1. Move to PostgreSQL; the migrations run in PostgreSQL mode.
2. Replace the in-process run locks with database row locks (`SELECT … FOR UPDATE` on the run) or
   advisory locks.
3. Run the scheduler as leader-elected (for example ShedLock), or claim due work with
   `FOR UPDATE SKIP LOCKED`.
4. Keep the redirect path separate from the control plane so it can be scaled on its own. The
   planes already share no in-memory state.
