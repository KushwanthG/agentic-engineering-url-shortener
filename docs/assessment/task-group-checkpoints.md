# Task-Group Checkpoints and Pre-Commit Reviews

Records the task-group checkpoint (SDD guide §16) and pre-commit review (§17) at every task-group
boundary of `/speckit-implement`, as required by the tasks' definition of done (analysis finding E1).
Entries are written by the AI assistant during the delegated session; human review is pending
(gate G5).

## Implementation start decision (2026-09-26)

`/speckit-implement` checklist gate result:

| Checklist | Total | Checked | Unchecked | Status |
|-----------|-------|---------|-----------|--------|
| architecture.md | 20 | 0 | 20 | FAIL |
| observability-testing.md | 20 | 0 | 20 | FAIL |
| orchestration.md | 38 | 0 | 38 | FAIL |
| release-submission.md | 18 | 0 | 18 | FAIL |
| requirements-traceability.md | 25 | 0 | 25 | FAIL |
| requirements.md (built-in) | 16 | 16 | 0 | PASS |
| scenarios.md | 21 | 0 | 21 | FAIL |
| security-compliance.md | 25 | 0 | 25 | FAIL |

The skill asks the user whether to proceed when checklists have unchecked items. The candidate is
not available in this session. The assistant therefore proceeds **provisionally** under the
constitution's Delegated Provisional Progression clause, on the strength of the candidate's
instruction to build the project (verbatim in the gate register). This is a recorded process
deviation, **not** an approval. The custom checklists remain reviewer-owned and unchecked; gates
G1–G5 remain PENDING RATIFICATION.

---

## Checkpoint: Phase 1 — Setup (T001–T003; T004 gate pending)

| # | Item | Record |
|---|------|--------|
| 1 | Completed tasks | T001, T002, T003. T004 (human gate G1–G5) remains PENDING for the candidate |
| 2 | Requirements addressed | CON-01, CON-02, NFR-TST-02, NFR-SEC-05 (SBOM), SC-001 (wrapper) |
| 3 | ADRs followed | ADR-002 (Boot 4.1.1, Java 21, enforcer), ADR-003 (H2 runtime scope), ADR-013 (test stack), ADR-014 (wrapper) |
| 4 | Files created or changed | `docs/assessment/timebox-and-scope.md`, `pom.xml`, `mvnw`, `mvnw.cmd`, `.mvn/wrapper/maven-wrapper.properties`, `.gitignore` (added `*.jar`, `*.tmp`, `*.swp`), `docs/assessment/tdd-evidence.md`, this file |
| 5 | Tests written before implementation | none: build configuration only (TDD N/A per tasks) |
| 6 | Expected initial failures | n/a |
| 7 | Validation commands executed | `mvn -B -ntp compile` (Maven 3.8.4, JDK 21) → BUILD SUCCESS; `mvn -N wrapper:wrapper -Dmaven=3.9.16 -Dtype=only-script` → BUILD SUCCESS; `.\mvnw.cmd -v` → Maven 3.9.16, Java 21.0.12.1 |
| 8 | Actual outcomes | enforcer rules passed; CycloneDX wrote `target/classes/META-INF/sbom/application.cdx.json` (104 components) during `compile`; 15 contract files copied to `classpath:contracts/` |
| 9 | Documentation updated | timebox and scope plan |
| 10 | Traceability updated | tasks T001–T003 marked complete |
| 11 | Deviations from plan | T002 names `mvnw verify` as its validation; `verify` needs the main class from T005 (Spring Boot repackage), so `compile` was used now and `verify` runs at the Phase 2 checkpoint |
| 12 | New risks | none |
| 13 | New assumptions | none |
| 14 | Remaining failures | none |
| 15 | Commit boundary | Phase 1 |
| 16 | Commit message | `chore(build): add Maven build, wrapper, and scope-control plan` |
| 17 | Next task group | Phase 2 Foundational (T005–T016) |
| 18 | Human approval required | not for this group; G1–G5 remain pending |

**Pre-commit review**: change intent is build baseline only; no application code, API, schema, or
state-model changes; no unrelated files; single coherent commit.

---

## Checkpoint: Phase 2 — Foundational (T005–T016)

