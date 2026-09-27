# Reviewer navigation guide

This guide covers the 25 navigation items of the assessment guide, §26. Each entry gives the path,
the command, the expected result, and the requirement or scenario it relates to. Commands assume
PowerShell with `$env:JAVA_HOME` set to a JDK 21, run from the repository root; `mvnw` means
`.\mvnw.cmd -B -ntp`. Items 5–11 need the application running in the demo profile (item 2).

**Before you start:**
- Human decisions in the automated evidence are **simulated**: they come from labeled demo
  principals.
- All gates and ADRs await the candidate's ratification.

## Run and test

| # | Item | Where / command | Expected result | Ref |
|---|---|---|---|---|
| 1 | Project objective | [README.md](../../README.md); [spec.md](../../specs/001-agentic-url-shortener/spec.md) | Two planes: a governed agentic SDLC control plane and a URL shortener | A§1 |
| 2 | Run the application | `mvnw spring-boot:run "-Dspring-boot.run.profiles=demo"`, then `curl http://localhost:8080/actuator/health` | Response contains `"status":"UP"`; the log shows `DEMO PROFILE ACTIVE` | ADR-014 |
| 3 | Run the tests | `mvnw clean verify` | 530 tests, 0 failures, BUILD SUCCESS (at `2a2a0bf`) | NFR-TST-02 |

## Use the system

| # | Item | Where / command | Expected result | Ref |
|---|---|---|---|---|
| 4 | Exercise the URL shortener | [quickstart.md §4](../../specs/001-agentic-url-shortener/quickstart.md) (token `demo-consumer-token`) | `201`, then `302` with `no-store`, then stats `totalClicks: 1`; a `javascript:` URL gets `400 URL_SCHEME_NOT_ALLOWED` | FR-LNK-*, FR-RED-* |
| 5 | Start an orchestration workflow | `POST /api/v1/workflows` with `--data @src/main/resources/scenarios/scn-a-greenfield.json` and `demo-requester-token` | `201` with a `runId` | FR-ORC-01 |
| 6 | Inspect workflow state | `GET /api/v1/workflows/{runId}`, `/timeline`, `/artifacts`, `/decisions`, `/plan-versions` (any control-plane token) | Stages with status and dependencies; pending actions; timeline intervals | FR-ORC-09 |
| 7 | Perform a human approval | `POST /api/v1/workflows/{runId}/gates/ARCHITECTURE_APPROVAL/decision` with `demo-approver-token` and `{"decision":"APPROVE","rationale":"…"}` | `200`; the same call with the requester token returns `403` | FR-GOV-02..05 |

## Demonstrate the engine

| # | Item | Where / command | Expected result | Ref |
|---|---|---|---|---|
| 8 | Retry | `mvnw test "-Dtest=ReliabilityDrillsE2ETest"` (RDR-01); by hand: [quickstart §6](../../specs/001-agentic-url-shortener/quickstart.md) | TESTING attempts `FAILED_TRANSIENT`, `FAILED_TRANSIENT`, `SUCCEEDED`; run `COMPLETED` | FR-REL-01, RDR-01 |
| 9 | Compensation or rollback | Same suite (RDR-03); [reliability.md §4](../architecture/reliability.md) | Post-release verification fails; release rolled back; run `SAFE_STOPPED` | FR-REL-04/05, RDR-03 |
| 10 | Safe-stop | RDR-03, RDR-04, RDR-07; `POST …/safe-stop` with `demo-release-token` | Run `SAFE_STOPPED`; a `SAFE_STOP` decision is recorded; a final summary is produced | FR-REL-06/07 |
| 11 | Dynamic re-planning | `mvnw test "-Dtest=ScenarioCAmbiguousE2ETest,ReplanningServiceTest"`; `GET …/plan-versions` | Plan version 2 with a diff (IMPACT_ANALYSIS and REGRESSION_TESTING added); unaffected stages reused | FR-RPL-01..05 |

## The three scenarios

