# Orchestration engine (control plane)

How the control plane turns a requirement into a governed, audited run. Decisions: ADR-005 (persisted
DAG engine), ADR-007 (explicit state machines), ADR-008 (human gates), ADR-017 (deterministic
agents), ADR-018 (capability release), ADR-010 (rollback versus compensation). All ADRs are
**Proposed**, pending the candidate's acceptance. Scenario walkthrough:
[SCN-A](../scenarios/scn-a-greenfield.md).

## 1. Engine model

```
 WorkflowController ─► WorkflowService ─► PlanFactory ─► PlanValidator ─► persisted plan (stage_node rows)
                                                                   │
 GovernanceController ─► GateService ─┐                            ▼
                                      ├──────────────► RunCoordinator.advance(runId)
 StageDispatcher (8 daemon threads) ◄─┘   schedule under the per-run lock, in one transaction
        │  after commit: submit Dispatch(agent, context, generation, attemptNo, schedulingCycle)
        ▼
 StageAgent.execute(StageContext) ─► StageResult ─► RunCoordinator.onAttemptFinished ─► advance
        │
        └─ PermissionScopedPort ─► ApplicationPlanePort ─► InProcessApplicationPlaneAdapter ─► shortener
```

- **Persisted state.** The run, its plan (one `stage_node` per stage), attempts, artifacts,
  decisions, policy evaluations, and the hash-chained audit trail are all in the database. The engine
  keeps no workflow state in memory except a "stopping" flag per run.
- **Per-run serialization.** Every state change runs under `RunLocks` in a `TransactionTemplate`.
  Dispatch happens after commit, so agents never see uncommitted plan state.
- **Stale-result protection.** Each result carries `(generation, attemptNo)`. A result for an
  older generation or a superseded attempt is discarded and audited, never applied.
- **Plane boundary.** Agents reach the shortener only through a `PermissionScopedPort` limited to
  their declared permissions (`READ_LINKS`, `WRITE_SYNTHETIC_LINKS`, `PREVIEW_CAPABILITY`,
  `READ_CAPABILITIES`, `CHANGE_CAPABILITY_RELEASE`). Only `orchestration.integration` may call the shortener, and ArchUnit
  enforces this.
- **Agents are deterministic** (ADR-017). They reason from versioned knowledge
  (`capability-catalog.yaml`, `ambiguity-lexicon.yaml`, `policy-set.yaml` 1.0.0), so the same input
  gives the same artifacts and fingerprints.

## 2. Stage table

| Stage | Kind | Depends on | Produces | Notes |
|---|---|---|---|---|
| `REQUIREMENT_INGESTION` | agent | — | `REQUIREMENT` | |
| `REQUIREMENT_ANALYSIS` | agent | ingestion | `NORMALIZED_REQUIREMENT` | 5 quality checks, ambiguity detection |
| `CLARIFICATION` | gate (APPROVER) | analysis | — | skipped unless a blocking ambiguity exists |
| `DECOMPOSITION` | agent | clarification | `TASK_GRAPH` | parallel with threat assessment |
| `THREAT_ASSESSMENT` | agent | clarification | `THREAT_MODEL` | |
| `IMPACT_ANALYSIS` | agent | clarification | `IMPACT_ANALYSIS` | only for changes to existing behavior |
| `DESIGN` | agent | decomposition, threats (, impact) | `DESIGN` | decides `materialChange` |
| `ARCHITECTURE_APPROVAL` | gate (APPROVER) | design | — | skipped unless the design is material |
| `IMPLEMENTATION` | agent | architecture approval | `CHANGE_SET` | checks delivery status via the port |
| `DOCUMENTATION` | agent | architecture approval | `DOCUMENTATION` | parallel with implementation |
| `TESTING` | agent (probes) | implementation | `TEST_REPORT` | never degrades |
| `REGRESSION_TESTING` | agent (probes) | implementation | `REGRESSION_REPORT` | only for changes to existing behavior |
| `SECURITY_VERIFICATION` | agent (probes) | implementation | `SECURITY_REPORT` | never degrades |
| `VALIDATION` | agent (join) | testing, security, documentation (, regression) | `VALIDATION_REPORT` | synchronization point |
| `COMPLIANCE_EVALUATION` | agent | validation | `COMPLIANCE_REPORT`, `READINESS_REPORT` | READY / READY_WITH_ACCEPTED_LIMITATIONS / NOT_READY |
| `RELEASE_APPROVAL` | gate (RELEASE_OWNER) | compliance | — | always required |
| `RELEASE` | agent (probes) | release approval | `RELEASE_RECORD` | smoke probe; rollback of the flag on failure |
| `FINAL_SUMMARY` | agent | release | `FINAL_SUMMARY` | |
| `CHANGE_APPROVAL` | gate (APPROVER) | (inserted on re-plan) | — | change requests, Phase 5+ |