| # | Item | Record |
|---|------|--------|
| 1 | Completed tasks | T005–T016 |
| 2 | Requirements addressed | FR-ORC-08, NFR-CHG-01/02, FR-LNK-05 (constraint), FR-ORC-10, FR-GOV-05, FR-RPL-01 (fingerprints), FR-OPS-01, FR-OPS-02 (fail-fast config), FR-OPS-03, FR-OPS-04, FR-AUD-01, FR-AUD-02, FR-AUD-05, NFR-AUD-02, SC-006, FR-LNK-13 (routes), NFR-SEC-02/03, FR-GOV-03 (roles), NFR-SCA-01 (stateless), NFR-MNT-01, CON-02 |
| 3 | ADRs followed | ADR-001 (package boundaries), ADR-003 (H2 file, Flyway, validate), ADR-006 (persistence), ADR-012 (hash chain), ADR-013 (test stack, contract harness), ADR-014 (profiles), ADR-015 (hashed bearer tokens) |
| 4 | Files created or changed | `src/main/java/com/agentic/sdlc/{AgenticSdlcApplication, platform/{json,time,web,security}/*, orchestration/{model/ActorType, audit/*}}`, `src/main/resources/{application*.yml, db/migration/V1, V2}`, tests under `src/test/java/com/agentic/sdlc/{persistence, platform, orchestration/audit, architecture, contract, support}` and `WalkingSkeletonIT`, `src/test/resources/application-test.yml`, `CLAUDE.md`, `.gitattributes`, `data-model.md`, `contracts/openapi.yaml`, `contracts/CHANGELOG.md` |
| 5 | Tests written before implementation | all Phase 2 tests except `ArchitectureTest` (verification) — red runs recorded in `tdd-evidence.md` |
| 6 | Expected initial failures | missing tables; stub behavior (see evidence log) |
| 7 | Validation commands executed | `mvnw test -Dtest=MigrationTest` (red, then green); `mvnw test` (Phase 2 red, then green); `mvnw verify` |
| 8 | Actual outcomes | `mvnw verify`: 54 tests, 0 failures, BUILD SUCCESS |
| 9 | Documentation updated | `data-model.md` (type conventions, `audit_chain_head`, `scheduling_cycle`, `trigger_type`, canonical-JSON audit hash), `CLAUDE.md`, contract code list, changelog |
| 10 | Traceability updated | tests tagged with requirement ids; tasks marked |
| 11 | Deviations from plan | (a) persistence schema refinements listed in row 9 (implementation details of FR-AUD-02 and RC-2; recorded for change-control review in T112); (b) three generic error codes added to the contract baseline (`RESOURCE_NOT_FOUND`, `METHOD_NOT_ALLOWED`, `UNSUPPORTED_MEDIA_TYPE`), additive, recorded for T112; (c) `ScriptedAgent` moved from T015 to T040 (task dependency error: it implements the SPI created in T039); (d) security test positive metrics check uses `/actuator/metrics` because Spring Boot disables the Prometheus registry in tests |
| 12 | New risks | Spring Boot disables metrics export in tests, so the Prometheus endpoint itself is only exercised when the application runs (quickstart validation T120 covers it) |
| 13 | New assumptions | H2 `LOCK_TIMEOUT=10000` (10 s) for pessimistic audit locks |
| 14 | Remaining failures | none |
| 15 | Commit boundary | Phase 2 |
| 16 | Commit message | `chore: establish project quality and test baseline` |
| 17 | Next task group | Phase 3 US1 — URL shortener core (T017–T033) |
| 18 | Human approval required | the contract and schema refinements in row 11 need the candidate's change-control review (added to the gate register pending items) |

**Pre-commit review**: coherent foundation change; no unrelated files; API contract change limited to
additive generic error codes (documented); no weakening of security (deny-by-default, hashed tokens);
all tests green.

---

## Checkpoint: Phase 3 — US1 URL shortener core (T017–T033)

