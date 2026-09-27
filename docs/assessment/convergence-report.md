# Convergence report (T121)

This report records the results of `/speckit-converge` and the full quality suite on 2026-09-27, at
commit `2a2a0bf`.

## 1. `/speckit-converge` result: `tasks_appended`

The skill found 7 findings. Four were appended as **Phase 11: Convergence** (T133–T136) in
`tasks.md`. The other three were already deferred or already tracked, so they were not added again.

| ID | Gap | Severity | Source | Disposition |
|---|---|---|---|---|
| F1 | contradicts | **CRITICAL** | Constitution V (no credentials in logs) | Spring Boot's generated default-user password was logged at every startup (seen in the T120 demo log). **Fixed**: T133, commit `2a2a0bf`, `StartupLogSecurityTest` (red, then green) |
| F2 | missing | HIGH | FR-ORC-18 | Deferred: T074 (SD-1) |
| F3 | missing | HIGH | FR-RPL-06 | Deferred: T096 (SD-1) |
| F4 | partial | HIGH | Constitution V (dependency-risk check), NFR-SEC-05 | T128 (existing task), completed afterwards: 3 CRITICAL Tomcat 11.0.24 advisories fixed by upgrading to 11.0.26; re-scan clean |
| F5 | partial | MEDIUM | NFR-PRF-01 | T134, **open**: see [performance.md](performance.md) |
| F6 | missing | MEDIUM | ADR-015 mitigation (demo startup warning) | **Fixed**: T135, `DemoProfileWarning` |
| F7 | partial | LOW | SC-001 (quickstart accuracy) | **Fixed**: T136, `quickstart.md` corrected |

**What was checked:**
- the requirements, through `TraceabilityMatrixTest`, which covers every FR and scenario;
- the NFRs and SCs;
- the plan decisions in §7 and §8;
- the risk mitigations of the ADRs;
- constitution V in detail.

**Measurements taken during the assessment:**
- line coverage;
- a run of the suite in random order;
- the configured query timeout;
- that the `*IT` classes execute.

## 2. Full quality suite

| Run | Command | Result |
|---|---|---|
| Final clean build | `mvnw -B -ntp clean verify` at `2a2a0bf` | **530 tests, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS**, 163 s |
| Random test order | `mvnw -B -ntp test -Dsurefire.runOrder=random` at `90c2bfe` plus the convergence work | 528/528 green (before the 2 T133 tests existed). No order dependence observed |
| Clean clone | [quickstart-validation.md](quickstart-validation.md) | 528/528 green, 202 s; quickstart §1–§6 pass |

**Flaky or order-dependent checks.** None failed during Phase 9 and 10. Two earlier
non-deterministic issues are recorded in `tdd-evidence.md` and were fixed at the time: the SCN-A
synthetic alias collision (Phase 4) and audit-chain head creation under concurrent appends. The
performance measurement varies a lot between runs, so only a generous bound is asserted.

## 3. Requirement verification

- **Tested ids.** `target/traceability/requirements-to-tests.md` lists 126 of 136 ids with at least
  one test class.
- **Untested FRs.** Only FR-ORC-18 and FR-RPL-06 are untested; both are deferred.
- **Ids without a tag.** Nine NFR and SC ids carry no tag and are verified by measurement or review:
  | Id | How it is covered |
  |---|---|
  | NFR-PRF-01, NFR-PRF-02 | performance measurement and scenario timing |
  | NFR-TST-01 | measured 95.0% |
  | NFR-TST-02 | random-order run |
  | NFR-OBS-01 | T107 deferred |
  | NFR-MNT-02 | T114 deferred |
  | NFR-AUT-02 | budget, T074 |
  | SC-001 | quickstart validation |
  | SC-009 | evidence index, T109 deferred |

## 4. Architecture drift check

- `ArchitectureTest` is green: the planes are separated, agents are isolated from governance,
  security, repositories, and the coordinator, and there are no package cycles.
- `ContractDriftTest` is green: the 29 contract operations and the controller mappings match in
  both directions.
- `ArtifactSchemaTest` and the per-run artifact validation are green.
- **Changes since planning, all recorded in the gate register for review:**
  - `CapabilityReleaseReader`, a read-only evidence port outside the agent port;
  - the `LineageService` extraction;
  - the `metrics` package;
  - the governance-invariant checker, which is test support.
- There were no contract or JSON-schema file changes in Phases 9 and 10.

## 5. Orchestration evidence

| Behavior | Where it is shown |
|---|---|
| Sequential and parallel paths | SCN-A timeline: TESTING and SECURITY_VERIFICATION overlap; VALIDATION follows both. Also confirmed by hand in T120 |
| Gates | Every end-to-end run |
| Retry | RDR-01 (automated) and T120 by hand |
| Fallback | RDR-02 |
| Rollback | RDR-03 |
| Deadline | RDR-04 |
| Resume | RDR-05 |
| Policy exception | RDR-06 |
| Operator controls | RDR-07 (automated) and T120 by hand |
| Re-planning with reuse | SCN-C and `ReplanningServiceTest` |
| Governance invariants | Hold on all 10 end-to-end runs (`GovernanceInvariants`) |

## 6. Scenarios

SCN-A, SCN-B, and SCN-C each complete with readiness `READY` (asserted in their end-to-end tests).
Their red runs are recorded in `tdd-evidence.md`:
- SCN-B was refused before the click-limit code existed.
- SCN-C was refused at TESTING before the default-expiry code existed.

## 7. Documentation

The following documents were added in Phase 10:
- README, SECURITY, CONTRIBUTING;
- the architecture overview;
- testing, limitations, and trade-offs;
- the risk register;
- performance, security scans, quickstart validation, and release readiness;
- the final summary and the reviewer guide.

Two overclaims were found and corrected:
- the SCN-A mid-run ambiguity sentence (Phase 10);
- the quickstart discrepancies D-1 to D-3 (T136).

## 8. Compliance and change control

See [release-readiness.md](release-readiness.md). Policy results are `READY` on all scenarios, and
the failed-mandatory path is proven by RDR-06. Contract change-control approval (T112) is deferred
and remains the candidate's decision.

## 9. Evidence integrity

- **Provenance.** Exported evidence carries its provenance: commit, command, time, JDK, and
  `simulatedInput`.
- **Audit chains.** They verify as intact: `valid: true`, 110 events in the T120 manual run.
  Tampering is detected (`EvidenceControllerTest`, `AuditTamperDetectionTest`).
- **MTTR.** It reproduces by hand ([mttr-validation.md](mttr-validation.md)).
- **Simulated input.** Every simulated human decision is labeled as simulated.
- **Missing committed copies.** No committed evidence snapshots exist (T109 deferred); the evidence
  is regenerated under `target/`.
