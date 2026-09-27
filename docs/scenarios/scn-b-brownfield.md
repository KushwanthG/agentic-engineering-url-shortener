# SCN-B — Brownfield: click-limited links

Scenario definition: [spec.md § SCN-B](../../specs/001-agentic-url-shortener/spec.md). Executable
proof: `ScenarioBBrownfieldE2ETest` (real agents, real application plane, HTTP only). Impact analysis
committed before the change: [scn-b-impact-analysis.md](scn-b-impact-analysis.md).

> **Simulated human input.** The architecture approval (`bob`, APPROVER) and release approval
> (`carol`, RELEASE_OWNER) are given by the automated test as labeled, non-production demo
> principals. They are not decisions of the candidate.
>
> The candidate's review of the impact analysis is still **PENDING**; see the
> [gate register](../governance/human-gate-register.md).

## 1. Input and interpretation

Fixed requirement `BF-001`, stored verbatim in
[`scenarios/scn-b-brownfield.json`](../../src/main/resources/scenarios/scn-b-brownfield.json):
"Click-limited short links", type `CHANGE_TO_EXISTING`, acceptance criteria AC-1..AC-6.

Requirement analysis classifies the requirement as `CHANGE_TO_EXISTING`, with catalog capability
`click-limit`. No blocking ambiguity is found, so clarification is skipped with its rationale.

The change conflicts with the existing fail-open analytics rule (FR-ANL-04), which the impact
analysis lists as an impacted requirement. AC-6 resolves the conflict: limited links fail closed.

## 2. Impact analysis and repository process

1. **Before any click-limit code**, the implemented `IMPACT_ANALYSIS` ran for BF-001 against this
   repository (`BrownfieldImpactGateIT`). The analysis was committed as `53d8fc4`, ahead of the
   commit that adds `V4__click_limit.sql`.
2. The source scan expanded 5 catalog seeds into 15 impacted components. 10 of them are derived
   through the reverse-dependency closure, for example
   `RedirectController → RedirectService` and `IdempotencyService → LinkView → ShortLink`.
3. It also listed 16 existing tests that reference impacted classes, the documentation to update,
   4 regression risks (RR-CL-1..4), the rollout, the rollback, and the impacted requirements
   (FR-ANL-04, FR-RED-04, FR-LNK-11).
4. The `SCN-B` end-to-end test was written next and run before the code existed. It failed as
   expected: the run safe-stopped at `IMPLEMENTATION`, with *"capability click-limit is not
   delivered in the running system: [MIGRATION_APPLIED: Flyway history does not contain a
   successful migration V4]"*. The orchestrator refuses to release a capability that does not exist
   (FR-ORC-15).
5. The click-limit tests (`ClickLimitTest`, `ClickLimitConcurrencyTest`) were written and seen
   failing, and then the change was implemented:
   - `V4__click_limit.sql` (nullable column plus a range `CHECK`);
   - `ShortLink.maxClicks`;
   - the conditional increment `… WHERE click_count < max_clicks`;
   - fail-closed redirects for limited links;
   - `ClickLimitCapability`;
   - probes CL-P1..CL-P6 and CL-SEC-BOUNDS.

## 3. Orchestration path

```
REQUIREMENT_INGESTION → REQUIREMENT_ANALYSIS → CLARIFICATION (skipped)
   → IMPACT_ANALYSIS ‖ DECOMPOSITION ‖ THREAT_ASSESSMENT → DESIGN → ARCHITECTURE_APPROVAL (human)
   → IMPLEMENTATION ‖ DOCUMENTATION
        IMPLEMENTATION → TESTING ‖ REGRESSION_TESTING ‖ SECURITY_VERIFICATION
   → VALIDATION → COMPLIANCE_EVALUATION → RELEASE_APPROVAL (human) → RELEASE → FINAL_SUMMARY
```

Recorded scheduling cycles (latest run, E-B4): `IMPACT_ANALYSIS`, `DECOMPOSITION`, and
`THREAT_ASSESSMENT` all started in cycle 3. `TESTING`, `REGRESSION_TESTING`, and
`SECURITY_VERIFICATION` all started in cycle 6. `VALIDATION` started in cycle 7.

## 4. Approvals

| Gate | Reviewed | Decided by (simulated) |
|---|---|---|
| `ARCHITECTURE_APPROVAL` | the design with migration `V4__click_limit.sql`, the rollback plan ("stored limits stay enforced"), the threat model, and the impact analysis | `bob` |
| `RELEASE_APPROVAL` | readiness `READY`, validation, and compliance (all 14 policies `PASS`, including TST-002 regression and CHG-002 impact analysis) | `carol` |

## 5. Validation

- **Acceptance** (E-B6): CL-P1..CL-P6 verify AC-1..AC-6 against the live service.
  - CL-P3 resolves a 3-click link with 12 concurrent clients: exactly 3 redirects.
  - CL-P6 uses a click-store outage simulated for this run's synthetic links on the probing thread
    only: the redirect is refused, then served once the store is back.
- **Regression** (E-B2): R-P1..R-P6 (creation, redirect, expiry, unlimited links, statistics,
  idempotent replay) all pass with the capability enabled.
- **Security:** the URL catalog, TH-CL-1 (concurrency, CL-P3), and TH-CL-2 (limit bounds,
  CL-SEC-BOUNDS).
- **After release, over HTTP:**
  - A link created with `maxClicks: 2` answers 302, 302, 410.
  - A link created **before** the run answers 302 on every resolution (AC-4).
- **Terminal outcome:** `COMPLETED`, readiness `READY`, audit chain verified (E-B8).

## 6. Evidence index

Regenerate with:

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21.0.12.1"
.\mvnw.cmd -B -ntp test "-Dtest=BrownfieldImpactGateIT,ScenarioBBrownfieldE2ETest"
```

Output goes to `target/evidence/scn-b/`, with provenance in every file. The scenario run is flagged
`simulatedInput: true`.

| Id | Evidence | File |
|---|---|---|
| E-B1 | impact analysis: components (seed or derived, with chain), interfaces, data flows, tests, documentation, regression risks, rollout, rollback | `E-B1-impact-analysis.json` (run); `E-B1-impact-analysis-gate.json` (gate, before the code) |
| E-B2 | regression results R-P1..R-P6 | `E-B2-regression-results.json` |
| E-B3 | data compatibility and rollback: additive V4, rollback plan, old link unaffected, new link limited | `E-B3-data-compatibility-and-rollback.json` |
| E-B4 | plan graph with the brownfield stages, timeline, scheduling cycles | `E-B4-plan-and-timeline.json` |
| E-B5 | decisions | `E-B5-decisions.json` |
| E-B6 | per-criterion acceptance results | `E-B6-acceptance-results.json` |
| E-B7 | policy outcomes (policy set 1.0.0) | `E-B7-policy-outcomes.json` |
| E-B8 | audit trail with integrity verification | `E-B8-audit-trail.json` |
| E-B9 | final engineering summary | `E-B9-final-summary.md` |