| # | Item | Record |
|---|------|--------|
| 1 | Completed tasks | T017–T033 |
| 2 | Requirements addressed | FR-LNK-01..13, FR-RED-01..05, FR-ANL-01..05, FR-OPS-01..04, FR-CAP-01 (unreleased rejection), NFR-SEC-01, NFR-SEC-04, NFR-REL-01, NFR-SCA-02, NFR-CHG-01, SC-005 |
| 3 | ADRs followed | ADR-001 (planes, layered packages), ADR-003 (H2, Flyway, validate), ADR-004 (random base62, DB uniqueness, bounded retry), ADR-013 (contract tests), ADR-015 (bearer roles), ADR-016 (exact synchronous analytics, fail-open), ADR-018 (capability flags, read side) |
| 4 | Files created or changed | `shortener/{controller,dto,domain,repository,service,config}/*` (new); `orchestration/audit/AuditService`, `orchestration/repository/AuditChainHeadRepository`, `orchestration/domain/AuditChainHead` (defect fix); `application.yml` (self-hosts, readiness group, H2 `TIME ZONE=UTC`), `application-test.yml`; tests under `shortener/`, `security/UrlSecurityCatalogIT`, `contract/LinkApiContractTest`, `support/{ControllableTestConfig, MaliciousUrlCatalog}`; `docs/api/links.md`; package restructure to `com.agentic.urlshortener.{common,shortener,orchestration}` with layer packages (candidate request) and matching path updates in `plan.md`, `tasks.md`, ADR-001 |
| 5 | Tests written before implementation | all Phase 3 tests (compile-level red run recorded in `tdd-evidence.md`) |
| 6 | Expected initial failures | missing production types (15) |
| 7 | Validation commands executed | `mvnw test-compile` (red); `mvnw clean verify` ×6 (2 red-to-green iterations, then 3 consecutive green); targeted `-Dtest=` runs; temporary stress diagnostic; live smoke test of the packaged jar |
| 8 | Actual outcomes | final: 158 tests, 0 failures, 0 errors, BUILD SUCCESS, three consecutive runs |
| 9 | Documentation updated | `docs/api/links.md` (new); ADR-001 revision history; `plan.md` structure; `tasks.md` paths |
| 10 | Traceability updated | tests tagged with FR/NFR/SC ids; tasks T017–T033 marked |
| 11 | Deviations from plan | (a) package layout: `common` replaces `platform`, planes use `controller/dto/domain/repository/service/config` (candidate request, ADR-001 revised, still Proposed); (b) `UrlRejection.java` not created — the policy throws `ApiException` with the contract codes directly; (c) `CapabilityProvider.java` not created — a `Capability` enum lists the capabilities and `CapabilityRegistry` creates their rows; (d) `RateLimitProperties` is the nested record `ShortenerProperties.RateLimit`; rate limiter lives in `shortener/service`, limiter beans in `shortener/config/RateLimiters`; (e) `LinkStoreHealthIndicator` in `shortener/config` instead of `persistence`; (f) H2 session time zone fixed to UTC (runtime and test URLs); (g) audit sequence allocation reimplemented (atomic update plus serialized head creation) to fix a concurrency defect of Phase 2 |
| 12 | New risks | the audit head-creation guard is in-process; valid because the H2 file database admits one process (ADR-003), must be revisited with a server database (backlog BL-02) |
| 13 | New assumptions | none |
| 14 | Remaining failures | none |
| 15 | Commit boundary | Phase 3 |
| 16 | Commit message | `feat(shortener): implement URL shortener core with analytics and rate limits` |
| 17 | Next task group | Phase 4 US2 — orchestration core and SCN-A (T034–T058, T129–T131) |
| 18 | Human approval required | the package-layout revision of ADR-001 and deviations (b)–(g) for the candidate's review; G1–G5 remain pending |

**Pre-commit review**: one coherent feature (US1) plus the package restructure requested by the
candidate and one defect fix in the audit trail found by the Phase 3 runs; no contract changes;
security rules unchanged (deny-by-default; redirect route public by design); all tests green.

---

## Checkpoint: Phase 4 — US2 orchestration core and SCN-A (T034–T058, T129–T131)

