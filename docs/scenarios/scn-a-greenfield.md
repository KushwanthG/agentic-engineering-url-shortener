# SCN-A — Greenfield: custom aliases for short links

Scenario definition: [spec.md § SCN-A](../../specs/001-agentic-url-shortener/spec.md). Executable
proof: `ScenarioAGreenfieldE2ETest` (real agents, real application plane, HTTP only against a
random-port server). Engine model: [orchestration.md](../architecture/orchestration.md).

> **Simulated human input.** The architecture approval (`bob`, APPROVER) and release approval
> (`carol`, RELEASE_OWNER) in this scenario are given by the automated test as labeled,
> non-production demo principals. They show that the gates work. They are **not** decisions of the
> candidate, and they do not satisfy any human gate G1–G7 in the
> [human-gate register](../governance/human-gate-register.md).

## 1. Input

Fixed requirement `GF-001`, stored verbatim in
[`scenarios/scn-a-greenfield.json`](../../src/main/resources/scenarios/scn-a-greenfield.json):
"Custom aliases for short links", type `NEW_CAPABILITY`, acceptance criteria AC-1..AC-6 (alias
created, `ALIAS_CONFLICT`, invalid characters and length → `INVALID_ALIAS`, reserved → `RESERVED_ALIAS`,
alias redirects), constraint "aliases are case-sensitive and share the code namespace".

## 2. Interpretation

`REQUIREMENT_ANALYSIS` (deterministic, driven by `capability-catalog.yaml` and
`ambiguity-lexicon.yaml`, ADR-017) produces `NORMALIZED_REQUIREMENT`:

- classification `NEW_CAPABILITY`, matched catalog capability `custom-alias`;
- all five quality checks — completeness, consistency, testability, policy, architecture boundary
  — `PASS`, each with its evidence;
- no blocking ambiguity, so `clarificationRequired = false` with a recorded rationale, and the
  `CLARIFICATION` stage is `SKIPPED` with that rationale as its skip reason (FR-ORC-13, FR-ORC-06).

Evidence: E-A1.

## 3. Decomposition, threats, and design

`DECOMPOSITION` and `THREAT_ASSESSMENT` run in parallel after clarification is skipped.

- `TASK_GRAPH`: 7 tasks mapped to acceptance criteria, with dependencies and a critical path (E-A2).
- `THREAT_MODEL`: the catalog's threats, each bound to the probe that verifies it:
  - TH-CA-1: an alias imitates a system route. HIGH; verified by CA-SEC-RESERVED and CA-SEC-ROUTE.
  - TH-CA-2: an alias takes over an existing code. HIGH; verified by CA-P2.
  - TH-URL-1: an aliased link points at a malicious target. HIGH; verified by SEC-URL-CATALOG.
  - TH-CA-3: aliases are easier to guess. LOW; accepted, and mitigated by not-found throttling.
- `DESIGN`: API change (contract 1.1.0: optional `alias` on `POST /api/v1/links`, alias on
  `GET /api/v1/links/{code}`), schema change (`V3__custom_alias.sql`), release plan (capability
  flag `custom-alias`), rollback plan (withdraw the flag; existing alias links keep resolving). The
  API and schema changes make the design **material** (E-A3).

## 4. Orchestration path

```
REQUIREMENT_INGESTION → REQUIREMENT_ANALYSIS → CLARIFICATION (skipped)
   → DECOMPOSITION ‖ THREAT_ASSESSMENT → DESIGN → ARCHITECTURE_APPROVAL (human)
   → IMPLEMENTATION ‖ DOCUMENTATION
        IMPLEMENTATION → TESTING ‖ SECURITY_VERIFICATION
   → VALIDATION (joins TESTING, SECURITY_VERIFICATION, DOCUMENTATION)
   → COMPLIANCE_EVALUATION → RELEASE_APPROVAL (human) → RELEASE → FINAL_SUMMARY
```

The test proves fork and join from the recorded `schedulingCycle` of each `ATTEMPT_STARTED` audit
event and from the timeline (review RC-2):

| Assertion | Recorded result (latest run) |
|---|---|
| `IMPLEMENTATION` and `DOCUMENTATION` dispatched in one scheduling cycle | both cycle 5 |
| `TESTING` and `SECURITY_VERIFICATION` dispatched in one scheduling cycle | both cycle 6 |
| `VALIDATION` starts only after all four have finished | cycle 7; start ≥ last predecessor finish |

