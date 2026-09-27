# Release-readiness proposal (T122, FR-RDY-01)

> **This is a proposal, not a decision.** The candidate decides release readiness at gate **G6**
> (T125) in the [gate register](../governance/human-gate-register.md). The assistant cannot
> approve it.

## Proposed status: **NOT READY**

Under the constitution's release-blocking rules, the status stays NOT READY while any mandatory
human gate is unratified, and while any mandatory check has neither passed nor been covered by an
accepted exception. Both conditions hold today:

1. **Human gates.** G1–G5 are `PENDING RATIFICATION`; G6 and G7 are not yet reached; all 19 ADRs
   are `Proposed`. Implementation proceeded under delegated provisional progression, as recorded in
   the gate register.
2. **Open mandatory items.** Each one needs either resolution or the candidate's explicit
   acceptance as a limitation.

| # | Item | Evidence | Needed for READY WITH ACCEPTED LIMITATIONS |
|---|---|---|---|
| O-1 | FR-ORC-18 autonomy budget not implemented (T074, SD-1) | `TraceabilityMatrixTest` DEFERRED list; [risk R-01](risk-register.md) | implement T074, or accept the limitation at G6 |
| O-2 | FR-RPL-06 ambiguity found mid-run not implemented (T096, SD-1) | same | implement T096, or accept the limitation |
| O-3 | Dependency vulnerability scan not run (T128, NFR-SEC-05, constitution V) | [security-scans.md §2](security-scans.md) | **run the scan** and disposition the findings (preferred), or grant an explicit exception |
| O-4 | Performance targets PVT-19 and PVT-20 not met (NFR-PRF-01, T134 open) | [performance.md](performance.md) | accept as a demonstration limitation, or complete T134 |
| O-5 | Deviations awaiting review in the gate register (Phase 4 to Phase 10a) | [gate register](../governance/human-gate-register.md), lower table | review and decide each |

If the candidate ratifies G1–G5, accepts O-1, O-2, and O-4 as limitations, resolves O-3, and
decides O-5, the proposal would become **READY WITH ACCEPTED LIMITATIONS**. It cannot become READY
while O-1 and O-2 are open, because they are specification MUSTs.

## Mandatory validation status

| Check | Command | Result (2026-09-27) |
|---|---|---|
| Full suite, clean build | `mvnw -B -ntp clean verify` | see [convergence-report.md](convergence-report.md): all green |
| Clean-clone quickstart | [quickstart-validation.md](quickstart-validation.md) | §1–§6 pass; 3 wording discrepancies, fixed in `quickstart.md` (T136) |
| Random test order (NFR-TST-02) | `mvnw test -Dsurefire.runOrder=random` | 528/528 green |
| Scenarios with governance invariants | `Scenario*E2ETest`, `ReliabilityDrillsE2ETest` | SCN-A, SCN-B, SCN-C completed `READY`; 7 drill runs as expected; invariants hold on all 10 runs |
| Contract drift and artifact schemas | `ContractDriftTest`, `ArtifactSchemaTest` | no drift; every scenario artifact is valid |
| Traceability | `TraceabilityMatrixTest` | every FR and scenario is tested except the 2 deferred |
| Line coverage (NFR-TST-01, PVT-23 ≥ 80%) | JaCoCo report | 95.0% overall; 88.9–96.9% in the named packages. **Measured, not enforced** (T115 deferred) |
| Repository secret scan | `RepositorySecretScanTest` | pass; 0 unexplained findings |
| Startup log credential check | `StartupLogSecurityTest` | pass (after convergence fix T133) |
| Dependency vulnerability scan | OSV-Scanner | **not run** (O-3) |

## Policy and change-control results

- **Scenario runs.** Compliance evaluated the pinned policy set on every scenario run. SCN-A,
  SCN-B, and SCN-C reached readiness `READY` with no mandatory failure. In the T120 manual SCN-A
  run, the 14 evaluations were 12 `PASS` and 2 `NOT_APPLICABLE`.
- **Failed mandatory policy.** The path is proven by RDR-06:
  - an approved exception with a compensating control and an expiry leads to
    `READY_WITH_ACCEPTED_LIMITATIONS`;
  - a rejection leads to `SAFE_STOPPED`;
  - the governance-invariant checker confirms that no release follows an unexcepted failure.
- **Change control.**
  - SCN-B required the impact-analysis gate before the click-limit code (commit `53d8fc4` precedes
    `86e4320`).
  - Material change requests after an approval insert a `CHANGE_APPROVAL` gate
    (`ChangeRequestFlowTest`).
  - Contract version changes still need the candidate's change-control approval (T112, deferred).