| # | Item | Record |
|---|------|--------|
| 1 | Completed tasks | T034–T058, T129, T130, T131 |
| 2 | Requirements addressed | as tagged on the Phase 4 tests: FR-AUD-03, FR-AUD-06, FR-CAP-01, FR-CAP-02, FR-GOV-01, FR-GOV-03, FR-GOV-08, FR-GOV-09, FR-ORC-01, FR-ORC-02, FR-ORC-03, FR-ORC-04, FR-ORC-05, FR-ORC-06, FR-ORC-07, FR-ORC-08, FR-ORC-09, FR-ORC-10, FR-ORC-11, FR-ORC-12, FR-ORC-13, FR-ORC-14, FR-ORC-15, FR-ORC-16, FR-ORC-17, FR-POL-01, FR-POL-02, FR-POL-03, FR-POL-05, FR-POL-06, FR-RDY-01, FR-RDY-03, FR-RDY-04, FR-REL-05, FR-RPL-03, NFR-AUD-01, NFR-AUT-01, NFR-CHG-01, NFR-SEC-01, NFR-SEC-05, SC-002, SCN-A, SCN-B, SCN-C (SCN-B/SCN-C tags are agent-level tests on those inputs only; their end-to-end scenarios are Phases 7–8); SC-009 for SCN-A evidence |
| 3 | ADRs followed | ADR-001 (planes; only `orchestration.integration` calls the shortener), ADR-005 (persisted DAG engine), ADR-007 (state machines), ADR-008 (human gates), ADR-010 (synthetic-data compensation by run), ADR-017 (deterministic agents), ADR-018 (capability release and preview) |
| 4 | Files created or changed | `orchestration/{domain,repository,engine,planning,agent,agent/probes,port,integration,knowledge,policy,governance,service,controller,dto,config}/*`; shortener alias support (`AliasPolicy`, `CustomAliasCapability`, `CapabilityChange`, alias path in `LinkCreationService`, `ShortLink`, `Capability`, DTOs, `ShortenerConfig`); `res/db/migration/V3__custom_alias.sql`; `res/orchestration/{capability-catalog,ambiguity-lexicon,policy-set}.yaml`; `res/scenarios/scn-a-greenfield.json`; tests under `orchestration/*`, `e2e/ScenarioAGreenfieldE2ETest`, `contract/WorkflowApiContractTest`, `contract/OpenApiContract` (harness), `support/*`; `docs/api/links.md`, `docs/scenarios/scn-a-greenfield.md`, `docs/architecture/orchestration.md` |
| 5 | Tests written before implementation | all Phase 4 tests; red runs in `tdd-evidence.md` (compile-level per group; behavioral red for T056 and T057) |
| 6 | Expected initial failures | missing production types per group; 404s for the workflow API; SCN-A failing until the full path worked |
| 7 | Validation commands executed | per-group `mvnw -o test-compile` (red) and `mvnw test -Dtest=…` (green); `mvnw test -Dtest=ScenarioAGreenfieldE2ETest`; `mvnw clean verify` offline (failed, SBOM skipped) and online |
| 8 | Actual outcomes | `mvnw -B -ntp verify` (online): 343 tests, 0 failures, 0 errors, SBOM generated, BUILD SUCCESS; SCN-A `COMPLETED` / `READY`; evidence E-A1..E-A9 in `target/evidence/scn-a/` |
| 9 | Documentation updated | `docs/scenarios/scn-a-greenfield.md`, `docs/architecture/orchestration.md` (new); `docs/api/links.md` (alias); `tdd-evidence.md`; gate register pending items |
| 10 | Traceability updated | tests tagged with FR/SC/SCN ids (`SCN-A` on the end-to-end test); tasks T034–T058, T129–T131 marked |
| 11 | Deviations from plan | (a) stage transition `PENDING → FAILED` added for unmet entry criteria. It is not in the `data-model.md` diagram and was raised for review, not changed silently. (b) `QualityChecks.java` not created: the five checks are private methods of `RequirementAnalysisAgent`. (c) New `SyntheticScope`: the parallel probe stages of one run serialize their probe-and-cleanup section, because ADR-010 cleans up synthetic data by run. The two stages still dispatch in one cycle. This was found by SCN-A. (d) The contract test harness merges `allOf` before validation (`withResolveCombinators(true)`); `openapi.yaml` is unchanged. (e) SCN-A checks audit integrity through `AuditService.verify` inside the test JVM, because the evidence API (`EvidenceController`) is Phase 9. Everything else in the scenario goes over HTTP. |
| 12 | New risks | (1) `SyntheticScope` is an in-process lock. It is valid for the single-process H2 deployment (ADR-003) and must be revisited with a server database, like the audit-head guard (BL-02). (2) The full build must run online: offline Maven skips the CycloneDX SBOM, and policy LIC-001 then fails. |
| 13 | New assumptions | none |
| 14 | Remaining failures | none |
| 15 | Commit boundary | Phase 4 |
| 16 | Commit message | `feat(orchestration): implement governed workflow engine, agents, and SCN-A` |
| 17 | Next task group | Phase 5 US3 — govern high-impact decisions (T059–T066) |
| 18 | Human approval required | the state-model refinement (a) and deviations (b)–(e) for the candidate's review (gate register). Gate decisions in SCN-A are simulated demo-principal input, not approvals. G1–G5 remain pending |

**Pre-commit review**:
- One coherent feature: the US2 control plane and SCN-A.
- The shortener changes are limited to the custom-alias capability, which is released only through
  the port.
- No change to the contract file or the ADRs. One state-model refinement was raised for review.
- The plane boundary is enforced by ArchUnit. Agents are permission-scoped.
- The simulated approvals are labeled in the test, the decisions, and the evidence.
- All tests are green.

---

## Checkpoint: Phase 5 — US3 govern high-impact decisions (T059–T066)

