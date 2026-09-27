# ADR-019: Versioned YAML Policy Set Evaluated by Java Policy Rules

## Status

Proposed (2026-09-26) — pending acceptance at Gate G4.

## Context

Policy guardrails for security, compliance, privacy, audit retention, licensing, testing,
documentation, and change control must be versioned. Each run records the version evaluated,
every evaluation produces one of four outcomes with evidence, mandatory failures block, and
exceptions are human-approved, time-bound, and carry compensating controls (constitution VI;
FR-POL-01..06; FR-RDY-01/02).

## Decision Drivers

- Versioning and evidence per evaluation
- Blocking semantics integrated with the orchestration state model
- Simplicity for about 14 rules
- Real inputs (artifacts, audit chain, SBOM, configuration)

## Options Considered

| Option | Advantages | Disadvantages / risks |
|--------|------------|-----------------------|
| **YAML set (id, domain, severity, applicability, version) + `PolicyRule` beans** | versioned data plus typed, testable logic | rule logic still ships with the application |
| Open Policy Agent (Rego) | industry-standard policy-as-code | extra runtime and language; heavy for this scope |
| Rules hard-coded inside agents | little code | no versioning; no independent evidence |

## Decision

- `policy-set.yaml` (version `1.0.0`) lists the policies in plan §9. `PolicyEngine` loads it at
  startup; each run pins `policy_set_version` at creation, and a later set version never changes
  running runs.
- Each `PolicyRule` returns `PASS`, `FAIL`, or `NOT_APPLICABLE` with evidence; the engine converts
  a `FAIL` covered by an approved, unexpired exception into `EXCEPTION_REQUESTED`.
- Mandatory `FAIL` → `COMPLIANCE_EVALUATION` becomes `AWAITING_DECISION(POLICY_EXCEPTION)`.
  Advisory `FAIL` → recorded, readiness `READY_WITH_ACCEPTED_LIMITATIONS`.
- Exceptions: requested by `REQUESTER` (reason, scope, compensating control, expiry); decided by an
  `APPROVER` who is not the requester; expiry is checked when readiness is computed and at the
  release decision.
- Evidence: every evaluation is persisted (`policy_evaluation`) and audited (`POLICY_EVALUATED`)
  and appears in the compliance report and final summary. Simulated failures (fault injection) are
  flagged `simulated`.
- `LIC-001` reads the CycloneDX SBOM generated into `META-INF/sbom/` by the build and checks
  licenses against an allow-list.

## Rationale

Data-defined versioning with typed rules gives governance evidence without adding a policy
runtime; OPA remains a documented upgrade path.

## Consequences

- **Positive**: policy outcomes are first-class, queryable evidence; blocking is real.
- **Negative**: changing rule logic requires a release of the application (versioned together).
- **Testing**: rule unit tests (pass, fail, not applicable); blocking and exception flow tests;
  expiry tests; SBOM parsing test.

## Risks and Mitigations

| Risk | Mitigation |
|------|------------|
| SBOM absent when running from an IDE | `LIC-001` fails with clear evidence; quickstart uses Maven, which generates it |
| Rule gaps | policy coverage reviewed in checklists; advisory rules for soft signals |

## Reversibility

High.

## Traceability

- Requirements: FR-POL-01..06, FR-RDY-01/02, NFR-SEC-05, NFR-AUD-01, NFR-CHG-01; constitution VI
- Plan: §9; research R-16
- Tasks: policy task group; RDR-06

## Validation

`PolicyEngineTest`, per-rule tests, `PolicyExceptionFlowTest`, `ReadinessEvaluatorTest`, RDR-06
end-to-end test.