Cycle numbers come from the exported E-A4. Re-running the test regenerates them.

**Shared synthetic data.** `TESTING` and `SECURITY_VERIFICATION` both run probes on synthetic
links owned by the run. Per ADR-010, those links are cleaned up by run. The first real-agent run of
this test showed that the two parallel probe sections collided on the same run-scoped alias. It
also showed that one stage's cleanup could delete the other stage's in-flight data. The
probe-and-cleanup section is now exclusive per run (`SyntheticScope`). The two stages still
dispatch in the same cycle and run concurrently; only that section is serialized.

## 5. Approvals

| Gate | Opened because | Decided by (simulated) | Bound to |
|---|---|---|---|
| `ARCHITECTURE_APPROVAL` | material design (API and schema change) | `bob`, APPROVER | fingerprints of `DESIGN`, `THREAT_MODEL` (and `IMPACT_ANALYSIS` when present) |
| `RELEASE_APPROVAL` | always, before release | `carol`, RELEASE_OWNER | fingerprints of `READINESS_REPORT`, `VALIDATION_REPORT`, `COMPLIANCE_REPORT` |

A gate succeeds only through `GateService`, and only for a principal with the gate's role
(ADR-008). Evidence: E-A5.

## 6. Validation and release

- `TESTING`: acceptance probes CA-P1..P6 run against the live service. The unreleased capability is
  enabled for these calls only (preview, ADR-018). Every criterion AC-1..AC-6 is verified by a
  passing probe, and synthetic links are removed afterwards (E-A6).
- `SECURITY_VERIFICATION`: the URL-security catalog and alias threat probes. Every HIGH threat must
  be verified.
- `COMPLIANCE_EVALUATION`: policy set `1.0.0` (14 rules). There are no mandatory failures, and
  readiness is `READY` (E-A7).
- `RELEASE`: the capability flag is set through the port, followed by a post-release smoke probe.
  If the smoke probe fails, the flag is rolled back.
- After completion, the test calls the public API as a consumer:
  - an alias link is created (201, code = alias);
  - the alias redirects (302 to the target);
  - reusing the alias returns `ALIAS_CONFLICT`.

Terminal outcome: `COMPLETED`, readiness `READY`, policy-set version `1.0.0`. The audit chain of
the run verifies as intact (E-A8), and a final engineering summary exists (E-A9).

## 7. Failure path

A transient `TESTING` failure that recovers through bounded retry is drill **RDR-01**. It runs the
same GF-001 input with `TRANSIENT_ERROR` ×2 on `TESTING`, a labeled simulated fault. The recorded
result: attempts `FAILED_TRANSIENT`, `FAILED_TRANSIENT`, `SUCCEEDED`, and the run `COMPLETED`. The
other failure paths on the same input (fallback, rollback, gate deadline, policy exception, operator
stop) are drills RDR-02..RDR-07: see [drills.md](drills.md). A material ambiguity that emerges
later suspends only the affected path (FR-RPL-06, Phase 8).

## 8. Evidence index

Regenerate the evidence with:

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21.0.12.1"
.\mvnw.cmd -B -ntp test "-Dtest=ScenarioAGreenfieldE2ETest"
```

Output goes to `target/evidence/scn-a/`. Every file starts with provenance: git commit, command,
timestamp, JDK, profile, and `simulatedInput: true`.

| Id | Evidence | File |
|---|---|---|
| E-A1 | requirement-quality record, no-clarification rationale, skipped clarification stage | `E-A1-requirement-quality.json` |
| E-A2 | decomposition with task dependencies and critical path | `E-A2-decomposition.json` |
| E-A3 | API and schema impact, material-change reasons | `E-A3-api-and-schema-impact.json` |
| E-A4 | plan graph (stages, dependencies, states), timeline, scheduling cycles | `E-A4-plan-and-timeline.json` |
| E-A5 | architecture and release decisions with bound fingerprints | `E-A5-decisions.json` |
| E-A6 | per-acceptance-criterion probe results | `E-A6-acceptance-results.json` |
| E-A7 | policy outcomes with policy-set version | `E-A7-policy-outcomes.json` |
| E-A8 | run audit trail with integrity verification | `E-A8-audit-trail.json` |
| E-A9 | final engineering summary | `E-A9-final-summary.md` |