| # | Item | Record |
|---|------|--------|
| 1 | Completed tasks | T059–T066 |
| 2 | Requirements addressed | FR-GOV-02, FR-GOV-03, FR-GOV-04, FR-GOV-05, FR-GOV-06, FR-GOV-07, FR-GOV-09, FR-REL-06 (gate deadline path), NFR-AUT-01, NFR-SEC-02 |
| 3 | ADRs followed | ADR-008 (human approval model), ADR-010 (compensation of synthetic data), ADR-015 (roles), ADR-005 (scheduler) |
| 4 | Files created or changed | `governance/{GateService, DeadlineSweeper}`, `engine/RunCoordinator` (re-validation guard, `safeStop`, `escalateExpiredGate`), `domain/StageNode.reopen`, `repository/StageNodeRepository`, `reliability/CompensationCoordinator` (new), `config/SchedulingConfig` (new), `application.yml` / `application-test.yml` (`deadline-sweep-interval`); tests `governance/{SeparationOfDuties, ApprovalBinding, GateDeadline, ConcurrentGateDecision, GateRejection, WaitingGateIndependence}Test`, `security/GovernanceSecurityMatrixTest`, `ArchitectureTest`, `support/GovernanceHarness`; `docs/architecture/governance.md` |
| 5 | Tests written before implementation | T059–T063 (red recorded); T065 verification tests |
| 6 | Expected initial failures | requester self-approval accepted, late decision accepted, no invalidation, no escalation, no compensation |
| 7 | Validation commands executed | see `tdd-evidence.md` Phase 5; `mvnw -B -ntp verify` |
| 8 | Actual outcomes | 392 tests, 0 failures, BUILD SUCCESS |
| 9 | Documentation updated | `docs/architecture/governance.md` (new); approval evidence remains SCN-A `E-A5-decisions.json` (decisions with bound fingerprints) |
| 10 | Traceability updated | tests tagged FR-GOV-02..07, FR-GOV-09, NFR-AUT-01, NFR-SEC-02, RDR-04; tasks marked |
| 11 | Deviations from plan | (a) `CompensationCoordinator` created early in its Phase 6 package with synthetic-data cleanup only; T070 extends it. (b) Safe-stop on deadline uses `RunCoordinator.safeStop` until `SafeStopService` (T071). (c) Re-opening an invalidated gate also re-opens every downstream stage as a new generation and invalidates downstream approvals: this interprets FR-GOV-05's "gate re-opened" for work that already consumed the approval. (d) The sweep interval is a plain property (`app.orchestration.deadline-sweep-interval`), not a field of `OrchestrationProperties`. (e) The exception-requester half of T059 moves to T076, where policy exceptions are implemented. |
| 12 | New risks | the run lock and the deadline sweeper are in-process; valid for the single-process H2 deployment (ADR-003, backlog BL-02) |
| 13 | New assumptions | a decision arriving after its deadline but before the sweep is refused (`DEADLINE_PASSED`), not recorded |
| 14 | Remaining failures | none |
| 15 | Commit boundary | Phase 5 |
| 16 | Commit message | `feat(governance): enforce separation of duties, approval binding, and gate deadlines` |
| 17 | Next task group | Phase 6 US4 — reliability, readiness, and drills (T067–T082, T074 deferred by SD-1) |
| 18 | Human approval required | deviation (c) (re-open scope) and (a)–(e) for the candidate's review |

**Pre-commit review**:
- The governance hardening is limited to `GateService`, the scheduler guard, and the sweeper.
- There are no contract or schema changes. The existing error codes (`SEPARATION_OF_DUTIES`,
  `DEADLINE_PASSED`, `CONCURRENT_DECISION`) are now used.
- The anti-bypass rules are executable.
- All tests are green.

---

## Checkpoint: Phase 6 — US4 reliability, readiness, and drills (T067–T082; T074 deferred)