| # | Item | Where / command | Expected result | Ref |
|---|---|---|---|---|
| 12 | Greenfield scenario | [scn-a-greenfield.md](../scenarios/scn-a-greenfield.md); `mvnw test "-Dtest=ScenarioAGreenfieldE2ETest"` | `COMPLETED`, `READY`; `custom-alias` released; evidence in `target/evidence/scn-a/` | SCN-A |
| 13 | Brownfield scenario | [scn-b-brownfield.md](../scenarios/scn-b-brownfield.md); `mvnw test "-Dtest=ScenarioBBrownfieldE2ETest,BrownfieldImpactGateIT"` | Impact gate before code; `COMPLETED`, `READY`; `click-limit` released | SCN-B |
| 14 | Ambiguous-requirement scenario | [scn-c-ambiguous.md](../scenarios/scn-c-ambiguous.md); `mvnw test "-Dtest=ScenarioCAmbiguousE2ETest"` | Stops at clarification (7 questions); re-plans; `COMPLETED`, `READY`; default expiry 30 days | SCN-C |

## Design and evidence

| # | Item | Where / command | Expected result | Ref |
|---|---|---|---|---|
| 15 | Architecture | [overview.md](../architecture/overview.md); `mvnw test "-Dtest=ArchitectureTest"` | Plane and agent-isolation rules pass | NFR-MNT-01 |
| 16 | ADRs | [docs/adr/README.md](../adr/README.md) | 19 ADRs, all `Proposed` (gate G4) | ADR-001..019 |
| 17 | Requirement traceability | `mvnw test "-Dtest=TraceabilityMatrixTest"`, then open `target/traceability/requirements-to-tests.md` | 126 of 136 ids verified; FR-ORC-18 and FR-RPL-06 `DEFERRED` | SC-003 |
| 18 | Audit evidence | `GET …/audit` and `…/audit/verification`; `mvnw test "-Dtest=EvidenceControllerTest,AuditTamperDetectionTest"` | `valid: true`; tampering gives `valid: false` with `firstBrokenSeq` | FR-AUD-01..03, SC-006 |
| 19 | Reliability measurements | `GET /api/v1/reliability/report` (auditor); [mttr-validation.md](mttr-validation.md) | Labeled `DEMONSTRATION DATA`; MTTR, rates, and latency | FR-AUD-04, SC-008 |
| 20 | Security controls | [SECURITY.md](../../SECURITY.md); `mvnw test "-Dtest=*Security*Test,UrlSecurityCatalogIT,RepositorySecretScanTest"` | All green; [security-scans.md](security-scans.md) §2: 3 CRITICAL Tomcat advisories fixed by upgrading to 11.0.26; re-scan reports no issues | NFR-SEC-* |
| 21 | Known limitations | [testing-limitations-tradeoffs.md](testing-limitations-tradeoffs.md), [risk-register.md](risk-register.md) | Deferred FRs, performance miss, single process, scan gap | — |
| 22 | Final engineering summary | [final-engineering-summary.md](final-engineering-summary.md) | 21 sections in the guide's order; proposal NOT READY | A§5 |
| 23 | API contracts, schemas, versions, compatibility, migrations, examples, contract tests | [openapi.yaml](../../specs/001-agentic-url-shortener/contracts/openapi.yaml), [schemas/](../../specs/001-agentic-url-shortener/contracts/schemas/), [CHANGELOG.md](../../specs/001-agentic-url-shortener/contracts/CHANGELOG.md), `src/main/resources/db/migration/V1..V4`, [links.md](../api/links.md); `mvnw test "-Dtest=ContractDriftTest,*ContractTest,ArtifactSchemaTest,MigrationTest"` | No drift; responses and artifacts valid. The compatibility review is **deferred** (T112) | NFR-CHG-01/02 |
| 24 | Compliance and change-control results, approved exceptions | [release-readiness.md](release-readiness.md), [policy-set.md](../governance/policy-set.md); RDR-06 in `ReliabilityDrillsE2ETest`; `ChangeRequestFlowTest` | Scenarios `READY`; exception approved gives `READY_WITH_ACCEPTED_LIMITATIONS`, rejected gives `SAFE_STOPPED`. No exception exists for this project's own release | FR-POL-*, FR-GOV-06..09 |
| 25 | MTTR inputs, recovered population, exclusions, unrecovered failures, limitations | [mttr-validation.md](mttr-validation.md); `target/evidence/reliability/reliability-report.json` after `mvnw test "-Dtest=ReliabilityDrillsE2ETest"` | 2 recovered (RETRY, FALLBACK), 1 unrecovered (RDR-03), 0 exclusions; the hand calculation equals the report | FR-AUD-04, NFR-RCV-02 |

**Further detail:**
- [task-group-checkpoints.md](task-group-checkpoints.md) and [tdd-evidence.md](tdd-evidence.md):
  how each phase was built and tested.
- [human-gate-register.md](../governance/human-gate-register.md): every open decision.
