# SCN-B impact analysis: click-limited links (BF-001)

**Brownfield gate (guide §19, task T086).** This analysis was produced by the implemented
`IMPACT_ANALYSIS` agent. The agent scanned this repository, and this document was committed before
any click-limit code existed. `git log` shows this commit ahead of the one that adds
`V4__click_limit.sql`.

- **Source:** `BrownfieldImpactGateIT` runs the analysis. Its raw output is SCN-B evidence E-B1
  (`target/evidence/scn-b/E-B1-impact-analysis-gate.json`).
- **Regenerate:** `.\mvnw.cmd -B -ntp test "-Dtest=BrownfieldImpactGateIT"`.

> **Human approval required: PENDING.** The candidate's review of this impact analysis is a
> reviewer-owned item in the [gate register](../governance/human-gate-register.md). Implementation
> proceeds provisionally under the delegated-progression clause. This document is not an approval.

## 1. Current versus requested behavior

| Aspect | Current (before this change) | Requested (BF-001) |
|---|---|---|
| Link creation | `maxClicks` exists in contract 1.2.0 but is refused with `CAPABILITY_NOT_AVAILABLE`; no column stores it | optional `maxClicks` 1..1,000,000 stored with the link, reported back (AC-1); out of range → `INVALID_CLICK_LIMIT` (AC-5) |
| Redirect | `RedirectService.resolve`: not found / expired / redirect. `ClickRecorder` increments `click_count` unconditionally in its own transaction | a limited link redirects only while `click_count < max_clicks`; the next resolution is the expired outcome (AC-2), including under concurrency (AC-3) |
| Analytics failure | **fail-open** (FR-ANL-04): a click that cannot be recorded is logged and counted, and the redirect is still served | unchanged for unlimited links; **fail-closed** for limited links: 503 when the click cannot be recorded (AC-6) |
| Existing links | unlimited | still unlimited (AC-4): no default, nullable column |

## 2. Impacted modules

**Catalog seeds** (named by the capability catalog):

| Component | Reason |
|---|---|
| `ShortLink` | new `maxClicks` value |
| `ShortLinkRepository` | increment only while below the limit (conditional atomic update) |
| `ClickRecorder` | conditional increment; report whether the click was accepted |
| `RedirectService` | exhausted links resolve as expired; fail closed for limited links |
| `LinkCreationService` | validate and store `maxClicks` |

**Derived by the scan** (reverse-dependency closure, with dependency chains):

| Component | Chain | Assessment |
|---|---|---|
| `RedirectController` | RedirectController → RedirectService | maps the new outcomes: expired → 410, unavailable → 503 |
| `LinkController` | LinkController → LinkCreationService | passes `maxClicks` through (already present) |
| `LinkView`, `LinkResponse`, `CreatedLink` | … → LinkView → ShortLink | report `maxClicks` (fields already in the contract) |
| `LinkQueryService`, `LinkWriter` | … → ShortLink | read and write paths; no rule change expected, but covered by regression tests |
| `IdempotencyService` | IdempotencyService → LinkView → ShortLink | the replay fingerprint already includes `maxClicks` (a replay with a different limit is a different request); verified, no change needed |
| `LinkStoreHealthIndicator` | LinkStoreHealthIndicator → ShortLinkRepository | none (health query only) |
| `InProcessApplicationPlaneAdapter` | InProcessApplicationPlaneAdapter → LinkCreationService | probes create limited synthetic links through it |

The scan found 10 components that the catalog does not list.

## 3. Interfaces, contracts, domain rules, persistence

- **API** (`openapi.yaml`, contract 1.2.0, already declared):
  - `POST /api/v1/links` takes an optional `maxClicks`, returns it, and can fail with
    `INVALID_CLICK_LIMIT` (400). Compatibility: BACKWARD_COMPATIBLE.
  - `GET /{code}` returns 410 once a limited link is exhausted, and 503 when the click of a limited
    link cannot be recorded. Compatibility: BEHAVIOR_ONLY.
- **Domain rules:** the limit is inclusive. Exhausted means `click_count >= max_clicks`. An
  exhausted link resolves as expired.
- **Persistence:** `V4__click_limit.sql` adds `short_link.max_clicks BIGINT NULL` with
  `CHECK (max_clicks BETWEEN 1 AND 1000000)`. The change is additive and nullable, so existing rows
  are unaffected.
- **Orchestration states:** no new stage or state.
  - The plan for `CHANGE_TO_EXISTING` adds `IMPACT_ANALYSIS` (in parallel with decomposition and
    threat assessment) and `REGRESSION_TESTING` (in parallel with testing and security).
  - The release flag is `click-limit`.