| # | Item | Record |
|---|------|--------|
| 1 | Completed tasks | T067–T073, T075–T082. T074 (autonomy budget) is deferred by SD-1 |
| 2 | Requirements addressed | FR-REL-01..11, FR-POL-03, FR-POL-04, FR-POL-06, FR-RDY-01..04, FR-ORC-08, FR-ORC-17, NFR-REL-02, NFR-REL-03, NFR-RCV-01, SC-002, SC-007 |
| 3 | ADRs followed | ADR-009 (retry, fallback, safe-stop), ADR-010 (rollback versus compensation), ADR-006 (persisted state, recovery), ADR-019 (policy exceptions, readiness), ADR-014 (fault injection only in demo and test profiles) |
| 4 | Files created or changed | **new:** `reliability/{RetryPolicy, StagePolicyProperties, FailureClassifier, FailureEventRecorder, FaultInjector, SafeStopService, RecoveryService}` (`CompensationCoordinator` extended); `agent/{TemplateDocumentationAgent, MinimalSummaryAgent, TransientStageException, PermanentStageException}`; `policy/PolicyExceptionService`; `controller/OperationsController`; `service/OperationsService`; `domain/FaultPlan`; `dto/{OperatorActionRequest, PolicyExceptionRequest, PolicyExceptionView}`. **changed:** `engine/{RunCoordinator, StageDispatcher, Dispatch}`, `StageResult.TimedOut`, `ReleaseAgent` (conflict-aware rollback), `DocumentationAgent` (shared repository check), `PolicyEngine`/`PolicyContext`/`RepositoryRunFacts` (simulated policy failures), `GateService` (release readiness re-check), `GovernanceController` (exception endpoints), `WorkflowService` (fault validation), `CapabilityState` (`changedByRun`), `CapabilityService`, `InProcessApplicationPlaneAdapter`, repositories, `application.yml` (stage policies, query timeout). **tests:** 12 new test classes, `support/{HttpDriver}`, `ScriptedAgent` (registers production fallbacks). **docs:** `docs/architecture/reliability.md`, `docs/operations/runbook.md`, `docs/scenarios/drills.md`, `docs/governance/policy-set.md`; orchestration and SCN-A docs updated |
| 5 | Tests written before implementation | T067–T078 (red recorded; two red runs were compile-level); T079, T080 are verification |
| 6 | Expected initial failures | no retry, fallback, recovery, faults, or exception endpoints |
| 7 | Validation commands executed | see `tdd-evidence.md` Phase 6; `mvnw -B -ntp verify` twice (1 test defect, then green) |
| 8 | Actual outcomes | 444 tests, 0 failures, SBOM generated, BUILD SUCCESS; drills RDR-01..RDR-07 green |
| 9 | Documentation updated | see row 4 |
| 10 | Traceability updated | tests tagged FR-REL-*, FR-POL-*, FR-RDY-*, RDR-*; tasks marked |
| 11 | Deviations from plan | (a) **Safe-stop decision:** safe-stop records one `SAFE_STOP` decision whose payload lists every compensation action; each action is audited separately. T070 asked for a decision per action, and no per-action decision type exists in the data model. (b) **Fallback attempt limit:** a fallback attempt may follow exhausted retries, so a stage runs at most `max-attempts + 1` times. (c) **Rollback of a previously released capability:** compensation cannot restore one, because `release-record.schema.json` has no previous parameters. This is reported as a `FAILED` action requiring manual intervention; the contract schema was not changed. (d) **Autonomy budget:** the `SafeStopServiceTest` triggers cover stage failure, deadline, operator request, compensation failure, and policy-exception rejection (in `PolicyExceptionFlowTest`). The autonomy budget trigger is deferred (T074); clarification rounds are Phase 8. (e) **Stage policies:** the properties use `defaults` and `overrides` (a component cannot be named `default`). (f) **Fault stage validation:** faults are validated against their stage (for example, `VERIFICATION_FAILURE` applies only to probe stages). (g) **Dispatcher shutdown:** the dispatcher drops results during shutdown, so an interrupted attempt stays open for recovery, as after a process kill |
| 12 | New risks | wake-ups, locks, and the probe mutex are in-process (single-process H2, BL-02); compensation fault occurrences are counted in memory |
| 13 | New assumptions | a fallback runs at most once per stage generation |
| 14 | Remaining failures | none |
| 15 | Commit boundary | Phase 6 |
| 16 | Commit message | `feat(reliability): add retry, fallback, compensation, safe-stop, recovery, and policy exceptions` |
| 17 | Next task group | Phase 7 US5 — SCN-B brownfield (T083–T090) |
| 18 | Human approval required | deviations (a)–(g) for the candidate's review; the CP2 scope record confirms that SD-1 continues |

**Pre-commit review**:
- **Scope.** One coherent capability, run reliability. The shortener changes are limited to exposing
  `changedByRun` through the port.
- **No contract change.** The new endpoints (pause, resume, safe-stop, policy exceptions) were
  already in `openapi.yaml` and are validated by the contract harness in tests.
- **Fault injection stays off by default.** A test proves the default profile refuses faults.
- **Tests.** All tests are green.

---

