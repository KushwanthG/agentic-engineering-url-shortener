# Governance: human gates and anti-bypass guarantees

How the control plane keeps humans in charge of high-impact decisions. Decisions: ADR-008 (human
approval model), ADR-015 (authentication and roles), ADR-019 (policy engine). Engine model:
[orchestration.md](orchestration.md). All ADRs are **Proposed**, pending the candidate's acceptance.

## 1. Gates

| Gate | Opens when | Decided by | Reviews (bound fingerprints) |
|---|---|---|---|
| `CLARIFICATION` | requirement analysis finds a blocking ambiguity | `APPROVER` | `CLARIFICATION_REQUEST`, `NORMALIZED_REQUIREMENT` |
| `ARCHITECTURE_APPROVAL` | the design is material (API, schema, or security-control change) | `APPROVER`, not the requester | `DESIGN`, `THREAT_MODEL`, `IMPACT_ANALYSIS` |
| `CHANGE_APPROVAL` | a change request arrives after approval (re-plan) | `APPROVER`, not the requester | the change request and the plan diff |
| policy exception (`AWAITING_DECISION(POLICY_EXCEPTION)`) | a mandatory policy fails | `APPROVER`, not the exception requester | `COMPLIANCE_REPORT`, `READINESS_REPORT` |
| `RELEASE_APPROVAL` | always, before `RELEASE` | `RELEASE_OWNER`, not the requester | `READINESS_REPORT`, `VALIDATION_REPORT`, `COMPLIANCE_REPORT` |

An open gate is a stage in `AWAITING_DECISION`. It carries a deadline and appears as a pending action
(`GET /api/v1/workflows/{runId}`) with the artifacts to review.

## 2. Roles and separation of duties

Principals authenticate with bearer tokens that are stored only as SHA-256 hashes (ADR-015). Roles:
`API_CONSUMER`, `REQUESTER`, `APPROVER`, `RELEASE_OWNER`, and `AUDITOR`.

- Only a principal with the gate's role may decide it (FR-GOV-03). Authorization is checked twice:
  by the URL rules in `SecurityConfig`, and again by `GateService` against the gate's
  `requiredRole`.
- The requester of a run may not decide its architecture, change, or release gate, even when they
  hold the role (FR-GOV-04). The refusal is `403 SEPARATION_OF_DUTIES`.
- Every refusal is audited as `DECISION_REFUSED` with the actor and the reason code.

`GovernanceSecurityMatrixTest` checks every control-plane endpoint: 401 without a token, and 403 for
each role that should be refused.

## 3. Decisions are bound to what was reviewed

An approval records:
- the actor, the role, the rationale, and the time;
- the fingerprints (SHA-256) of the artifacts in the gate's review bundle (FR-GOV-05, FR-GOV-09).

Before every scheduling cycle, `RunCoordinator` re-checks each approved gate against the current
artifacts. If a bound artifact has changed:

1. The decision is marked invalid, with the reason (old and new fingerprint), and the invalidation
   is audited as `DECISION_INVALIDATED`.
2. The gate and every stage downstream of it are re-opened as a new generation. Results of earlier
   attempts become stale and are discarded. Approvals of downstream gates are invalidated too.
3. The gate asks for a fresh decision, which is then bound to the new content.

## 4. Deadlines and escalation

Every gate has a deadline: `app.orchestration.gate-deadline`, PT24H by default. A demonstration
run may shorten it through fault-injection options.

`DeadlineSweeper` runs every `deadline-sweep-interval` (5 s) and escalates each expired gate:
- the escalation is audited as `GATE_ESCALATED`, with the deadline and the role it was waiting for;
- the run safe-stops (FR-GOV-07). A gate is **never approved by default**.

A decision that arrives after the deadline is refused with `409 DEADLINE_PASSED`.

## 5. Conflicts and rejection

- **Concurrent decisions.** Decisions on a run are serialized by the run lock. The gate row also
  has an optimistic version. When two approvers race, exactly one decision is recorded, and the
  other caller receives `409 CONCURRENT_DECISION`.
- **Rejection** (FR-GOV-06). The run moves to `COMPENSATING`, and open stages are cancelled.
  `CompensationCoordinator` then undoes the run's side effects: synthetic probe data is removed and
  audited as `COMPENSATION_ACTION`. Finally, the run ends `REJECTED`. Rejection happens before
  `RELEASE`, so no capability changes. Consumer data is never touched.

## 6. Anti-bypass guarantees

| Guarantee | Enforced by |
|---|---|
| A gate reaches `SUCCEEDED` only with a valid human approval of that gate in that run | `StageNode.succeedGate` guard; `succeed()` refuses gates |
| Agents cannot decide gates, request or decide exceptions, see principals, or reach the network | `ArchitectureTest.agentsCannotReachGovernanceSecurityOrTheNetwork` |
| Agents cannot drive the engine or its repositories | `ArchitectureTest.agentsDoNotDriveTheEngineOrItsRepositories` |
| Policies cannot be changed at runtime | `ArchitectureTest.thePolicySetExposesNoMutators`; the policy set is a versioned file pinned per run |
| Agents act on the application plane only within declared permissions | `PermissionScopedPort`; denials audited (`AgentPermissionTest`) |
| Waiting gates block only their dependents | scheduler dependency check (`WaitingGateIndependenceTest`) |

## 7. Evidence

SCN-A exports the gate decisions, with their bound fingerprints, as `E-A5-decisions.json` (see
[SCN-A](../scenarios/scn-a-greenfield.md)). The decisions in that export are **simulated human
input** given by the automated test as labeled demo principals. They are not decisions of the
candidate.
