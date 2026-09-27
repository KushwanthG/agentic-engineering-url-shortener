# Policy set 1.0.0

This page describes the policies `COMPLIANCE_EVALUATION` evaluates for every run. It mirrors
[`policy-set.yaml`](../../src/main/resources/orchestration/policy-set.yaml); the YAML file is the
source of truth, and `PolicySetCoverageTest` checks that every id in it has exactly one rule. Decisions: ADR-019
(policy engine), ADR-008 (human approval). The ADRs are **Proposed**, pending the candidate's acceptance.

## Policies

| Id | Title | Domain | Severity | Applies when | Passes when |
|---|---|---|---|---|---|
| SEC-001 | URL validation probes pass | SECURITY | MANDATORY | always | every URL-validation probe of the security verification report passed |
| SEC-002 | No secrets in run artifacts | SECURITY | MANDATORY | always | no artifact contains a private key, bearer token, cloud access key, or password value |
| SEC-003 | High threats verified | SECURITY | MANDATORY | threats present | every HIGH threat of the threat model is verified by a passing probe |
| PRV-001 | No personal data columns | PRIVACY | MANDATORY | schema changes | no schema change adds a personal-data column (IP address, e-mail, user agent, name) |
| AUD-001 | Audit chain intact | COMPLIANCE | MANDATORY | always | the run's audit chain verifies |
| AUD-002 | Audit retention | COMPLIANCE | MANDATORY | always | configured audit retention is at least 365 days |
| LIC-001 | Approved dependency licenses | LICENSING | MANDATORY | always | every SBOM component license is on the allow-list (a missing SBOM fails) |
| TST-001 | Acceptance criteria verified | TESTING | MANDATORY | always | every acceptance criterion is verified by a passing probe |
| TST-002 | Regression suite passed | TESTING | MANDATORY | change to existing behavior | the regression suite passed |
| DOC-001 | Documentation updated | DOCUMENTATION | MANDATORY | API or behavior change | generated documentation exists and the repository documentation mentions the capability |
| CHG-001 | Architecture approval bound to the design | CHANGE_CONTROL | MANDATORY | material change | a valid architecture approval is bound to the current design fingerprint |
| CHG-002 | Impact analysis complete | CHANGE_CONTROL | MANDATORY | change to existing behavior | an impact analysis is present and not degraded |
| REL-001 | Release and rollback plan | RELEASE | MANDATORY | always | the design contains a release plan and a rollback plan |
| AUT-001 | Autonomy budget headroom | COMPLIANCE | ADVISORY | always | autonomy budget usage is below 80 percent |

A run pins the policy-set version it was created with. Every evaluation records that version, and
the engine refuses to evaluate against a different one. The policy set cannot change at runtime:
`PolicySetLoader` has no mutators (`ArchitectureTest.thePolicySetExposesNoMutators`).

## Outcomes

| Outcome | Meaning | Effect |
|---|---|---|
| `PASS` | the rule holds | none |
| `NOT_APPLICABLE` | the condition in "applies when" does not hold for this run | none |
| `FAIL` (advisory) | an advisory rule failed | readiness lists a limitation |
| `FAIL` (mandatory) | a mandatory rule failed and no approved, unexpired exception covers it | compliance blocks: `AWAITING_DECISION(POLICY_EXCEPTION)`, readiness `NOT_READY` |
| `EXCEPTION_REQUESTED` | a mandatory rule failed and an approved, unexpired exception covers it | readiness lists the exception as a limitation |

An evaluation caused by fault injection (`POLICY_FAILURE`) is flagged `simulated: true` and its
evidence says so.

## Exception workflow (FR-POL-04)

1. Compliance blocks on a mandatory failure. The run waits, and the pending action lists the
   compliance and readiness reports.
2. A `REQUESTER` asks for an exception: `POST /api/v1/workflows/{runId}/policy-exceptions`. The
   request must name the policy, a reason, a scope, a **compensating control**, and a future
   **expiry**. An exception can be requested only for a policy that currently blocks the run. A
   pending exception unblocks nothing.
3. An `APPROVER` **other than the exception's requester** decides it:
   `POST .../policy-exceptions/{id}/decision`. Both the request and the decision are recorded as
   decisions (`EXCEPTION_REQUEST`, `EXCEPTION_DECISION`) and audited.
4. **Approved.** Once every blocking policy is covered, compliance is evaluated again. The covered
   failure becomes `EXCEPTION_REQUESTED`, and readiness becomes `READY_WITH_ACCEPTED_LIMITATIONS`.
5. **Rejected.** The run safe-stops, with trigger `POLICY_EXCEPTION_REJECTED`.
6. **No decision by the deadline.** The run safe-stops. It is never approved by default.

## Release-blocking conditions (FR-RDY-01, FR-RDY-02)

| Readiness | When |
|---|---|
| `NOT_READY` | validation failed, or a mandatory policy failed without an approved exception |
| `READY_WITH_ACCEPTED_LIMITATIONS` | an advisory policy failed, an exception was used, or a stage ran degraded (fallback) |
| `READY` | none of the above |

The release owner approves `RELEASE_APPROVAL` knowing the listed limitations. At decision time the
readiness is checked again. The approval is refused with `409 RELEASE_NOT_READY` when the run is
`NOT_READY`, or when an exception readiness relied on has expired since compliance was evaluated.

Evidence: drill RDR-06 ([drills](../scenarios/drills.md)) and `PolicyExceptionFlowTest`.