Stage entry and exit criteria (`EntryExitCriteria`) check that the required input artifacts exist
before a stage starts, and that the declared outputs exist before it succeeds.

## 3. State machines

**Stage** (`StageTransitions`):

| From | Allowed to |
|---|---|
| `PENDING` | `READY`, `SKIPPED`, `CANCELLED`, `REMOVED`, `FAILED`¹ |
| `READY` | `RUNNING`, `SUCCEEDED`, `AWAITING_DECISION`, `CANCELLED` |
| `RUNNING` | `SUCCEEDED`, `RETRY_WAIT`, `RUNNING`, `FAILED`, `AWAITING_DECISION`, `PENDING`, `CANCELLED` |
| `RETRY_WAIT` | `RUNNING`, `PENDING`, `CANCELLED` |
| `AWAITING_DECISION` | `SUCCEEDED`, `READY`, `FAILED`, `PENDING`, `CANCELLED` |
| `SUCCEEDED` | `PENDING` (re-plan), `COMPENSATED` |
| `SKIPPED` | `PENDING` (re-plan) |
| `FAILED`, `COMPENSATED`, `CANCELLED`, `REMOVED` | terminal |

¹ `PENDING → FAILED` records an unmet entry criterion at resolution time. It is implemented and
tested, but it is not yet in the `data-model.md` stage diagram. It is listed as a deviation for
review and has not been changed silently.

A gate reaches `SUCCEEDED` from `AWAITING_DECISION` only through `StageNode.succeedGate(decision)`,
which `GateService` calls after a valid, role-checked human decision.

**Run** (`RunTransitions`):

| From | Allowed to |
|---|---|
| `CREATED` | `RUNNING` |
| `RUNNING` | `AWAITING_HUMAN`, `PAUSED`, `COMPENSATING`, `COMPLETED`, `SAFE_STOPPED` |
| `AWAITING_HUMAN` | `RUNNING`, `PAUSED`, `COMPENSATING`, `SAFE_STOPPED`, `REJECTED` |
| `PAUSED` | `RUNNING`, `COMPENSATING`, `SAFE_STOPPED` |
| `COMPENSATING` | `SAFE_STOPPED`, `REJECTED` |
| `COMPLETED`, `REJECTED`, `SAFE_STOPPED` | terminal |

Every transition is written to the run's audit chain as `STAGE_TRANSITION` or `RUN_TRANSITION`,
with from, to, and reason. An illegal transition throws `IllegalTransitionException`.

## 4. Scheduling algorithm

`RunCoordinator.advance(runId)` runs one **scheduling cycle** under the run lock:

1. Stop if the run is terminal, paused, or compensating.
2. Load the plan in plan order and take the next cycle number (`max(schedulingCycle) + 1`).
3. Repeat until nothing changes:
   - for each `PENDING` stage whose dependencies are all satisfied (`SUCCEEDED` or `SKIPPED`):
     - if its condition is not met, move it to `SKIPPED` with the reason (for example, "Clarification
       not required: …");
     - if an entry criterion is unmet, move it to `FAILED`;
     - otherwise, move it to `READY`;
   - for each `READY` stage:
     - a gate opens as `AWAITING_DECISION` with a deadline and a review bundle;
     - an agent stage becomes `RUNNING`, and an attempt is recorded with this cycle number
       (`ATTEMPT_STARTED` audit event) and queued as a `Dispatch`.
4. Derive the run status from the plan: `AWAITING_HUMAN` when only gates are open, `COMPLETED`
   when every stage is done, and so on.
5. Commit, then submit the dispatches to `StageDispatcher`.

When an attempt finishes, its result is applied under the lock (artifacts stored, exit criteria
checked, stage transitioned), and `advance` runs again. Stages that become ready together are
dispatched in the **same cycle**. This is how fork and join are recorded, and SCN-A asserts it:
`IMPLEMENTATION ‖ DOCUMENTATION` share one cycle, `TESTING ‖ SECURITY_VERIFICATION` share one
cycle, and `VALIDATION` waits for all of them.

**Shared synthetic data.** Probe stages that run in parallel for the same run share its synthetic
links, which are cleaned up by run (ADR-010). Their probe-and-cleanup sections therefore run under
a per-run mutex (`SyntheticScope`). Dispatch and the rest of each stage remain concurrent.

**Reliability** (retry with backoff, timeouts, fallback, compensation, safe-stop, operator controls,
recovery after restart, fault injection) is described in [reliability.md](reliability.md).
**Re-planning** on change requests and clarifications arrives with Phases 7–8. The state machines
already include those states, so later phases add behavior without changing the model.
