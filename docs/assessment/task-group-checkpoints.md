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