## Checkpoint: Phase 7 — US5 change existing behavior safely, SCN-B (T083–T090)

| # | Item | Record |
|---|------|--------|
| 1 | Completed tasks | T083–T090 |
| 2 | Requirements addressed | FR-ORC-14, FR-ORC-15, FR-CAP-03, FR-ANL-04 (fail-closed exception), FR-REL-03 (impact fallback), NFR-CHG-01, NFR-CHG-02, SC-002 (SCN-B), SC-009 (E-B1..E-B9) |
| 3 | ADRs followed | ADR-017 (knowledge-driven agents, codebase scan), ADR-016 (analytics consistency: fail-closed only for limited links), ADR-018 (capability flag), ADR-010 (rollback: withdraw the flag, stored limits stay) |
| 4 | Files created or changed | `knowledge/CodebaseScanner`; `agent/{ImpactAnalysisAgent, CatalogImpactAnalysisAgent, ImpactReports, RegressionTestingAgent}`; `agent/probes/{RegressionProbes, ClickLimitProbes}`, `SecurityProbes` (CL-SEC-BOUNDS), `ProbeRegistry`, `ProbeContext`; port `SyntheticLinkSpec` (idempotency key), `LinkSnapshot` (`maxClicks`), `ApplicationPlanePort.withSyntheticClickOutage`; shortener `V4__click_limit.sql`, `ShortLink`, `ShortLinkRepository`, `ClickRecorder`, `RedirectService`, `RedirectController`, `Resolution`, `LinkView`, `LinkCreationService`, `ClickLimitCapability`; catalog regression risks; `res/scenarios/scn-b-brownfield.json`; docs `scn-b-impact-analysis.md` (gate), `scn-b-brownfield.md`, `docs/api/links.md`, `reliability.md` |
| 5 | Tests written before implementation | T083–T085, T087, T089 (red recorded); T086 is analysis; T088 is the green step |
| 6 | Expected initial failures | missing scanner and agents; SCN-B refused at `IMPLEMENTATION` (capability not delivered); limits neither stored nor enforced |
| 7 | Validation commands executed | see `tdd-evidence.md` Phase 7; two full `mvnw verify` runs |
| 8 | Actual outcomes | 462 tests green; SCN-B `COMPLETED`/`READY`, all 14 policies `PASS`; SCN-A still green |
| 9 | Documentation updated | see row 4 |
| 10 | Traceability updated | tests tagged FR-ORC-14/15, FR-CAP-03, SCN-B; tasks marked |
| 11 | Deviations from plan | (a) **Probe-only outage simulation.** Needed to verify AC-6 live: a thread-scoped click-store outage (`ApplicationPlanePort.withSyntheticClickOutage`, permission `PREVIEW_CAPABILITY`) that affects only synthetic links on the probing thread. It is a new port operation; plan and contract are unchanged. (b) **Regression risks in the catalog.** Added as reviewable knowledge (plan §11 names them); the agent does not invent them. (c) **Regression probes for click-limit** extended from R-P1..R-P4 to R-P1..R-P6 (statistics and idempotent replay are in the impact closure). (d) **Impact-analysis fallback.** `IMPACT_ANALYSIS` has a catalog-only fallback (plan §3 table); `FallbackTest` and `reliability.md` were updated. (e) **Two commits in this phase.** The gate commit had to precede V4 (T086 guardrail). (f) **Idempotency residue.** Synthetic-link cleanup leaves the probe's idempotency records with `link_id` set to null (FK `ON DELETE SET NULL`); they hold no link data |
| 12 | New risks | the thread-scoped outage simulation is test machinery in production code; it is confined to synthetic links and a permission-checked port call |
| 13 | New assumptions | a click-limited link's click is either counted or refused, never served uncounted |
| 14 | Remaining failures | none |
| 15 | Commit boundary | Phase 7 (second commit after the gate commit `53d8fc4`) |
| 16 | Commit message | `feat(shortener): add click-limited links through the SCN-B brownfield workflow` |
| 17 | Next task group | Phase 8 US6 — SCN-C ambiguous requirement, clarification, re-planning (T091–T100; T096 deferred) |
| 18 | Human approval required | **the impact analysis review (T086) is PENDING**; deviations (a)–(f) for review |

**Pre-commit review**:
- The click-limit change is additive: a nullable column, and unlimited links keep their exact code
  path.
- Contract 1.2.0 already declared the fields, and the contract test covers them.
- The brownfield gate order is visible in `git log`.
- SCN-A still passes as the regression guard.
- All tests are green.

---

