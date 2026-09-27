# ADR-008: Role-Based, Fingerprint-Bound Human Approvals with Deadlines

## Status

Proposed (2026-09-26) — pending acceptance at Gate G4.

## Context

High-impact actions require explicit human approval that cannot be bypassed or inferred from
silence (constitution III; FR-GOV-01..09; FR-RDY-02/04). An approval must mean "I approved *this
content*": if the reviewed artifacts change afterwards, the approval must not silently carry over
(FR-GOV-05).

## Decision Drivers

- Non-bypassability (API, engine, and code-structure level)
- Separation of duties
- Binding of approvals to reviewed content
- Safe behavior on silence (deadlines)
- Concurrency safety (one decision wins)

## Options Considered

| Option | Advantages | Disadvantages / risks |
|--------|------------|-----------------------|
| **Role-checked decision endpoints + SoD + approvals storing artifact fingerprints + deadlines that safe-stop** | approvals are meaningful and verifiable; silence is safe | more moving parts |
| Boolean "approved" flag per stage | trivial | approval survives content changes; no SoD |
| Approval by any authenticated user | simple | violates least privilege and SoD |

## Decision

- Gate types and roles: `CLARIFICATION` → `APPROVER`; `ARCHITECTURE_APPROVAL` and
  `CHANGE_APPROVAL` → `APPROVER` (≠ run requester); policy exception decisions → `APPROVER`
  (≠ exception requester); `RELEASE_APPROVAL` → `RELEASE_OWNER` (≠ run requester).
- Each gate has a **review bundle**: the artifact types it presents (for example `DESIGN`,
  `THREAT_MODEL`, `IMPACT_ANALYSIS`). An approval stores `{artifactType: fingerprint}` for the
  bundle at decision time.
- Before any dependent stage starts, and at every replan, the engine re-checks the bound
  fingerprints. A mismatch marks the decision `valid = false` with a reason and re-opens the gate.
- Deadlines: `decision_deadline` per gate (default 24 h, configurable per stage and per run in
  demo mode). `DeadlineSweeper` safe-stops runs whose gate expired; late decisions get `409
  DEADLINE_PASSED`.
- Release approval re-computes readiness at decision time; `NOT_READY` → `409 RELEASE_NOT_READY`.
- Only HTTP endpoints create gate decisions; the engine's transition guard refuses to move a gate
  to `SUCCEEDED` without a valid decision; ArchUnit forbids agents from depending on
  `GateService`.

## Rationale

Fingerprint binding makes approvals verifiable evidence rather than flags, and makes replanning
safe: an approval is reused exactly when the approver would see the same content.

## Consequences

- **Positive**: every approval is auditable and tied to content; bypass requires changing tested
  code.
- **Negative**: demonstrations need several principals (five demo tokens).
- **Operational**: the pending actions list shows role, deadline, and review bundle.
- **Testing**: role matrix, SoD, deadline, concurrent-decision, invalidation tests.
- **Governance**: directly implements constitution III.

## Risks and Mitigations

| Risk | Mitigation |
|------|------------|
| Shared tokens undermine SoD | tokens map to distinct principals; production uses an identity provider (ADR-015) |
| Deadline too short in production | configurable; default 24 h |

## Reversibility

Medium.

## Traceability

- Requirements: FR-GOV-01..09, FR-RDY-02, FR-RDY-04, FR-RPL-03, NFR-SEC-02; constitution III
- Plan: §5; §3 stage specifications
- Tasks: governance task group

## Validation

`GateAuthorizationTest`, `SeparationOfDutiesTest`, `ApprovalBindingTest`, `GateDeadlineTest`,
`ConcurrentGateDecisionTest`, `AgentBoundaryArchitectureTest`.