- **Telemetry:** `shortener.redirects{outcome}` gains expired outcomes for exhausted links.
  `shortener.analytics.failures` still counts failures on unlimited links. Failures on limited
  links become 503 responses.

## 4. Tests

- **Existing tests that reference impacted classes (16, from the scan):**
  - `RedirectServiceTest`, `RedirectControllerTest`, `RedirectConcurrencyTest`
  - `LinkCreationServiceTest`, `LinkCreationAliasTest`, `IdempotencyServiceTest`
  - `ShortLinkRepositoryTest`, `StoreUnavailableTest`, `LinkApiContractTest`, `UrlSecurityCatalogIT`
  - `ApplicationPlaneAdapterTest`, `CompensationCoordinatorTest`
  - `TestingAgentTest`, `SecurityVerificationAgentTest`, `ReleaseAgentTest`, `RegressionTestingAgentTest`

  All of them must stay green.
- **New direct tests (written first):**
  - `ClickLimitTest`: two redirects then 410; range; unreleased; fail closed; limits stay enforced
    after withdrawal.
  - `ClickLimitConcurrencyTest`: exactly N of more than N concurrent resolutions redirect.
- **Regression probes in the run:** R-P1..R-P6 run with the capability enabled. R-P4 checks that
  links without a limit stay unlimited.

## 5. Risks

**Security:** limit abuse through huge or negative values. Range checks exist in both the API and
the database `CHECK`.

**Reliability:**
- Lost updates under concurrency: the fix is one conditional `UPDATE … WHERE click_count < max_clicks`.
- Fail-open versus fail-closed divergence: explicit by design (AC-6), and fail-closed applies only
  when `max_clicks` is set.

**Data compatibility:** additive nullable column with no backfill. A rollback keeps the column.

**Regression risk matrix** (from the analysis):

| Id | Risk | Severity | Mitigation / verification |
|---|---|---|---|
| RR-CL-1 | redirect latency grows with the conditional increment | MEDIUM | single statement; unlimited links keep the existing path (R-P2) |
| RR-CL-2 | lost updates exceed the limit | HIGH | atomic conditional update; `ClickLimitConcurrencyTest`, probe CL-P3 |
| RR-CL-3 | limited links fail closed while unlimited links fail open | MEDIUM | fail closed only when `max_clicks` is set; R-P4 |
| RR-CL-4 | existing links or clients that never send `maxClicks` change behavior | HIGH | nullable column with no default; R-P1..R-P6 with the capability enabled |

## 6. Rollback and compensation

- **Rollback:** withdraw the `click-limit` flag. New limits are then rejected, and stored limits
  stay enforced (clarification Q4). V4 is additive and stays.
- **Compensation:** synthetic probe links of the run are deleted by run (ADR-010).
- **Consumer data:** limited links created by consumers are never modified.

## 7. Affected ADRs and downstream tasks

- **ADR-016 (analytics consistency):** the fail-open rule gains the fail-closed exception for
  limited links. This is already anticipated by AC-6 and FR-ANL-04; no ADR text change is proposed.
- **ADR-010:** the rollback semantics above.
- **ADR-018:** a new capability flag, released only by `RELEASE`.
- **Downstream tasks that need no re-planning:**
  - T087: the tests in section 4.
  - T088: implementation, as planned.
  - T089, T090: the scenario test and its documentation.

## 8. Dependency map and test-first change plan

```
V4 migration → ShortLink.maxClicks → ShortLinkRepository.incrementIfBelowLimit → ClickRecorder
     ↘ LinkCreationService (validate, store) → LinkController (pass-through, response)
RedirectService (exhausted = expired; fail closed if limited) → RedirectController (410 / 503)
```

1. Write `ScenarioBBrownfieldE2ETest` first. Its expected red: the run safe-stops at
   `IMPLEMENTATION` with "capability not delivered" (FR-ORC-15).
2. Write `ClickLimitTest` and `ClickLimitConcurrencyTest`, and record the red run.
3. Implement V4, the entity, the conditional update, the fail-closed branch, the capability wiring,
   and probes CL-P1..CL-P6. The tests turn green.
4. Run the full suite, including SCN-A as the regression guard.

## 9. Re-planning decision

**No re-planning of the feature plan is needed.** The impact stays within the components the plan
and catalog anticipated. The derived components are consumers of the seeds. The one derived
component that looked at risk, the idempotency fingerprint, already includes `maxClicks`.