## Checkpoint: Phase 8 — US6 ambiguous requirement, SCN-C (T091–T100; T096 deferred)

| # | Item | Record |
|---|------|--------|
| 1 | Completed tasks | T091–T095, T097–T100. T096 (late ambiguity) is deferred by SD-1 |
| 2 | Requirements addressed | FR-GOV-08, FR-GOV-02, FR-GOV-05, FR-REL-06 (clarification rounds), FR-RPL-01..05, FR-POL-01, FR-CAP-04, FR-ORC-15, SC-002 (SCN-C), SC-009 (17 SCN-C evidence items) |
| 3 | ADRs followed | ADR-011 (content-addressed re-planning), ADR-008 (human gates, change control), ADR-018 (capability release with a decided parameter), ADR-017 (knowledge-driven agents) |
| 4 | Files created or changed | `governance/{ClarificationService, ClarificationDerivation, ChangeRequestService}`, `planning/{ReplanningService, InputFingerprinter}`, `RunCoordinator` (reuse path, approval carry-over, clarification round limit; re-opening delegated), `RequirementAnalysisAgent` (resolved clarifications), `StageNode.redependOn`, `ArtifactStore` (supersede, produced-by), `GovernanceController` (clarification and change-request endpoints), DTOs, `WorkflowService.requirementDocument` made public; shortener `DefaultExpiryCapability`, `LinkCreationService`; probes `DefaultExpiryProbes`, `ProbeContext` (release parameters), `LinkSnapshot` (`expiresAt`); catalog acceptance templates; `res/scenarios/scn-c-ambiguous.json`; docs `scn-c-ambiguous.md`, `orchestration.md` (§5), `links.md` |
| 5 | Tests written before implementation | T091, T093, T095, T097, T098 (red recorded) |
| 6 | Expected initial failures | no clarification or change endpoints; no fingerprinter; no default expiry |
| 7 | Validation commands executed | see `tdd-evidence.md` Phase 8; `mvnw -B -ntp verify` |
| 8 | Actual outcomes | 483 tests green; SCN-A, SCN-B, SCN-C, and drills green |
| 9 | Documentation updated | see row 4 |
| 10 | Traceability updated | tests tagged FR-GOV-08, FR-RPL-*, FR-CAP-04, SCN-C; tasks marked |
| 11 | Deviations from plan | (a) **Resolved clarifications.** A clarification counts as resolving when its question quotes the ambiguous phrase and the answer resolves it; only resolving answers are recorded in the requirement. This keeps within `requirement-document.schema.json`, which has no resolution field. (b) **Clarified requirement input.** The clarified requirement enters as a new `REQUIREMENT` artifact (stage `CLARIFICATION`), so ingestion is not repeated, as SCN-C requires. (c) **Approval carry-over.** A re-opened approval gate carries its approval over when its bound artifacts are unchanged; an interpretation of FR-GOV-05 for re-planning. (d) **Materiality rules** are criteria, constraints, or change type; wording is not material. (e) **Change gate limit.** After a rejected material change, the removed `CHANGE_APPROVAL` gate cannot be inserted again (`REMOVED` is terminal in the stage state model), so a further material change request on that run is refused with 409. (f) **SCN-C red stage.** The planned red stopped at `TESTING`, not `IMPLEMENTATION` (no schema or contract change to detect). (g) **Acceptance templates.** Acceptance criteria are derived from new catalog `acceptanceTemplates`. (h) **Knowledge version** hashes the catalog and lexicon files plus the policy-set version |
| 12 | New risks | reuse depends on complete declared inputs (guardrail: agents only see declared inputs through `StageContext`) |
| 13 | New assumptions | a new plan version is recorded on every clarification round, even when the stage structure is unchanged |
| 14 | Remaining failures | none |
| 15 | Commit boundary | Phase 8 |
| 16 | Commit message | `feat(orchestration): add clarification, content-addressed re-planning, change control, and default expiry` |
| 17 | Next task group | Phase 9 US7 — evidence and metrics (T101–T113 and T132, minus SD-1 deferrals) |
| 18 | Human approval required | deviations (a)–(h), especially (c) and (e), for the candidate's review; the SCN-C answers are simulated, and a live walkthrough by the candidate is documented |

**Pre-commit review**:
- **Scope.** Clarification, re-planning, and change control are control-plane features. The shortener
  change is limited to default expiry, which is capability-gated and applies at creation only.
- **Contracts.** No contract or schema file changes. The new endpoints were already in `openapi.yaml`
  and are validated by the contract harness in tests.
- **Tests.** All scenarios green.
