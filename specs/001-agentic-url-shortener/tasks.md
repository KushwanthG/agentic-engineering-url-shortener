---

description: "Dependency-aware task plan for feature 001-agentic-url-shortener"
---

# Tasks: Agentic Software Engineering System — URL Shortener

**Input**: Design documents from `specs/001-agentic-url-shortener/` (spec, plan, research, data-model,
contracts, quickstart) and the 19 Proposed ADRs in `docs/adr/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

**Tests**: Required. The constitution (Principle IV) mandates red-green-refactor TDD, so test tasks
precede the implementation they drive.

**Organization**: Phases follow SpecKit rules: Setup → Foundational → one phase per user story in
priority order (US1, US2, US3 are P1; US4, US5, US6 are P2; US7 is P3) → Polish. User stories share
one codebase, so later stories build on earlier ones; each story phase ends with an independent test
checkpoint.

## Format and task metadata

`- [ ] T### [P?] [Story?] Description with exact target paths`, followed by one metadata line:

`Req` requirement ids · `Scn` scenario/drill ids · `ADR` · `Deps` prerequisite tasks · `Out` expected
artifact · `TDD` obligation · `Val` validation command or evidence · `Doc` documentation impact ·
`Trace` traceability update · `Risk` risk or guardrail · `Done` completion criteria · `Appr` human
approval requirement.

- **[P]** marks a task that can run in parallel: different files and no dependency on an incomplete
  task. Tasks without [P] are sequential. Synchronization points are the phase **Checkpoints**.
- **Path roots**: `main/` = `src/main/java/com/agentic/urlshortener/`; `test/` = `src/test/java/com/agentic/urlshortener/`;
  `res/` = `src/main/resources/`; `feature/` = `specs/001-agentic-url-shortener/`.
- **Validation commands**: run from the repository root with `JAVA_HOME` pointing at JDK 21
  (PowerShell: `.\mvnw.cmd`; bash: `./mvnw`). Written below as `mvnw test -Dtest=<Class>`.
- **TDD labels**: `Red→Green` = write the named tests first, run them, confirm they fail for the
  expected reason (record in `docs/assessment/tdd-evidence.md`), implement, run green, refactor, run
  the regression suite. `Verification` = a test written after its implementation to prove a
  property such as concurrency, contracts, or architecture; it is recorded as such, never as TDD.
  `N/A` = no executable behavior.
- **Default `Done`** (used when a task says `default`): validation command green; new tests carry
  `@Tag` for every listed `Req`/`Scn`; `Doc` updated; `tdd-evidence.md` updated for Red→Green
  tasks; changes committed at the task-group boundary after the task-group checkpoint and
  pre-commit review (guide §16–17) are recorded in `docs/assessment/task-group-checkpoints.md`.
- **Stable identifiers**: task ids never change once published. Tasks added during analysis
  remediation (T127–T132) are listed at their execution position; the order of lines, not the
  numeric order, is the execution order, and `Deps` fields are authoritative.
- **Default `Appr`**: `none`. Product-level approvals (gates inside runs) are behavior under test,
  not repository approvals. Repository approvals are the explicit `[GATE]` tasks, recorded as
  PENDING for the candidate; the assistant never checks them.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: timebox and scope control, build baseline, human gate checkpoints.

- [X] T001 Write the timebox and scope-control plan in `docs/assessment/timebox-and-scope.md`: must-have scope (P1 stories, orchestration core, governance, reliability, three scenarios, evidence), deferred backlog BL-01..BL-07, day-level milestones M0–M9 mapped to the task phases, critical path, checkpoint decisions CP1–CP3 with decision rules, stop conditions
  - Req: CON-01 · Scn: — · ADR: — · Deps: — · Out: scope document · TDD: N/A · Val: document reviewed in the pre-implementation review · Doc: new doc · Trace: referenced from the final summary §2 · Risk: scope creep displacing mandatory validation · Done: all six sections present · Appr: reviewed at G5 (T004)
- [X] T002 Create the Maven build in `pom.xml`: Spring Boot parent 4.1.1; `java.version` 21; starters `spring-boot-starter-webmvc`, `-data-jpa`, `-flyway`, `-security`, `-validation`, `-actuator`; `io.micrometer:micrometer-registry-prometheus` (runtime); `com.h2database:h2` (**runtime scope**, candidate directive); test starters `-webmvc-test`, `-data-jpa-test`, `-flyway-test`, `-security-test`, `-validation-test`, `-actuator-test`; `org.awaitility:awaitility`, `com.tngtech.archunit:archunit` 1.5.1, `com.atlassian.oai:openapi-request-validator-core` 3.0.0 (test); plugins `maven-enforcer-plugin` (Java ≥ 21, Maven ≥ 3.6.3), `jacoco-maven-plugin` 0.8.15 (report), `cyclonedx-maven-plugin` (Boot-managed), resources copying `feature/contracts/**` to `classpath:contracts/`
  - Req: CON-02, NFR-TST-02, NFR-SEC-05 · Scn: — · ADR: ADR-002, ADR-003, ADR-013, ADR-014 · Deps: — · Out: `pom.xml` · TDD: N/A · Val: `mvnw -v` reports Java 21; `mvnw verify` succeeds with zero tests · Doc: — · Trace: — · Risk: Boot 4.1 artifact names (verified against the Initializr POM in research) · Done: build green; SBOM generated at `target/classes/META-INF/sbom/` · Appr: default
- [X] T003 Add the Maven Wrapper pinned to Maven 3.9.x: `mvnw`, `mvnw.cmd`, `.mvn/wrapper/maven-wrapper.properties`
  - Req: CON-02, SC-001 · Scn: — · ADR: ADR-014 · Deps: T002 · Out: wrapper files · TDD: N/A · Val: `.\mvnw.cmd -v` prints Maven 3.9.x and Java 21 · Doc: quickstart §1 · Trace: — · Risk: first-run download needs network (documented) · Done: default · Appr: default
- [ ] T004 [GATE] Human checkpoint: the candidate ratifies G1 (constitution), G2 (spec), G3 (clarifications Q1–Q5), G4 (plan and ADR-001..ADR-019), and G5 (pre-implementation analysis and review) in `docs/governance/human-gate-register.md`
  - Req: Constitution III · Scn: — · ADR: all · Deps: T001, `/speckit-analyze` report, pre-implementation review · Out: gate decisions recorded by the candidate · TDD: N/A · Val: register shows decisions with name and date · Doc: gate register · Trace: gate ids cited in the final summary §7 · Risk: implementation proceeding before ratification. Guardrail: under Delegated Provisional Progression, tasks T005+ proceed provisionally, and any rejected ADR reopens every task citing it · Done: **only the candidate may check this task** · Appr: **REQUIRED — PENDING (candidate)**

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: platform and persistence infrastructure every story needs, plus the walking skeleton.

**CRITICAL**: no user story work begins until this phase's checkpoint passes.

- [X] T005 Create the application entry point `main/UrlShortenerApplication.java` and configuration `res/application.yml` (H2 **file** database `jdbc:h2:file:./data/agentic-sdlc;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE`, Hikari `connection-timeout: 2000`, `spring.jpa.hibernate.ddl-auto: validate`, `open-in-view: false`, Actuator exposure `health,info` public, `app.*` defaults from PVT-01..PVT-26), `res/application-demo.yml` (five labeled demo principals with SHA-256 token hashes, fault injection enabled), `res/application-json-logs.yml` (ECS structured console logs), and `src/test/resources/application.yml` (in-memory H2 per context)
  - Req: CON-02, FR-OPS-02 (PVT-24), FR-REL-11 · Scn: — · ADR: ADR-003, ADR-014, ADR-015 · Deps: T002 · Out: entry point and profiles · TDD: covered by T016 · Val: `mvnw test -Dtest=WalkingSkeletonIT` (T016) · Doc: quickstart §3 · Trace: — · Risk: demo credentials leaking into default profile. Guardrail: principals exist only in `application-demo.yml` · Done: default · Appr: default
- [X] T006 [P] Add `CLAUDE.md` (build and test commands with `JAVA_HOME`, SpecKit stage order, where the gate register lives) and `.gitattributes` (LF for `*.sh`, `mvnw`; CRLF for `*.cmd`)
  - Req: Constitution X · Scn: — · ADR: ADR-014 · Deps: T003 · Out: repository guidance files · TDD: N/A · Val: review · Doc: new files · Trace: — · Risk: stale guidance. Guardrail: updated in T116 · Done: default · Appr: default
- [X] T007 Write Flyway migrations `res/db/migration/V1__shortener_baseline.sql` and `res/db/migration/V2__orchestration_baseline.sql` exactly per `feature/data-model.md`, including `short_link.code VARCHAR(32) NOT NULL` with unique `uk_short_link_code`, `target_url VARCHAR(2048) NOT NULL`, `click_event.referrer_host VARCHAR(255) NULL`, `idempotency_record` unique (`consumer_id`, `idem_key`) with `idem_key VARCHAR(128)`, `stage_node` unique (`run_id`, `stage_key`), `audit_event` unique (`chain_id`, `seq`), and every `version BIGINT` column; test first in `test/persistence/MigrationTest.java` (tables and constraints exist; duplicate code insert fails)
  - Req: FR-ORC-08, NFR-CHG-01/02, FR-LNK-05 · Scn: — · ADR: ADR-003, ADR-006 · Deps: T005 · Out: V1, V2, `MigrationTest` · TDD: Red→Green · Val: `mvnw test -Dtest=MigrationTest` · Doc: data-model.md unchanged (source of truth) · Trace: tags FR-ORC-08 · Risk: schema drift from data-model.md. Guardrail: Hibernate `validate` fails on mismatch · Done: default · Appr: default
- [X] T008 [P] Provide time and JSON utilities: `main/common/config/ClockConfig.java` (UTC `Clock` bean), `main/common/util/CanonicalJson.java` and `main/common/util/Fingerprints.java` (SHA-256 over canonical JSON with sorted keys), and test support `test/support/MutableClock.java`; tests first in `test/common/util/CanonicalJsonTest.java` (key order and whitespace independence, stable hashes)
  - Req: FR-ORC-10, FR-GOV-05, FR-RPL-01 · Scn: — · ADR: ADR-011 · Deps: T005 · Out: utilities and tests · TDD: Red→Green · Val: `mvnw test -Dtest=CanonicalJsonTest` · Doc: — · Trace: tags FR-ORC-10 · Risk: unstable fingerprints break reuse and binding · Done: default · Appr: default
- [X] T009 Implement the error model: `main/common/exception/ErrorCode.java` (every code listed in `openapi.yaml#/components/schemas/Problem`), `main/common/exception/ApiException.java`, and `main/common/exception/ProblemDetailsHandler.java` (RFC 9457 body with `code`, `correlationId`, optional `errors[]`, `retryAfterSeconds`; no stack traces or SQL); tests first in `test/common/exception/ProblemDetailsHandlerTest.java`
  - Req: FR-OPS-03 · Scn: — · ADR: ADR-013 · Deps: T005 · Out: error model · TDD: Red→Green · Val: `mvnw test -Dtest=ProblemDetailsHandlerTest` · Doc: — · Trace: tags FR-OPS-03 · Risk: leaking internals. Guardrail: generic 500 body test · Done: default · Appr: default
- [X] T010 [P] Implement `main/common/web/CorrelationIdFilter.java` (accept `^[A-Za-z0-9-]{8,64}$`, else generate a UUID; MDC `correlationId`; response header `X-Correlation-Id`); tests first in `test/common/web/CorrelationIdFilterTest.java`
  - Req: FR-OPS-04, FR-AUD-05 · Scn: — · ADR: ADR-012 · Deps: T005 · Out: filter · TDD: Red→Green · Val: `mvnw test -Dtest=CorrelationIdFilterTest` · Doc: — · Trace: tags FR-OPS-04 · Risk: log injection through the header. Guardrail: strict pattern · Done: default · Appr: default
- [X] T011 Implement security in `main/common/security/`: `Role.java` (`API_CONSUMER`, `REQUESTER`, `APPROVER`, `RELEASE_OWNER`, `AUDITOR`), `ApiPrincipal.java`, `PrincipalRegistry.java` (configuration with token SHA-256 hashes, constant-time comparison), `BearerTokenAuthenticationFilter.java`, `SecurityConfig.java` (stateless, CSRF off, URL rules per ADR-015, `GET /{code}` and `health`/`info` public), and `ProblemAuthenticationHandlers.java` (401/403 as problem details); tests first in `test/common/security/SecurityMatrixTest.java` (public vs. protected skeleton routes; 401 body shape) and `test/common/security/TokenNotLoggedTest.java`
  - Req: FR-LNK-13, NFR-SEC-02, NFR-SEC-03, FR-GOV-03, NFR-SCA-01 (stateless sessions) · Scn: — · ADR: ADR-015 · Deps: T009, T010 · Out: security layer · TDD: Red→Green · Val: `mvnw test -Dtest=SecurityMatrixTest,TokenNotLoggedTest` · Doc: SECURITY.md (T116) · Trace: tags NFR-SEC-02 · Risk: permissive defaults. Guardrail: deny-by-default rule last · Done: default · Appr: default
- [X] T012 Implement the hash-chained audit trail in `main/orchestration/audit/`: `AuditEvent.java` (entity), `AuditEventRepository.java` (insert and read only), `AuditService.java` (per-chain `seq`, `hash = SHA-256(prev_hash | fields)`, genesis 64 zeros, joins the caller's transaction), `AuditVerifier.java` (first broken `seq`); tests first in `test/orchestration/audit/AuditChainTest.java` and `test/orchestration/audit/AuditTamperDetectionTest.java` (modify, delete, insert, reorder detected)
  - Req: FR-AUD-01, FR-AUD-02, NFR-AUD-02, SC-006 · Scn: — · ADR: ADR-012 · Deps: T007, T008 · Out: audit module · TDD: Red→Green · Val: `mvnw test -Dtest=AuditChainTest,AuditTamperDetectionTest` · Doc: docs/architecture/overview.md (T117) · Trace: tags FR-AUD-02, SC-006 · Risk: concurrent appends on one chain. Guardrail: unique (`chain_id`, `seq`) plus retry · Done: default · Appr: default
- [X] T013 [P] Write architecture rules in `test/architecture/ArchitectureTest.java` (ArchUnit core, plain `@Test`): `..shortener..` must not depend on `..orchestration..`; only `..orchestration.integration..` may depend on `..shortener..`; `..shortener.domain..` must not depend on web or servlet APIs; no cycles between `platform`, `shortener`, `orchestration` slices
  - Req: NFR-MNT-01, Constitution VII · Scn: — · ADR: ADR-001 · Deps: T005 · Out: architecture tests · TDD: Verification (rules precede the code they constrain) · Val: `mvnw test -Dtest=ArchitectureTest` · Doc: — · Trace: tags NFR-MNT-01 · Risk: vacuous rules on empty packages. Guardrail: `allowEmptyShould(false)` activated once packages exist (T029) · Done: default · Appr: default
- [X] T014 Spike the contract harness in `test/contract/OpenApiContract.java` (load `classpath:contracts/openapi.yaml` with openapi-request-validator-core 3.0.0; validate a recorded MockMvc response) and `test/contract/ContractHarnessSpikeTest.java` (the `401` problem response of `POST /api/v1/links` validates); if the 3.0.0 API is unusable, implement the fallback validator per research R-19 and record the decision in `research.md`
  - Req: NFR-CHG-01 · Scn: — · ADR: ADR-013 · Deps: T011 · Out: contract harness · TDD: Red→Green · Val: `mvnw test -Dtest=ContractHarnessSpikeTest` · Doc: research.md R-19 outcome · Trace: — · Risk: validator API unknown. Guardrail: time-boxed spike with a fallback · Done: default · Appr: default
- [X] T015 [P] Add test support utility `test/support/EvidenceExporter.java` (`ScriptedAgent` — succeed, fail transiently N times, fail permanently, sleep, request clarification, report policy block — moved to T040 during implementation because it implements the agent SPI created in T039) (writes JSON and Markdown evidence to `target/evidence/<scenario>/`, each file headed by provenance: git commit, test command, timestamp, JDK, active profile, simulated-input flags — review RC-4)
  - Req: NFR-TST-02, SC-009 · Scn: — · ADR: ADR-013 · Deps: T005 · Out: test support · TDD: N/A (test infrastructure) · Val: used by later tests · Doc: — · Trace: — · Risk: support code hiding real behavior. Guardrail: scripted agents used only in engine tests · Done: compiles · Appr: default
- [X] T016 Write the walking skeleton test `test/WalkingSkeletonIT.java` (context starts, Flyway applied, `GET /actuator/health` = UP without credentials, `/actuator/prometheus` = 401 without credentials, protected API = 401 problem)
  - Req: FR-OPS-01, CON-02 · Scn: — · ADR: ADR-014 · Deps: T005–T014 · Out: skeleton test · TDD: Red→Green · Val: `mvnw verify` · Doc: — · Trace: tags FR-OPS-01 · Risk: — · Done: default · Appr: default

**Checkpoint (sync)**: `mvnw verify` green → foundation ready.

---

## Phase 3: User Story 1 — Shorten, share, and track a link (Priority: P1) 🎯 MVP

**Goal**: production-grade URL shortener (US1, FR-LNK/RED/ANL/OPS).

**Independent Test**: `mvnw test -Dtest="*Link*Test,*Redirect*Test,UrlPolicyTest"`, then the quickstart
§4 commands against a running application.

- [X] T017 [P] [US1] Write failing `test/shortener/domain/UrlPolicyTest.java`: the security URL catalog of ≥ 25 cases (schemes `javascript`, `data`, `file`, `ftp`, `mailto`, `vbscript`; user-info; missing host; length 2,049; `localhost`, `a.localhost`, `x.local`, `svc.internal`; `127.0.0.1`, `127.1`, `2130706433`, `0x7f000001`, `0177.0.0.1`, `10.0.0.1`, `172.16.0.1`, `192.168.1.1`, `169.254.169.254`, `100.64.0.1`, `0.0.0.0`, `[::1]`, `[::ffff:127.0.0.1]`, `[fe80::1]`, `[fc00::1]`, `224.0.0.1`, the configured own host) plus normalization cases (upper-case scheme and host, IDN host, default ports 80/443 removed, path/query/fragment preserved, exactly 2,048 characters accepted)
  - Req: FR-LNK-02, FR-LNK-03, FR-LNK-04, NFR-SEC-01 · Scn: — · ADR: — (research R-07) · Deps: T016 · Out: test class · TDD: Red · Val: `mvnw test -Dtest=UrlPolicyTest` fails (class missing or assertions) · Doc: — · Trace: tags FR-LNK-02/03/04, NFR-SEC-01 · Risk: catalog incomplete. Guardrail: PVT-15 count asserted · Done: red recorded · Appr: default
- [X] T018 [US1] Implement `main/shortener/domain/UrlPolicy.java`, `NormalizedUrl.java`, `Ipv4LiteralParser.java` (inet_aton forms, 1–4 parts, decimal/octal/hex), `UrlRejection.java` (maps to `URL_INVALID`, `URL_SCHEME_NOT_ALLOWED`, `URL_CREDENTIALS_NOT_ALLOWED`, `URL_HOST_MISSING`, `URL_HOST_NOT_ALLOWED`, `URL_TOO_LONG`) without DNS resolution
  - Req: FR-LNK-02..04 · Scn: — · ADR: — · Deps: T017 · Out: URL policy · TDD: Green · Val: `mvnw test -Dtest=UrlPolicyTest` passes · Doc: docs/api/links.md (T030) · Trace: — · Risk: parser differentials. Guardrail: rejects anything ambiguous · Done: default · Appr: default
- [X] T019 [P] [US1] Test first in `test/shortener/domain/ShortCodeGeneratorTest.java` (length 7, alphabet `[0-9A-Za-z]`, `SecureRandom` source, no sequential pattern in 10,000 samples), then implement `main/shortener/domain/ShortCodeGenerator.java` and `SecureRandomShortCodeGenerator.java`
  - Req: FR-LNK-06, NFR-SCA-02 · Scn: — · ADR: ADR-004 · Deps: T016 · Out: generator · TDD: Red→Green · Val: `mvnw test -Dtest=ShortCodeGeneratorTest` · Doc: — · Trace: tags FR-LNK-06 · Risk: weak randomness · Done: default · Appr: default
- [X] T020 [US1] Test first in `test/shortener/repository/ShortLinkRepositoryTest.java` (unique code constraint, atomic `incrementClicks`, daily counts per UTC day for 30 days, synthetic-row deletion by run id), then implement entities `main/shortener/domain/ShortLink.java`, `ClickEvent.java`, `IdempotencyRecord.java` and repositories `main/shortener/repository/ShortLinkRepository.java`, `ClickEventRepository.java`, `IdempotencyRecordRepository.java`
  - Req: FR-LNK-05, FR-ANL-01, FR-ANL-03, FR-ANL-05 · Scn: — · ADR: ADR-003, ADR-016 · Deps: T007, T019 · Out: persistence layer · TDD: Red→Green · Val: `mvnw test -Dtest=ShortLinkRepositoryTest` · Doc: — · Trace: tags listed Req · Risk: lost updates. Guardrail: SQL-level atomic update only · Done: default · Appr: default
- [X] T021 [US1] Write failing `test/shortener/service/LinkCreationServiceTest.java`: valid creation (normalized target, creator recorded, status ACTIVE); expiry in the past, at now, or beyond 5 years rejected `INVALID_EXPIRY`; forced collisions retried up to 5 then `CODE_GENERATION_EXHAUSTED` with no partial link; same target twice gives two links; `alias` or `maxClicks` while unreleased gives `CAPABILITY_NOT_AVAILABLE`
  - Req: FR-LNK-01, FR-LNK-05, FR-LNK-07, FR-LNK-12, FR-CAP-01 · Scn: — · ADR: ADR-004, ADR-018 · Deps: T018, T020 · Out: test class · TDD: Red · Val: test run fails as expected · Doc: — · Trace: tags listed Req · Risk: — · Done: red recorded · Appr: default
- [X] T022 [US1] Implement `main/shortener/service/LinkCreationService.java` (retry loop outside the transaction), `main/shortener/service/LinkWriter.java` (transactional insert), `main/shortener/service/CapabilityService.java` (read side: released flag and parameters), `main/shortener/service/CapabilityProvider.java` and `CapabilityRegistry.java` (creates unreleased `capability_release` rows at startup), `main/shortener/ShortenerProperties.java`
  - Req: FR-LNK-01, FR-LNK-05, FR-LNK-07, FR-LNK-12, FR-CAP-01 · Scn: — · ADR: ADR-004, ADR-018 · Deps: T021 · Out: creation service · TDD: Green · Val: `mvnw test -Dtest=LinkCreationServiceTest` · Doc: — · Trace: — · Risk: — · Done: default · Appr: default
- [X] T023 [US1] Test first in `test/shortener/service/IdempotencyServiceTest.java` (identical request with the same key replays; different payload gives `IDEMPOTENCY_KEY_REUSED`; key not matching `^[A-Za-z0-9_-]{1,128}$` gives `INVALID_IDEMPOTENCY_KEY`; expired record replaced; 10 concurrent identical requests produce exactly one link and identical responses), then implement `main/shortener/service/IdempotencyService.java` (link and record in one transaction; unique-violation replay)
  - Req: FR-LNK-08, FR-LNK-09 · Scn: — · ADR: — (research R-09) · Deps: T022 · Out: idempotency · TDD: Red→Green · Val: `mvnw test -Dtest=IdempotencyServiceTest` · Doc: docs/api/links.md · Trace: tags FR-LNK-08/09 · Risk: H2 lock behavior under concurrency · Done: default · Appr: default
- [X] T024 [US1] Write failing `test/shortener/service/RedirectServiceTest.java`: active → REDIRECT with target; unknown or malformed → NOT_FOUND; expired (including `expires_at == now`) → EXPIRED with no click increment; analytics write failure on a link without a click rule → REDIRECT plus `shortener.analytics.failures` increment
  - Req: FR-RED-01, FR-RED-03, FR-RED-04, FR-ANL-01, FR-ANL-02, FR-ANL-04, NFR-REL-01 · Scn: — · ADR: ADR-016 · Deps: T020 · Out: test class · TDD: Red · Val: fails as expected · Doc: — · Trace: tags listed Req · Risk: — · Done: red recorded · Appr: default
- [X] T025 [US1] Implement `main/shortener/service/RedirectService.java` and `main/shortener/service/ClickRecorder.java` (`REQUIRES_NEW`; atomic increment plus click event with referrer host only; fail-open with metric)
  - Req: FR-RED-01, FR-RED-03, FR-RED-04, FR-ANL-01, FR-ANL-02, FR-ANL-04, NFR-REL-01 · Scn: — · ADR: ADR-016 · Deps: T024 · Out: redirect service · TDD: Green · Val: `mvnw test -Dtest=RedirectServiceTest` · Doc: — · Trace: — · Risk: storing personal data. Guardrail: host-only extraction test · Done: default · Appr: default
- [X] T026 [P] [US1] Test first in `test/shortener/service/TokenBucketRateLimiterTest.java` (capacity, refill with `MutableClock`, per-key isolation, bounded map eviction), then implement `main/shortener/service/TokenBucketRateLimiter.java` and `RateLimitProperties.java` (creation 30/min per API consumer; not-found 60/min per client address)
  - Req: FR-LNK-10, FR-RED-05, NFR-SEC-04 · Scn: — · ADR: — (research R-11) · Deps: T016 · Out: rate limiter · TDD: Red→Green · Val: `mvnw test -Dtest=TokenBucketRateLimiterTest` · Doc: — · Trace: tags FR-LNK-10, FR-RED-05 · Risk: memory growth. Guardrail: bounded map · Done: default · Appr: default
- [X] T027 [US1] Write failing `test/shortener/controller/LinkControllerTest.java` (MockMvc): 201 with `Location` and body; 400 for each URL code; 401 without token; 403 with a non-consumer role; 409 `IDEMPOTENCY_KEY_REUSED`; 422 `CAPABILITY_NOT_AVAILABLE`; 429 with `Retry-After`; replay header `Idempotency-Replayed: true`; `GET /api/v1/links/{code}` and `/stats` (3 resolutions → `totalClicks` 3)
  - Req: FR-LNK-01, FR-LNK-10, FR-LNK-11, FR-LNK-13, FR-ANL-03 · Scn: — · ADR: ADR-015 · Deps: T022, T023, T025, T026 · Out: test class · TDD: Red · Val: fails as expected · Doc: — · Trace: tags listed Req · Risk: — · Done: red recorded · Appr: default
- [X] T028 [US1] Implement `main/shortener/controller/LinkController.java`, DTO records `main/shortener/dto/{CreateLinkRequest, LinkResponse, LinkStatsResponse}.java` (`CreateLinkRequest` fields `url`, `expiresAt`, `alias`, `maxClicks`), and `main/shortener/service/LinkQueryService.java`
  - Req: FR-LNK-01, FR-LNK-10, FR-LNK-11, FR-LNK-13, FR-ANL-03 · Scn: — · ADR: ADR-015 · Deps: T027 · Out: links API · TDD: Green · Val: `mvnw test -Dtest=LinkControllerTest` · Doc: docs/api/links.md · Trace: — · Risk: — · Done: default · Appr: default
- [X] T029 [US1] Test first in `test/shortener/controller/RedirectControllerTest.java` (302 with `Location` and `Cache-Control: no-store`; 404; 410; 429 after 60 not-found outcomes from one address; no credentials needed), then implement `main/shortener/controller/RedirectController.java` (`GET /{code:[A-Za-z0-9_-]{3,32}}`); enable `allowEmptyShould(false)` in `ArchitectureTest`
  - Req: FR-RED-01..05 · Scn: — · ADR: ADR-016 · Deps: T025, T026 · Out: redirect endpoint · TDD: Red→Green · Val: `mvnw test -Dtest=RedirectControllerTest,ArchitectureTest` · Doc: docs/api/links.md · Trace: tags FR-RED-* · Risk: route collision with `/api`, `/actuator`. Guardrail: pattern plus reserved aliases · Done: default · Appr: default
- [X] T030 [US1] Test first in `test/shortener/controller/StoreUnavailableTest.java` (data-access resource failure → 503 `STORE_UNAVAILABLE` within 2 s; readiness DOWN), then implement `main/shortener/config/LinkStoreHealthIndicator.java` and the 503 mapping; write `docs/api/links.md` (endpoints, errors, examples, rate limits)
  - Req: FR-OPS-01, FR-OPS-02 · Scn: — · ADR: ADR-003 · Deps: T028, T029 · Out: health indicator, API doc · TDD: Red→Green · Val: `mvnw test -Dtest=StoreUnavailableTest` · Doc: docs/api/links.md · Trace: tags FR-OPS-01/02 · Risk: — · Done: default · Appr: default
- [X] T031 [US1] Add verification tests `test/shortener/RedirectConcurrencyTest.java` (200 concurrent resolutions → click count equals successful redirects) and `test/security/UrlSecurityCatalogIT.java` (the catalog through the HTTP API, all rejected)
  - Req: FR-ANL-05, SC-005, NFR-SEC-01 · Scn: — · ADR: ADR-016 · Deps: T030 · Out: verification tests · TDD: Verification · Val: `mvnw test -Dtest=RedirectConcurrencyTest,UrlSecurityCatalogIT` · Doc: — · Trace: tags SC-005, NFR-SEC-01 · Risk: flaky concurrency. Guardrail: latch start and fixed pool · Done: default · Appr: default
- [X] T032 [US1] Contract tests `test/contract/LinkApiContractTest.java`: every links and redirect response (201, 302 headers, 400, 401, 403, 404, 409, 410, 422, 429, 503) validated against `openapi.yaml` 1.2.0
  - Req: FR-LNK-*, FR-RED-*, NFR-CHG-01 · Scn: — · ADR: ADR-013 · Deps: T014, T030 · Out: contract tests · TDD: Verification · Val: `mvnw test -Dtest=LinkApiContractTest` · Doc: — · Trace: tags NFR-CHG-01 · Risk: contract drift · Done: default · Appr: default
- [X] T033 [US1] Checkpoint CP1: record the scope decision in `docs/assessment/timebox-and-scope.md` (US1 complete? elapsed time vs. plan; keep or cut per the CP1 rule)
  - Req: CON-01 · Scn: — · ADR: — · Deps: T031, T032 · Out: checkpoint record · TDD: N/A · Val: record present · Doc: scope doc · Trace: — · Risk: — · Done: decision written · Appr: default

**Checkpoint (sync)**: US1 independently functional (MVP).

---

## Phase 4: User Story 2 — Deliver a well-defined requirement through the governed lifecycle (Priority: P1) — SCN-A

**Goal**: the orchestration engine, knowledge, agents, capability release, policy evaluation, and the
greenfield custom-alias capability, proven end-to-end by SCN-A.

**Independent Test**: `mvnw test -Dtest=ScenarioAGreenfieldE2ETest` → run `COMPLETED`, readiness
`READY`, clarification skipped with rationale, parallel overlap in the timeline, alias usable.

### Orchestration domain, dependency graph, and state machine

- [X] T034 [US2] Write failing `test/orchestration/engine/StageTransitionsTest.java` and `RunTransitionsTest.java` (every allowed transition in data-model.md passes; the prohibited examples throw `IllegalTransitionException`)
  - Req: FR-ORC-11 · Scn: — · ADR: ADR-005 · Deps: T016 · Out: tests · TDD: Red · Val: fails as expected · Doc: — · Trace: tags FR-ORC-11 · Risk: — · Done: red recorded · Appr: default
- [X] T035 [US2] Implement enums `main/orchestration/domain/{RunStatus, StageStatus, StageType, AwaitingType, FailureClass, ActorType, DecisionType}.java` and tables `main/orchestration/engine/{StageTransitions, RunTransitions, IllegalTransitionException}.java`
  - Req: FR-ORC-11, FR-ORC-03 · Scn: — · ADR: ADR-005 · Deps: T034 · Out: state machines · TDD: Green · Val: `mvnw test -Dtest=StageTransitionsTest,RunTransitionsTest` · Doc: docs/architecture/orchestration.md (T058) · Trace: — · Risk: — · Done: default · Appr: default
- [X] T036 [US2] Test first in `test/orchestration/repository/OrchestrationPersistenceTest.java` (round trip of every entity; optimistic-lock conflict on `StageNode`), then implement entities in `main/orchestration/domain/` and their repositories in `main/orchestration/repository/`: `WorkflowRun`, `RequirementVersion`, `PlanVersion`, `StageNode`, `StageAttempt`, `Artifact`, `Decision`, `ChangeRequest`, `PolicyEvaluation`, `PolicyExceptionRecord`, `FailureEvent`
  - Req: FR-ORC-08, FR-ORC-10 · Scn: — · ADR: ADR-006 · Deps: T007, T035 · Out: persistence model · TDD: Red→Green · Val: `mvnw test -Dtest=OrchestrationPersistenceTest` · Doc: — · Trace: tags FR-ORC-08 · Risk: — · Done: default · Appr: default
- [X] T037 [US2] Write failing `test/orchestration/planning/PlanFactoryTest.java` and `test/orchestration/engine/PlanValidatorTest.java` (NEW_CAPABILITY template stages and edges per plan §3; CHANGE_TO_EXISTING adds `IMPACT_ANALYSIS` and `REGRESSION_TESTING`; validator rejects cycles, unknown dependencies, multiple roots, `RELEASE` without `RELEASE_APPROVAL`, side-effecting stage not downstream of a gate)
  - Req: FR-ORC-02, FR-ORC-03, FR-ORC-06 · Scn: SCN-A, SCN-B · ADR: ADR-007 · Deps: T035 · Out: tests · TDD: Red · Val: fails as expected · Doc: — · Trace: tags listed Req · Risk: — · Done: red recorded · Appr: default
- [X] T038 [US2] Implement `main/orchestration/planning/{PlanFactory, PlanGraph, StageSpec, PlanDiff}.java` and `main/orchestration/engine/PlanValidator.java` (Kahn's algorithm plus invariants)
  - Req: FR-ORC-02, FR-ORC-03, FR-ORC-06 · Scn: SCN-A, SCN-B · ADR: ADR-007 · Deps: T037 · Out: planning · TDD: Green · Val: `mvnw test -Dtest=PlanFactoryTest,PlanValidatorTest` · Doc: — · Trace: — · Risk: — · Done: default · Appr: default
- [X] T039 [US2] Test first in `test/orchestration/agent/AgentPermissionTest.java` (an undeclared port call throws `AgentPermissionDeniedException` and is audited), then implement the SPI `main/orchestration/agent/{StageAgent, StageContext, StageResult (sealed: Succeeded, Failed, NeedsClarification, PolicyBlocked), AgentPermission, AgentRegistry}.java` and `main/orchestration/port/{ApplicationPlanePort, PermissionScopedPort}.java`
  - Req: FR-ORC-12, NFR-AUT-01 · Scn: — · ADR: ADR-017 · Deps: T035 · Out: agent SPI and port · TDD: Red→Green · Val: `mvnw test -Dtest=AgentPermissionTest` · Doc: — · Trace: tags FR-ORC-12, NFR-AUT-01 · Risk: permission bypass. Guardrail: agents receive only the proxy · Done: default · Appr: default
- [X] T040 [US2] Add `test/support/ScriptedAgent.java` (moved from T015) and write failing `test/orchestration/engine/RunCoordinatorTest.java` with `ScriptedAgent`s: fan-out stages run concurrently (three branches sleeping 300 ms each finish in well under 900 ms of elapsed time, and their dispatch audit events share one scheduling cycle — review RC-2); the join starts only after all predecessors succeed; the join never starts after a predecessor fails; conditional stages become `SKIPPED` with a reason; artifacts persisted with input refs and fingerprints; state persisted before dispatch; run reaches `COMPLETED`
  - Req: FR-ORC-04, FR-ORC-05, FR-ORC-06, FR-ORC-07, FR-ORC-08, FR-ORC-10, FR-AUD-06 · Scn: — · ADR: ADR-005, ADR-006 · Deps: T036, T038, T039 · Out: engine tests · TDD: Red · Val: fails as expected · Doc: — · Trace: tags listed Req · Risk: — · Done: red recorded · Appr: default
- [X] T041 [US2] Implement `main/orchestration/engine/{RunCoordinator, StageDispatcher (bounded pool of 8), RunLocks, EntryExitCriteria, ArtifactStore}.java` and `main/orchestration/service/WorkflowService.java` (create run: requirement v1, plan v1 validated, policy-set version pinned, start); every transition writes an audit event, and dispatch events carry the scheduling-cycle number of the `advance` call that started them
  - Req: FR-ORC-01, FR-ORC-04, FR-ORC-05, FR-ORC-07, FR-ORC-08, FR-ORC-10, FR-AUD-01, FR-AUD-06 · Scn: — · ADR: ADR-005, ADR-006 · Deps: T040, T012 · Out: engine core · TDD: Green · Val: `mvnw test -Dtest=RunCoordinatorTest` · Doc: docs/architecture/orchestration.md · Trace: — · Risk: races. Guardrail: stale-result discard by (generation, attempt) · Done: default · Appr: default

### Approval gates (basic) and capability release

- [X] T042 [US2] Test first in `test/orchestration/governance/GateDecisionBasicsTest.java` (gate opens `AWAITING_DECISION` with a pending action and review bundle; `APPROVER` approves `ARCHITECTURE_APPROVAL` → dependents start; `RELEASE_OWNER` decides `RELEASE_APPROVAL`; wrong role → 403; the engine cannot mark a gate `SUCCEEDED` without a valid decision), then implement `main/orchestration/governance/GateService.java` and `main/orchestration/controller/GovernanceController.java` (`POST /api/v1/workflows/{runId}/gates/{stageKey}/decision`)
  - Req: FR-GOV-01, FR-GOV-03, FR-GOV-09, FR-RDY-04 · Scn: SCN-A · ADR: ADR-008 · Deps: T041, T011 · Out: basic gates · TDD: Red→Green · Val: `mvnw test -Dtest=GateDecisionBasicsTest` · Doc: docs/architecture/governance.md (T066) · Trace: tags listed Req · Risk: approval bypass. Guardrail: transition guard test · Done: default · Appr: default
- [X] T043 [US2] Test first in `test/orchestration/integration/ApplicationPlaneAdapterTest.java` and `test/shortener/service/PreviewIsolationTest.java` (preview enables a capability only inside the scoped in-process call; concurrent consumer requests and HTTP cannot enable it; release is idempotent and audited on the `GLOBAL` chain), then implement `main/orchestration/integration/InProcessApplicationPlaneAdapter.java` and the preview and release-write parts of `main/shortener/service/CapabilityService.java`
  - Req: FR-CAP-01, FR-ORC-16, FR-RDY-03 · Scn: SCN-A · ADR: ADR-018, ADR-001 · Deps: T041, T022 · Out: port adapter · TDD: Red→Green · Val: `mvnw test -Dtest=ApplicationPlaneAdapterTest,PreviewIsolationTest,ArchitectureTest` · Doc: — · Trace: tags FR-CAP-01 · Risk: preview leakage. Guardrail: thread-scoped override cleared in `finally` · Done: default · Appr: default

### Knowledge, agents, and policy evaluation

- [X] T044 [P] [US2] Author `res/orchestration/capability-catalog.yaml` (baseline `link-creation`, `redirect`, `expiration`, `analytics`; scenario `custom-alias`, `click-limit`, `default-expiry`, each with keywords, components, API and schema deltas, release flag, probes, threats, doc anchors) and `main/orchestration/knowledge/CapabilityCatalog.java`; test first in `test/orchestration/knowledge/CapabilityCatalogTest.java`
  - Req: FR-ORC-13, FR-ORC-14 · Scn: SCN-A, SCN-B, SCN-C · ADR: ADR-017 · Deps: T041 · Out: catalog · TDD: Red→Green · Val: `mvnw test -Dtest=CapabilityCatalogTest` · Doc: — · Trace: tags FR-ORC-13 · Risk: catalog drift from code. Guardrail: implementation checks (T048) · Done: default · Appr: default
- [X] T045 [P] [US2] Author `res/orchestration/ambiguity-lexicon.yaml` (vague terms, undefined-concept patterns, conflict pairs, question templates with options and impacts) and `main/orchestration/knowledge/AmbiguityLexicon.java`; test first in `test/orchestration/knowledge/AmbiguityLexiconTest.java`
  - Req: FR-ORC-13, FR-GOV-08 · Scn: SCN-A, SCN-C · ADR: ADR-017 · Deps: T041 · Out: lexicon · TDD: Red→Green · Val: `mvnw test -Dtest=AmbiguityLexiconTest` · Doc: — · Trace: tags FR-ORC-13 · Risk: false positives blocking well-defined input. Guardrail: SCN-A input asserted clean · Done: default · Appr: default
- [X] T046 [US2] Write failing `test/orchestration/agent/RequirementAnalysisAgentTest.java`: GF-001 → `NEW_CAPABILITY`, capability `custom-alias`, all 5 quality checks PASS, 0 blocking ambiguities, `clarificationRequired = false` with the rationale; AMB-001 → the six expected ambiguity types with severities; an unknown capability → `ARCHITECTURE_BOUNDARY` FAIL; outputs validate against `normalized-requirement.schema.json`
  - Req: FR-ORC-01, FR-ORC-13 · Scn: SCN-A, SCN-C · ADR: ADR-017 · Deps: T044, T045 · Out: tests · TDD: Red · Val: fails as expected · Doc: — · Trace: tags FR-ORC-13, SCN-A · Risk: — · Done: red recorded · Appr: default
- [X] T047 [US2] Implement `main/orchestration/agent/{RequirementIngestionAgent, RequirementAnalysisAgent, AcceptanceCriterionParser, QualityChecks}.java`
  - Req: FR-ORC-01, FR-ORC-13 · Scn: SCN-A, SCN-C · ADR: ADR-017 · Deps: T046 · Out: analysis agents · TDD: Green · Val: `mvnw test -Dtest=RequirementAnalysisAgentTest` · Doc: — · Trace: — · Risk: — · Done: default · Appr: default
- [X] T048 [US2] Test first in `test/orchestration/agent/DecompositionAgentTest.java` and `ThreatAssessmentAgentTest.java`, then implement `main/orchestration/agent/DecompositionAgent.java` (every AC mapped to ≥ 1 task; acyclic task graph; output validates against `task-graph.schema.json`) and `ThreatAssessmentAgent.java` (STRIDE threats; every HIGH threat names ≥ 1 verification probe)
  - Req: FR-ORC-03, NFR-SEC-01 · Scn: SCN-A · ADR: ADR-017 · Deps: T047 · Out: two agents · TDD: Red→Green · Val: `mvnw test -Dtest=DecompositionAgentTest,ThreatAssessmentAgentTest` · Doc: — · Trace: tags FR-ORC-03 · Risk: unmapped acceptance criteria. Guardrail: exit criterion "every AC mapped" · Done: default · Appr: default
- [X] T129 [US2] Test first in `test/orchestration/agent/DesignAgentTest.java`, then implement `main/orchestration/agent/DesignAgent.java` (components, API and schema deltas from the catalog, release plan with parameters, rollback plan, `materialChange` with reasons; output validates against `design.schema.json`)
  - Req: FR-ORC-03, FR-ORC-06, FR-RPL-03 · Scn: SCN-A · ADR: ADR-017, ADR-018 · Deps: T048 · Out: design agent · TDD: Red→Green · Val: `mvnw test -Dtest=DesignAgentTest` · Doc: — · Trace: tags FR-ORC-06 · Risk: materiality misjudged. Guardrail: API, schema, or security-control change always material · Done: default · Appr: default
- [X] T130 [US2] Test first in `test/orchestration/agent/ImplementationAgentTest.java`, then implement `main/orchestration/agent/ImplementationAgent.java` (change set per task; delivery checks through the port: provider registered, migration applied in the Flyway history, contract declares the fields; an absent capability is a PERMANENT failure)
  - Req: FR-ORC-15 · Scn: SCN-A · ADR: ADR-017, ADR-018 · Deps: T129, T043 · Out: implementation agent · TDD: Red→Green · Val: `mvnw test -Dtest=ImplementationAgentTest` · Doc: — · Trace: tags FR-ORC-15 · Risk: fabricated delivery. Guardrail: live checks only · Done: default · Appr: default
- [X] T049 [US2] Test first, then implement probes and verification agents: `main/orchestration/agent/probes/{AcceptanceProbe, ProbeContext, ProbeRegistry}.java`, custom-alias probes `CustomAliasProbes.java` (CA-P1..CA-P6 mapped to GF-001 AC-1..AC-6 by declared patterns), security probes `SecurityProbes.java`, `TestingAgent.java`, `SecurityVerificationAgent.java` (preview mode; synthetic data removed in `finally`; an AC with no passing probe fails the stage) with tests `TestingAgentTest`, `SecurityVerificationAgentTest`
  - Req: FR-ORC-16, FR-REL-05, NFR-SEC-01 · Scn: SCN-A · ADR: ADR-017, ADR-018 · Deps: T130 · Out: verification stages · TDD: Red→Green · Val: `mvnw test -Dtest=TestingAgentTest,SecurityVerificationAgentTest` · Doc: — · Trace: tags FR-ORC-16 · Risk: leftover synthetic data. Guardrail: finalizer check (T071) · Done: default · Appr: default
- [X] T050 [P] [US2] Test first, then implement `main/orchestration/agent/DocumentationAgent.java` (API changelog, capability and runbook text; repository docs anchor check with `codebase-root`) and `ValidationAgent.java` (join consolidation, per-AC verification, degraded stages); tests `DocumentationAgentTest`, `ValidationAgentTest`
  - Req: FR-ORC-03, FR-ORC-05 · Scn: SCN-A · ADR: ADR-017 · Deps: T129 · Out: two agents · TDD: Red→Green · Val: `mvnw test -Dtest=DocumentationAgentTest,ValidationAgentTest` · Doc: — · Trace: tags FR-ORC-05 · Risk: — · Done: default · Appr: default
- [X] T051 [US2] Write failing policy tests `test/orchestration/policy/PolicyEngineTest.java` and per-rule tests (SEC-001, SEC-002, SEC-003, PRV-001, AUD-001, AUD-002, LIC-001, TST-001, TST-002, DOC-001, CHG-001, CHG-002, REL-001, AUT-001 → PASS, FAIL, NOT_APPLICABLE with evidence; the run's pinned `policy_set_version` recorded on every evaluation; mandatory FAIL → `PolicyBlocked`; advisory FAIL non-blocking)
  - Req: FR-POL-01, FR-POL-02, FR-POL-03, FR-POL-05, FR-POL-06, NFR-SEC-05, NFR-AUD-01 · Scn: SCN-A · ADR: ADR-019 · Deps: T041 · Out: tests · TDD: Red · Val: fails as expected · Doc: — · Trace: tags FR-POL-* · Risk: — · Done: red recorded · Appr: default
- [X] T052 [US2] Implement `res/orchestration/policy-set.yaml` (version `1.0.0`) and `main/orchestration/policy/{PolicySet, PolicySetLoader, PolicyRule, PolicyEngine, ReadinessEvaluator}.java` (version pinning per run, outcome derivation, blocking, advisory handling, basic readiness) until `PolicyEngineTest` passes
  - Req: FR-POL-01, FR-POL-02, FR-POL-03, FR-POL-05, FR-RDY-01 · Scn: SCN-A · ADR: ADR-019 · Deps: T051 · Out: policy engine core · TDD: Green · Val: `mvnw test -Dtest=PolicyEngineTest` · Doc: docs/governance/policy-set.md (T079) · Trace: — · Risk: unversioned evaluation. Guardrail: version asserted on every evaluation · Done: default · Appr: default
- [X] T131 [US2] Write failing `test/orchestration/agent/ComplianceEvaluationAgentTest.java` (compliance and readiness reports validate against their schemas; mandatory FAIL → `PolicyBlocked`), then implement the rule beans in `main/orchestration/policy/rules/` (SEC-001, SEC-002, SEC-003, PRV-001, AUD-001, AUD-002, LIC-001 with `main/orchestration/policy/SbomReader.java`, TST-001, TST-002, DOC-001, CHG-001, CHG-002, REL-001, AUT-001) and `main/orchestration/agent/ComplianceEvaluationAgent.java` (compliance and readiness reports) until the per-rule tests pass
  - Req: FR-POL-02, FR-POL-06, NFR-SEC-05, NFR-AUD-01, FR-RDY-01 · Scn: SCN-A · ADR: ADR-019 · Deps: T052 · Out: 14 rules and the compliance agent · TDD: Green · Val: `mvnw test -Dtest="*RuleTest,ComplianceEvaluationAgentTest"` · Doc: docs/governance/policy-set.md (T079) · Trace: — · Risk: SBOM missing under IDE runs. Guardrail: LIC-001 FAIL with clear evidence · Done: default · Appr: default
- [X] T053 [US2] Test first, then implement `main/orchestration/agent/ReleaseAgent.java` (set release with parameters via port; post-release smoke probe; rollback of release state on failure) and `FinalSummaryAgent.java` (Markdown summary of plan, decisions, policy outcomes, validation, risks, metrics, terminal outcome); tests `ReleaseAgentTest`, `FinalSummaryAgentTest`
  - Req: FR-RDY-03, FR-ORC-17, FR-REL-05 · Scn: SCN-A · ADR: ADR-010, ADR-018 · Deps: T049, T131 · Out: two agents · TDD: Red→Green · Val: `mvnw test -Dtest=ReleaseAgentTest,FinalSummaryAgentTest` · Doc: — · Trace: tags FR-RDY-03, FR-ORC-17 · Risk: — · Done: default · Appr: default

### Greenfield capability (custom alias) — contract 1.1.0

- [X] T054 [US2] Write failing `test/shortener/domain/AliasPolicyTest.java` and `test/shortener/service/LinkCreationAliasTest.java` (alias `spring-sale` → code `spring-sale`; in use → `ALIAS_CONFLICT`; outside `[A-Za-z0-9_-]` or length outside 3–32 → `INVALID_ALIAS`; reserved `api` → `RESERVED_ALIAS`; unreleased → `CAPABILITY_NOT_AVAILABLE`; alias equal to an existing generated code → `ALIAS_CONFLICT`)
  - Req: FR-CAP-02, FR-CAP-01 · Scn: SCN-A · ADR: ADR-004, ADR-018 · Deps: T022 · Out: tests · TDD: Red · Val: fails as expected · Doc: — · Trace: tags FR-CAP-02, SCN-A · Risk: — · Done: red recorded · Appr: default
- [X] T055 [US2] Implement `res/db/migration/V3__custom_alias.sql` (`short_link.custom_alias BOOLEAN NOT NULL DEFAULT FALSE`), `main/shortener/domain/AliasPolicy.java`, `main/shortener/service/CustomAliasCapability.java`, and the alias path in `LinkCreationService`; extend `LinkApiContractTest` for the 1.1.0 fields
  - Req: FR-CAP-02, NFR-CHG-01 · Scn: SCN-A · ADR: ADR-018 · Deps: T054 · Out: custom-alias capability · TDD: Green · Val: `mvnw test -Dtest=AliasPolicyTest,LinkCreationAliasTest,LinkApiContractTest` · Doc: docs/api/links.md (alias) · Trace: — · Risk: route collision. Guardrail: reserved list · Done: default · Appr: default

### Workflow API and SCN-A end-to-end

- [X] T056 [US2] Test first in `test/orchestration/controller/WorkflowApiTest.java` (submit → 201 with `Location`; list; detail with stages and pending actions; plan versions; requirement versions; artifacts and artifact detail; decisions; timeline; policy evaluations; summary; 404 unknown run; role checks), then implement `main/orchestration/controller/WorkflowController.java` and `main/orchestration/dto/WorkflowViews.java`; add these responses to `test/contract/WorkflowApiContractTest.java`
  - Req: FR-ORC-01, FR-ORC-09, FR-AUD-06 · Scn: SCN-A · ADR: ADR-013, ADR-015 · Deps: T053 · Out: workflow API · TDD: Red→Green · Val: `mvnw test -Dtest=WorkflowApiTest,WorkflowApiContractTest` · Doc: — · Trace: tags FR-ORC-09 · Risk: — · Done: default · Appr: default
- [X] T057 [US2] Add the fixed input `res/scenarios/scn-a-greenfield.json` (GF-001 verbatim from spec) and end-to-end test `test/e2e/ScenarioAGreenfieldE2ETest.java` (`RANDOM_PORT`, HTTP only): quality-check record with the no-clarification rationale; `CLARIFICATION` `SKIPPED`; API/schema impact in the design; architecture approval by `bob` and release approval by `carol`, labeled **simulated human input**; `IMPLEMENTATION` and `DOCUMENTATION` dispatched in one scheduling cycle, `TESTING` and `SECURITY_VERIFICATION` dispatched in one scheduling cycle, and `VALIDATION` starting only after all four finished (timeline plus audit cycles; review RC-2); policy outcomes with version `1.0.0`; `COMPLETED`/`READY`; alias usable over HTTP afterwards; audit verification valid; exports E-A1..E-A9 via `EvidenceExporter`
  - Req: FR-ORC-04, FR-ORC-05, FR-ORC-13, FR-CAP-02, FR-POL-01, SC-002 · Scn: SCN-A · ADR: ADR-005, ADR-008, ADR-018 · Deps: T055, T056 · Out: SCN-A evidence under `target/evidence/scn-a/` · TDD: Red→Green (fails until the full path works) · Val: `mvnw test -Dtest=ScenarioAGreenfieldE2ETest` · Doc: docs/scenarios/scn-a-greenfield.md (T058) · Trace: tags SCN-A · Risk: simulated approvals mistaken for the candidate's. Guardrail: label in the test and the exported evidence · Done: default · Appr: default
- [X] T058 [US2] Write `docs/scenarios/scn-a-greenfield.md` (input, interpretation, decomposition, orchestration path, approvals, failure path RDR-01 reference, validation, evidence index E-A1..E-A9 with the regeneration command) and `docs/architecture/orchestration.md` (engine model, stage table, state machines, scheduling algorithm)
  - Req: SC-009, Constitution X · Scn: SCN-A · ADR: ADR-005, ADR-007 · Deps: T057 · Out: docs · TDD: N/A · Val: links resolve; content matches the exported evidence · Doc: new docs · Trace: evidence ids · Risk: doc-behavior drift. Guardrail: evidence copied from the test export · Done: default · Appr: default

**Checkpoint (sync)**: SCN-A completes end-to-end.

---

## Phase 5: User Story 3 — Govern high-impact decisions (Priority: P1)

**Goal**: non-bypassable, content-bound, deadline-safe approvals.

**Independent Test**: `mvnw test -Dtest="*Gate*Test,SeparationOfDutiesTest,ApprovalBindingTest"`.

- [X] T059 [P] [US3] Write failing `test/orchestration/governance/SeparationOfDutiesTest.java` (the run requester cannot approve `ARCHITECTURE_APPROVAL`, `CHANGE_APPROVAL`, or `RELEASE_APPROVAL` → 403 `SEPARATION_OF_DUTIES`; an exception requester cannot decide their own exception)
  - Req: FR-GOV-04 · Scn: — · ADR: ADR-008 · Deps: T042 · Out: tests · TDD: Red · Val: fails as expected · Doc: — · Trace: tags FR-GOV-04 · Risk: — · Done: red recorded · Appr: default
- [X] T060 [P] [US3] Write failing `test/orchestration/governance/ApprovalBindingTest.java` (approval stores bundle fingerprints; a changed bound artifact invalidates the decision with a reason and re-opens the gate; an unchanged bundle keeps it valid)
  - Req: FR-GOV-05 · Scn: — · ADR: ADR-008, ADR-011 · Deps: T042 · Out: tests · TDD: Red · Val: fails as expected · Doc: — · Trace: tags FR-GOV-05 · Risk: — · Done: red recorded · Appr: default
- [X] T061 [P] [US3] Write failing `test/orchestration/governance/GateDeadlineTest.java` (deadline passes → run `SAFE_STOPPED` with an escalation audit event; a late decision → 409 `DEADLINE_PASSED`; never approved by default)
  - Req: FR-GOV-07, FR-REL-06 · Scn: RDR-04 · ADR: ADR-008, ADR-009 · Deps: T042 · Out: tests · TDD: Red · Val: fails as expected · Doc: — · Trace: tags FR-GOV-07, RDR-04 · Risk: — · Done: red recorded · Appr: default
- [X] T062 [P] [US3] Write failing `test/orchestration/governance/ConcurrentGateDecisionTest.java` (two approvers race: exactly one decision recorded, the other 409 `CONCURRENT_DECISION`) and `test/orchestration/governance/GateRejectionTest.java` (rejection → compensation of leftover side effects → run `REJECTED`; no capability change)
  - Req: FR-GOV-06, FR-GOV-09 · Scn: — · ADR: ADR-006, ADR-008 · Deps: T042 · Out: tests · TDD: Red · Val: fails as expected · Doc: — · Trace: tags FR-GOV-06 · Risk: — · Done: red recorded · Appr: default
- [X] T063 [P] [US3] Write failing `test/orchestration/governance/WaitingGateIndependenceTest.java` (while a gate waits, stages that do not depend on it continue and finish)
  - Req: FR-GOV-02 · Scn: — · ADR: ADR-005 · Deps: T042 · Out: tests · TDD: Red · Val: fails as expected · Doc: — · Trace: tags FR-GOV-02 · Risk: — · Done: red recorded · Appr: default
- [X] T064 [US3] Harden `main/orchestration/governance/GateService.java` (separation of duties, bundle fingerprint binding, optimistic-lock conflict → `CONCURRENT_DECISION`, deadline check), add the re-validation guard in `RunCoordinator`, implement `main/orchestration/governance/DeadlineSweeper.java` (`@Scheduled`) and the rejection path
  - Req: FR-GOV-02..07, FR-GOV-09 · Scn: RDR-04 · ADR: ADR-008 · Deps: T059–T063 · Out: hardened governance · TDD: Green · Val: `mvnw test -Dtest="SeparationOfDutiesTest,ApprovalBindingTest,GateDeadlineTest,ConcurrentGateDecisionTest,GateRejectionTest,WaitingGateIndependenceTest"` · Doc: docs/architecture/governance.md · Trace: — · Risk: — · Done: default · Appr: default
- [X] T065 [US3] Extend `test/architecture/ArchitectureTest.java` (agents must not depend on `GateService`, `PolicyExceptionService`, policy mutation, HTTP client classes, or the `common.security` package — review RC-5) and add `test/security/GovernanceSecurityMatrixTest.java` (401/403 per control-plane endpoint and role)
  - Req: NFR-AUT-01, NFR-SEC-02, FR-GOV-03 · Scn: — · ADR: ADR-001, ADR-008, ADR-015 · Deps: T064 · Out: verification tests · TDD: Verification · Val: `mvnw test -Dtest=ArchitectureTest,GovernanceSecurityMatrixTest` · Doc: — · Trace: tags NFR-AUT-01 · Risk: — · Done: default · Appr: default
- [X] T066 [US3] Write `docs/architecture/governance.md` (gates, roles, separation of duties, binding, deadlines, anti-bypass guarantees) and add approval evidence (decision records with bound fingerprints) to the SCN-A export
  - Req: FR-GOV-09, SC-009 · Scn: SCN-A · ADR: ADR-008 · Deps: T064, T057 · Out: governance doc and approval evidence · TDD: N/A · Val: evidence file lists decisions with fingerprints · Doc: new doc · Trace: evidence ids · Risk: — · Done: default · Appr: default

**Checkpoint (sync)**: governance suite green.

---

## Phase 6: User Story 4 — Decide release readiness and operate runs safely (Priority: P2)

**Goal**: retry, timeout, fallback, compensation, safe-stop, pause and resume, recovery, budgets,
fault injection, policy exceptions, and readiness, proven by drills RDR-01..RDR-07.

**Independent Test**: `mvnw test -Dtest="ReliabilityDrillsE2ETest,RestartResumeTest"`.

### Retry, timeout, and fallback

- [X] T067 [US4] Write failing `test/orchestration/reliability/RetryPolicyTest.java` (200 ms × 2ⁿ capped at 5 s), `FailureClassifierTest.java` (transient vs. permanent defaults), and `StageRetryTimeoutTest.java` (transient ×2 → success with 3 attempts; permanent → no retry; exhausted → `FAILED`; timeout → transient, late result recorded `DISCARDED`)
  - Req: FR-REL-01, FR-REL-02, FR-REL-09, NFR-REL-02 · Scn: RDR-01 · ADR: ADR-009 · Deps: T041 · Out: tests · TDD: Red · Val: fails as expected · Doc: — · Trace: tags FR-REL-01/02 · Risk: — · Done: red recorded · Appr: default
- [X] T068 [US4] Implement `main/orchestration/reliability/{FailureClassifier, RetryPolicy, StagePolicyProperties}.java` and coordinator integration (`RETRY_WAIT` with persisted `next_attempt_at` on a `TaskScheduler`; per-attempt timeout with interrupt; stale-result discard; cancellation checks in agents between steps and a JDBC query timeout for agent database calls — review RC-6)
  - Req: FR-REL-01, FR-REL-02, FR-REL-09 · Scn: RDR-01 · ADR: ADR-009 · Deps: T067 · Out: retry and timeout · TDD: Green · Val: `mvnw test -Dtest=RetryPolicyTest,FailureClassifierTest,StageRetryTimeoutTest` · Doc: docs/architecture/reliability.md (T081) · Trace: — · Risk: unbounded loops. Guardrail: max-attempt assertion · Done: default · Appr: default
- [X] T069 [US4] Test first in `test/orchestration/reliability/FallbackTest.java` (permanent or exhausted → fallback agent runs; stage `degraded`; `FALLBACK_USED` decision; verification stages have no fallback), then implement the fallback registry and `main/orchestration/agent/TemplateDocumentationAgent.java` and `MinimalSummaryAgent.java`
  - Req: FR-REL-03, FR-RDY-01 · Scn: RDR-02 · ADR: ADR-009 · Deps: T068 · Out: fallback · TDD: Red→Green · Val: `mvnw test -Dtest=FallbackTest` · Doc: — · Trace: tags FR-REL-03, RDR-02 · Risk: silent degradation. Guardrail: readiness limitation asserted · Done: default · Appr: default

### Rollback, compensation, safe-stop, operator controls

- [X] T070 [US4] Test first in `test/orchestration/reliability/CompensationCoordinatorTest.java` (reverse completion order; 3 retries; failure → `manualInterventionRequired`; audit and decision per action; deletes only synthetic rows of the run) and `ReleaseRollbackTest.java` (post-release verification failure → release state rolled back → run `SAFE_STOPPED`; rollback skipped with a recorded conflict if the flag no longer holds this run's value), then implement `main/orchestration/reliability/CompensationCoordinator.java`
  - Req: FR-REL-04, FR-REL-05, FR-REL-10, FR-RDY-03 · Scn: RDR-03 · ADR: ADR-010 · Deps: T053, T068 · Out: compensation · TDD: Red→Green · Val: `mvnw test -Dtest=CompensationCoordinatorTest,ReleaseRollbackTest` · Doc: — · Trace: tags FR-REL-04/05, RDR-03 · Risk: destroying consumer data. Guardrail: synthetic-only deletion test · Done: default · Appr: default
- [X] T071 [US4] Test first in `test/orchestration/reliability/SafeStopServiceTest.java` (each trigger — mandatory policy rejection, exhausted retries and fallback, gate deadline, compensation failure, autonomy budget, clarification rounds > 3, operator request — ends in exactly one terminal outcome; no new dispatch; in-flight attempts cancelled; final summary produced), then implement `main/orchestration/reliability/SafeStopService.java` and the finalizer (synthetic-data sweep, summary, `RUN_TERMINATED`)
  - Req: FR-REL-06, FR-ORC-17, NFR-REL-03 · Scn: RDR-04, RDR-07 · ADR: ADR-009 · Deps: T070 · Out: safe-stop · TDD: Red→Green · Val: `mvnw test -Dtest=SafeStopServiceTest` · Doc: — · Trace: tags FR-REL-06 · Risk: — · Done: default · Appr: default
- [X] T072 [US4] Test first in `test/orchestration/controller/OperationsControllerTest.java` (pause: no new dispatch, in-flight finishes; resume continues; safe-stop from running, waiting, and paused; `RELEASE_OWNER` only), then implement `main/orchestration/controller/OperationsController.java` and coordinator pause support
  - Req: FR-REL-07, FR-REL-06 · Scn: RDR-07 · ADR: ADR-009, ADR-015 · Deps: T071 · Out: operator controls · TDD: Red→Green · Val: `mvnw test -Dtest=OperationsControllerTest` · Doc: docs/operations/runbook.md (T081) · Trace: tags FR-REL-07, RDR-07 · Risk: — · Done: default · Appr: default

### Resume, budget, fault injection

- [X] T073 [US4] Test first in `test/orchestration/reliability/RestartResumeTest.java` (temporary H2 file database; context 1 runs with a `DELAY` fault on `DESIGN` and is closed mid-attempt — evidence labeled "graceful context stop", with the manual process-kill variant documented in the runbook (review RC-8); context 2 starts → the interrupted attempt is `INTERRUPTED`, the stage is re-dispatched, the run completes, `REQUIREMENT_INGESTION` attempts = 1, a failure event is recovered with mechanism `RESUME`), then implement `main/orchestration/reliability/RecoveryService.java`
  - Req: FR-REL-08, FR-ORC-08, SC-007, NFR-RCV-01 · Scn: RDR-05 · ADR: ADR-006, ADR-009 · Deps: T068 · Out: recovery · TDD: Red→Green · Val: `mvnw test -Dtest=RestartResumeTest` · Doc: runbook · Trace: tags FR-REL-08, RDR-05 · Risk: duplicate side effects. Guardrail: only unfinished attempts re-dispatched · Done: default · Appr: default
- [ ] T074 [P] [US4] Test first in `test/orchestration/reliability/AutonomyBudgetTest.java` (60 attempts or 10 minutes of processing → `SAFE_STOPPED` with reason), then implement `main/orchestration/reliability/AutonomyBudget.java`
  - Req: FR-ORC-18, NFR-AUT-02 · Scn: — · ADR: ADR-009 · Deps: T068 · Out: budget · TDD: Red→Green · Val: `mvnw test -Dtest=AutonomyBudgetTest` · Doc: — · Trace: tags FR-ORC-18 · Risk: — · Done: default · Appr: default
- [X] T075 [P] [US4] Test first in `test/orchestration/reliability/FaultInjectionTest.java` (default profile → 400 `FAULT_INJECTION_DISABLED`; enabled → faults applied per stage and occurrence; attempts, failure events, and policy evaluations flagged `simulated`), then implement `main/orchestration/reliability/FaultInjector.java` and `SimulationOptions` validation
  - Req: FR-REL-11, Constitution X · Scn: RDR-01..RDR-07 · ADR: ADR-009, ADR-014 · Deps: T068 · Out: fault injection · TDD: Red→Green · Val: `mvnw test -Dtest=FaultInjectionTest` · Doc: runbook · Trace: tags FR-REL-11 · Risk: enabled in production. Guardrail: default-off test · Done: default · Appr: default

### Policy exceptions and release readiness

- [X] T076 [US4] Write failing `test/orchestration/policy/PolicyExceptionFlowTest.java` (mandatory FAIL → `AWAITING_DECISION(POLICY_EXCEPTION)`; request requires policy, reason, scope, compensating control, expiry; a pending exception does not unblock; approval by a different approver → re-evaluation → `EXCEPTION_REQUESTED` → `READY_WITH_ACCEPTED_LIMITATIONS`; rejection → `SAFE_STOPPED`; deadline → `SAFE_STOPPED`)
  - Req: FR-POL-03, FR-POL-04, FR-POL-06 · Scn: RDR-06 · ADR: ADR-019 · Deps: T131, T071 · Out: tests · TDD: Red · Val: fails as expected · Doc: — · Trace: tags FR-POL-04, RDR-06 · Risk: — · Done: red recorded · Appr: default
- [X] T077 [US4] Write failing `test/orchestration/policy/ReadinessEvaluatorTest.java` (READY / READY_WITH_ACCEPTED_LIMITATIONS / NOT_READY matrix; an exception expired at decision time → release approval 409 `RELEASE_NOT_READY`)
  - Req: FR-RDY-01, FR-RDY-02 · Scn: RDR-06 · ADR: ADR-019 · Deps: T052 · Out: tests · TDD: Red · Val: fails as expected · Doc: — · Trace: tags FR-RDY-01/02 · Risk: — · Done: red recorded · Appr: default
- [X] T078 [US4] Implement `main/orchestration/policy/PolicyExceptionService.java`, the exception endpoints in `GovernanceController`, the full `ReadinessEvaluator`, and the readiness re-check at the release decision
  - Req: FR-POL-04, FR-RDY-01, FR-RDY-02, FR-RDY-04 · Scn: RDR-06 · ADR: ADR-019, ADR-008 · Deps: T076, T077 · Out: exceptions and readiness · TDD: Green · Val: `mvnw test -Dtest=PolicyExceptionFlowTest,ReadinessEvaluatorTest` · Doc: docs/governance/policy-set.md · Trace: — · Risk: — · Done: default · Appr: default
- [X] T079 [US4] Write `docs/governance/policy-set.md` (policy set 1.0.0 table, outcomes, exception workflow, release-blocking conditions) mirroring `res/orchestration/policy-set.yaml`
  - Req: FR-POL-01, FR-POL-04 · Scn: — · ADR: ADR-019 · Deps: T078 · Out: policy doc · TDD: N/A · Val: doc matches YAML (ids and severities) · Doc: new doc · Trace: — · Risk: doc drift · Done: default · Appr: default

### Drills and documentation

- [X] T080 [US4] End-to-end drills in `test/e2e/ReliabilityDrillsE2ETest.java` (demo profile semantics, HTTP only): RDR-01 retry (runs the SCN-A input with `TRANSIENT_ERROR` ×2 on `TESTING`, the failure path cited by SCN-A); RDR-02 documentation fallback → `READY_WITH_ACCEPTED_LIMITATIONS`; RDR-03 `VERIFICATION_FAILURE` on `RELEASE` → rollback → `SAFE_STOPPED`; RDR-04 gate deadline → `SAFE_STOPPED`; RDR-06 `POLICY_FAILURE` DOC-001 → exception approved (with compensating control) and a second run rejected; RDR-07 pause, resume, safe-stop. Exports evidence per drill (retry, fallback, compensation, safe-stop, failed-policy blocking, approved exception, compensating control); RDR-05 evidence exported by `RestartResumeTest`
  - Req: FR-REL-01..07, FR-POL-03, FR-POL-04, FR-RDY-03, SC-002 · Scn: RDR-01..RDR-07 · ADR: ADR-009, ADR-010, ADR-019 · Deps: T069–T078 · Out: `target/evidence/drills/` · TDD: Verification · Val: `mvnw test -Dtest=ReliabilityDrillsE2ETest` · Doc: docs/scenarios/drills.md · Trace: tags RDR-* · Risk: timing flakiness. Guardrail: short configured backoff and deadlines, Awaitility · Done: default · Appr: default
- [X] T081 [US4] Write `docs/operations/runbook.md` (run, pause, resume, safe-stop, recovery after restart including the manual process-kill variant of RDR-05, drills, reset `./data/`), `docs/architecture/reliability.md` (taxonomy, retry and timeout, fallback registry, rollback vs. compensation, safe-stop procedure), and `docs/scenarios/drills.md` (drill evidence index)
  - Req: FR-REL-*, SC-009 · Scn: RDR-01..RDR-07 · ADR: ADR-009, ADR-010 · Deps: T080 · Out: docs · TDD: N/A · Val: links resolve; statements match drill evidence · Doc: new docs · Trace: evidence ids · Risk: doc drift · Done: default · Appr: default
- [X] T082 [US4] Checkpoint CP2: record the scope decision in `docs/assessment/timebox-and-scope.md`
  - Req: CON-01 · Scn: — · ADR: — · Deps: T080 · Out: checkpoint record · TDD: N/A · Val: record present · Doc: scope doc · Trace: — · Risk: — · Done: decision written · Appr: default

**Checkpoint (sync)**: reliability and drill suites green.

---

## Phase 7: User Story 5 — Change existing behavior safely (Priority: P2) — SCN-B

**Goal**: codebase-derived impact analysis, regression testing, and the click-limit capability,
with the repository brownfield gate executed **before** the click-limit code is written.

**Independent Test**: `mvnw test -Dtest=ScenarioBBrownfieldE2ETest`.

- [X] T083 [US5] Test first in `test/orchestration/knowledge/CodebaseScannerTest.java` (fixture tree under `src/test/resources/codebase-fixture/`: import graph, reverse-dependency closure, tests importing impacted classes, docs mentioning endpoints), then implement `main/orchestration/knowledge/CodebaseScanner.java`
  - Req: FR-ORC-14 · Scn: SCN-B · ADR: ADR-017 · Deps: T041 · Out: scanner · TDD: Red→Green · Val: `mvnw test -Dtest=CodebaseScannerTest` · Doc: — · Trace: tags FR-ORC-14 · Risk: reading outside the repository. Guardrail: root confined to `app.orchestration.codebase-root` · Done: default · Appr: default
- [X] T084 [US5] Test first in `test/orchestration/agent/ImpactAnalysisAgentTest.java` (source-scan method fills components, interfaces, data flows, tests, documentation, regression risks, rollout, rollback, impacted requirements; each component's `reason` states whether it is a catalog seed or derived through the reverse-dependency closure with its dependency chain, and at least one derived component is not a seed (review RC-3); a missing root → `CatalogImpactAnalysisAgent` fallback with `degraded = true`; output validates against `impact-analysis.schema.json`), then implement `main/orchestration/agent/ImpactAnalysisAgent.java` and `CatalogImpactAnalysisAgent.java`
  - Req: FR-ORC-14, FR-REL-03 · Scn: SCN-B · ADR: ADR-017, ADR-009 · Deps: T083, T069 · Out: impact analysis · TDD: Red→Green · Val: `mvnw test -Dtest=ImpactAnalysisAgentTest` · Doc: — · Trace: tags FR-ORC-14 · Risk: — · Done: default · Appr: default
- [X] T085 [US5] Test first, then implement baseline regression probes `main/orchestration/agent/probes/RegressionProbes.java` (R-P1 creation, R-P2 redirect, R-P3 expiry, R-P4 links without a limit stay unlimited, R-P5 stats, R-P6 idempotent replay) and `main/orchestration/agent/RegressionTestingAgent.java`; test `RegressionTestingAgentTest`
  - Req: FR-ORC-14, NFR-CHG-02 · Scn: SCN-B · ADR: ADR-017 · Deps: T049 · Out: regression stage · TDD: Red→Green · Val: `mvnw test -Dtest=RegressionTestingAgentTest` · Doc: — · Trace: tags SCN-B · Risk: — · Done: default · Appr: default
- [X] T086 [US5] **Brownfield impact analysis gate (repository process, guide §19)**: before any click-limit code exists, run the implemented `IMPACT_ANALYSIS` for BF-001 against this repository through `test/e2e/BrownfieldImpactGateIT.java` and commit `docs/scenarios/scn-b-impact-analysis.md` (current vs. requested behavior; impacted modules, interfaces, API contracts, domain rules, persistence, orchestration states, telemetry, documentation; direct and regression tests; security, reliability, and data-compatibility risks; rollback/compensation implications; affected ADRs; downstream tasks needing replanning; dependency map; test-first change plan; regression risk matrix; replanning decision; human approval requirement)
  - Req: FR-ORC-14, Constitution I · Scn: SCN-B · ADR: ADR-010, ADR-016 · Deps: T084, T085 · Out: impact evidence committed before T087 · TDD: N/A (analysis) · Val: `mvnw test -Dtest=BrownfieldImpactGateIT`; commit ordering visible in `git log` · Doc: new scenario doc · Trace: SCN-B evidence E-B1 · Risk: analysis after the fact. Guardrail: commit precedes V4 · Done: doc committed · Appr: **candidate review of the impact analysis — PENDING** (recorded in the gate register; implementation proceeds provisionally)
- [ ] T089 [US5] Add `res/scenarios/scn-b-brownfield.json` (BF-001 verbatim) and write `test/e2e/ScenarioBBrownfieldE2ETest.java` **before** the click-limit code (impact analysis in parallel with decomposition and threat assessment; regression testing in parallel with testing and security; architecture approval shows migration and rollback; `COMPLETED`/`READY`; a link created before release stays unlimited; exports E-B1..E-B9). Expected red: the run safe-stops at `IMPLEMENTATION` with "capability not delivered" (FR-ORC-15), recorded as evidence that the orchestrator refuses an undelivered change
  - Req: FR-ORC-14, FR-ORC-15, FR-CAP-03, SC-002 · Scn: SCN-B · ADR: ADR-005, ADR-016 · Deps: T086 · Out: `target/evidence/scn-b/` · TDD: Red→Green (green after T088) · Val: `mvnw test -Dtest=ScenarioBBrownfieldE2ETest` · Doc: docs/scenarios/scn-b-brownfield.md · Trace: tags SCN-B · Risk: — · Done: red run recorded, then green after T088 · Appr: default
- [ ] T087 [US5] Write failing `test/shortener/service/ClickLimitTest.java` and `test/shortener/ClickLimitConcurrencyTest.java`: `maxClicks` 2 → two redirects then 410; exactly N of more than N concurrent resolutions succeed; links without a limit unaffected; limit outside 1..1,000,000 → `INVALID_CLICK_LIMIT`; unreleased → `CAPABILITY_NOT_AVAILABLE`; a click-recording failure on a limited link → 503 (fail closed); after withdrawal, stored limits stay enforced
  - Req: FR-CAP-03, FR-ANL-04, FR-CAP-01 · Scn: SCN-B · ADR: ADR-016, ADR-010 · Deps: T086 · Out: tests · TDD: Red · Val: fails as expected · Doc: — · Trace: tags FR-CAP-03, SCN-B · Risk: — · Done: red recorded · Appr: default
- [ ] T088 [US5] Implement `res/db/migration/V4__click_limit.sql` (`max_clicks BIGINT NULL`, `CHECK (max_clicks BETWEEN 1 AND 1000000)`), `ShortLink.maxClicks`, the conditional update `… WHERE click_count < max_clicks` in `ShortLinkRepository`, the fail-closed branch in `RedirectService`, `main/shortener/service/ClickLimitCapability.java`, and click-limit probes CL-P1..CL-P6; extend `LinkApiContractTest` for the 1.2.0 fields
  - Req: FR-CAP-03, NFR-CHG-01, NFR-CHG-02 · Scn: SCN-B · ADR: ADR-016, ADR-018 · Deps: T087, T089 · Out: click-limit capability · TDD: Green (turns T087 and T089 green) · Val: `mvnw test -Dtest=ClickLimitTest,ClickLimitConcurrencyTest,LinkApiContractTest,ScenarioBBrownfieldE2ETest` · Doc: docs/api/links.md (maxClicks) · Trace: — · Risk: regression of unlimited links. Guardrail: R-P4 · Done: default · Appr: default
- [ ] T090 [US5] Write `docs/scenarios/scn-b-brownfield.md` (input, impact analysis summary linking T086, orchestration path, approvals, validation, the recorded red run, evidence index with regeneration commands) and update `docs/api/links.md`
  - Req: SC-009 · Scn: SCN-B · ADR: — · Deps: T088 · Out: docs · TDD: N/A · Val: links resolve · Doc: new doc · Trace: evidence ids · Risk: doc drift · Done: default · Appr: default

**Checkpoint (sync)**: SCN-B completes; SCN-A still green (regression).

---

## Phase 8: User Story 6 — Resolve an ambiguous requirement without unsafe progress (Priority: P2) — SCN-C

**Goal**: clarification with real suspension, requirement versioning, content-addressed replanning,
change control, late-ambiguity path suspension, and the default-expiry capability.

**Independent Test**: `mvnw test -Dtest=ScenarioCAmbiguousE2ETest`.

- [ ] T091 [US6] Write failing `test/orchestration/governance/ClarificationFlowTest.java` (AMB-001 → `CLARIFICATION_REQUEST` with options and impacts; run `AWAITING_HUMAN`; no downstream stage started; resume or downstream decisions refused; `APPROVER` answers → one decision per answer, requirement v2 with derived acceptance criteria and exclusions; more than 3 rounds → `SAFE_STOPPED`; a requester answer → 403)
  - Req: FR-GOV-08, FR-GOV-02, FR-REL-06 · Scn: SCN-C · ADR: ADR-008, ADR-011 · Deps: T064 · Out: tests · TDD: Red · Val: fails as expected · Doc: — · Trace: tags FR-GOV-08, SCN-C · Risk: — · Done: red recorded · Appr: default
- [ ] T092 [US6] Implement `main/orchestration/governance/ClarificationService.java` (question generation from lexicon templates, answer validation, requirement versioning with derived acceptance criteria) and `POST /api/v1/workflows/{runId}/clarifications` in `GovernanceController`
  - Req: FR-GOV-08 · Scn: SCN-C · ADR: ADR-011 · Deps: T091 · Out: clarification · TDD: Green · Val: `mvnw test -Dtest=ClarificationFlowTest` · Doc: — · Trace: — · Risk: — · Done: default · Appr: default
- [ ] T093 [P] [US6] Write failing `test/orchestration/planning/InputFingerprinterTest.java` (deterministic; changes when any declared input changes, including the agent id or version and the version of any knowledge resource the agent reads — catalog, lexicon, policy set (review RC-1); unchanged otherwise) and `test/orchestration/planning/ReplanningServiceTest.java` (downstream closure invalidated with generation + 1 and superseded artifacts; unchanged fingerprint → `REUSED` without execution; changed → re-run; bound approval invalidated when its fingerprint changes; plan version with diff and trigger; terminal run refused)
  - Req: FR-RPL-01, FR-RPL-02, FR-RPL-05, FR-GOV-05 · Scn: SCN-C · ADR: ADR-011 · Deps: T092 · Out: tests · TDD: Red · Val: fails as expected · Doc: — · Trace: tags FR-RPL-* · Risk: — · Done: red recorded · Appr: default
- [ ] T094 [US6] Implement `main/orchestration/planning/{ReplanningService, InputFingerprinter}.java`, the reuse path in `RunCoordinator`, and plan refinement on classification change (`CHANGE_TO_EXISTING` adds `IMPACT_ANALYSIS` and `REGRESSION_TESTING`)
  - Req: FR-RPL-01, FR-RPL-02, FR-RPL-05 · Scn: SCN-C · ADR: ADR-011, ADR-007 · Deps: T093 · Out: replanning · TDD: Green · Val: `mvnw test -Dtest=InputFingerprinterTest,ReplanningServiceTest` · Doc: docs/architecture/orchestration.md (replanning) · Trace: — · Risk: hidden inputs. Guardrail: context exposes declared inputs only · Done: default · Appr: default
- [ ] T095 [US6] Test first in `test/orchestration/governance/ChangeRequestFlowTest.java` (non-material or pre-approval → applied immediately; material after approval → `CHANGE_APPROVAL` inserted as a dependency of every not-started stage (plan v+1); approve → applied, v+2; reject → gate removed, run continues; impact lists affected stages and invalidated approvals), then implement `main/orchestration/governance/ChangeRequestService.java` and the change-request endpoints
  - Req: FR-RPL-03, FR-RPL-04, FR-POL-01 · Scn: SCN-C · ADR: ADR-011, ADR-008 · Deps: T094 · Out: change control · TDD: Red→Green · Val: `mvnw test -Dtest=ChangeRequestFlowTest` · Doc: — · Trace: tags FR-RPL-03/04 · Risk: material change bypass. Guardrail: materiality rules test · Done: default · Appr: default
- [ ] T096 [US6] Test first in `test/orchestration/planning/LateAmbiguityTest.java` (a mid-run stage returns `NeedsClarification` → only its dependents wait; independent branches finish; answer → affected path replanned and resumed). This is the greenfield escalation evidence for SCN-A. Then implement the late-clarification path in `RunCoordinator` and `ClarificationService`
  - Req: FR-RPL-06, FR-GOV-02 · Scn: SCN-A, SCN-C · ADR: ADR-011 · Deps: T094 · Out: path suspension · TDD: Red→Green · Val: `mvnw test -Dtest=LateAmbiguityTest` · Doc: docs/scenarios/scn-a-greenfield.md (escalation section) · Trace: tags FR-RPL-06, SCN-A · Risk: — · Done: default · Appr: default
- [ ] T098 [US6] Add `res/scenarios/scn-c-ambiguous.json` (AMB-001 verbatim) and write `test/e2e/ScenarioCAmbiguousE2ETest.java` **before** the default-expiry code: the 17 evidence items of spec SCN-C; clarification answered with the reference decisions D1–D4 as **simulated human input** by `bob`; requirement v1 → v2; plan v1 → v2 diff adds `IMPACT_ANALYSIS` and `REGRESSION_TESTING`; resumed at `REQUIREMENT_ANALYSIS` (ingestion attempts = 1); `COMPLETED`/`READY` with `defaultExpiryDays = 30`; exports to `target/evidence/scn-c/`. Expected red: the run passes clarification and replanning, then safe-stops at `IMPLEMENTATION` because default expiry is not delivered
  - Req: FR-GOV-08, FR-RPL-01, FR-RPL-02, FR-CAP-04, FR-ORC-15, SC-002 · Scn: SCN-C · ADR: ADR-011, ADR-018 · Deps: T095, T096 · Out: SCN-C evidence · TDD: Red→Green (green after T097) · Val: `mvnw test -Dtest=ScenarioCAmbiguousE2ETest` · Doc: docs/scenarios/scn-c-ambiguous.md · Trace: tags SCN-C · Risk: simulated answers mistaken for the candidate's. Guardrail: labels plus a live walkthrough for the candidate · Done: red run recorded, then green after T097 · Appr: default
- [ ] T097 [US6] Test first in `test/shortener/service/DefaultExpiryTest.java` (released with `defaultExpiryDays` N → new links without `expiresAt` expire at creation + N days; explicit expiry kept; existing links unchanged; unreleased → no default), then implement `main/shortener/service/DefaultExpiryCapability.java`, the creation-path rule, and probes DE-P1..DE-P3
  - Req: FR-CAP-04 · Scn: SCN-C · ADR: ADR-018 · Deps: T092, T098 · Out: default-expiry capability · TDD: Red→Green (also turns T098 green) · Val: `mvnw test -Dtest=DefaultExpiryTest,ScenarioCAmbiguousE2ETest` · Doc: docs/api/links.md (behavior note) · Trace: tags FR-CAP-04 · Risk: — · Done: default · Appr: default
- [ ] T099 [US6] Write `docs/scenarios/scn-c-ambiguous.md` (input, detected ambiguities, suspension evidence, questions, simulated vs. candidate decisions, replanning diff, resumed state, evidence index, live walkthrough where the candidate answers personally)
  - Req: SC-009 · Scn: SCN-C · ADR: — · Deps: T097 · Out: doc · TDD: N/A · Val: links resolve · Doc: new doc · Trace: evidence ids · Risk: — · Done: default · Appr: default
- [ ] T100 [US6] Checkpoint CP3: record the scope decision in `docs/assessment/timebox-and-scope.md`
  - Req: CON-01 · Scn: — · ADR: — · Deps: T097 · Out: checkpoint record · TDD: N/A · Val: record present · Doc: scope doc · Trace: — · Risk: — · Done: decision written · Appr: default

**Checkpoint (sync)**: SCN-A, SCN-B, SCN-C all green.

---

## Phase 9: User Story 7 — Verify evidence independently (Priority: P3)

**Goal**: audit verification, lineage, MTTR and reliability reporting, metrics and logs,
traceability, and evidence indexes a reviewer can re-run.

**Independent Test**: `mvnw test -Dtest="Audit*Test,Lineage*Test,Reliability*Test,TraceabilityMatrixTest"`.

- [ ] T101 [US7] Test first in `test/orchestration/controller/EvidenceControllerTest.java` (`GET .../audit`; `GET .../audit/verification` valid; after direct SQL tampering → invalid with `firstBrokenSeq`; `GET /api/v1/capabilities` and `/api/v1/policies`; auditor role), then implement `main/orchestration/controller/EvidenceController.java`
  - Req: FR-AUD-02, FR-AUD-03, SC-006 · Scn: — · ADR: ADR-012 · Deps: T056 · Out: evidence API · TDD: Red→Green · Val: `mvnw test -Dtest=EvidenceControllerTest` · Doc: — · Trace: tags FR-AUD-02/03 · Risk: — · Done: default · Appr: default
- [ ] T102 [US7] Test first in `test/orchestration/engine/LineageServiceTest.java` (input closure plus bound and requirement-version decisions), then implement `main/orchestration/engine/LineageService.java` and the `lineage` field of the artifact detail response
  - Req: FR-AUD-03, FR-ORC-10 · Scn: SCN-C · ADR: ADR-012 · Deps: T101 · Out: lineage · TDD: Red→Green · Val: `mvnw test -Dtest=LineageServiceTest` · Doc: — · Trace: tags FR-AUD-03 · Risk: — · Done: default · Appr: default

### MTTR instrumentation and reporting

- [ ] T103 [US7] **MTTR timestamps**: test first in `test/orchestration/metrics/FailureEventRecorderTest.java` (episode opens at the first failed attempt with `detected_at`; `recovery_started_at` at the next attempt start; `recovery_completed_at` at success; mechanism `RETRY`/`FALLBACK`/`RESUME`; `UNRECOVERED` on terminal failure; superseded episodes excluded), then implement `main/orchestration/metrics/FailureEventRecorder.java` wired into the coordinator and recovery service
  - Req: FR-AUD-04, NFR-RCV-02 · Scn: RDR-01, RDR-02, RDR-05 · ADR: ADR-012 · Deps: T073 · Out: failure episodes · TDD: Red→Green · Val: `mvnw test -Dtest=FailureEventRecorderTest` · Doc: — · Trace: tags FR-AUD-04 · Risk: — · Done: default · Appr: default
- [ ] T104 [US7] **MTTR calculation and population**: test first in `test/orchestration/metrics/ReliabilityReportServiceTest.java` with fixed fixtures (MTTR = Σ recovered durations ÷ recovered count; unrecovered excluded from the denominator and listed; exclusions `OPEN` and superseded with counts; success, failure, retry, and compensation rates; latency excluding human wait; population including simulated share; label `DEMONSTRATION DATA - not production statistics`), then implement `main/orchestration/metrics/ReliabilityReportService.java` and `GET /api/v1/reliability/report`
  - Req: FR-AUD-04, SC-008, NFR-RCV-02 · Scn: RDR-01..RDR-07 · ADR: ADR-012 · Deps: T103 · Out: reliability report · TDD: Red→Green · Val: `mvnw test -Dtest=ReliabilityReportServiceTest` · Doc: — · Trace: tags FR-AUD-04, SC-008 · Risk: — · Done: default · Appr: default
- [ ] T105 [US7] **MTTR validation evidence**: export the report after the drills (`target/evidence/reliability-report.json`) and write `docs/assessment/mttr-validation.md` (inputs, recovered-event population, exclusions, unrecovered failures, a hand calculation reproducing the reported MTTR, demonstration-data limitations)
  - Req: FR-AUD-04, SC-008 · Scn: RDR-01..RDR-07 · ADR: ADR-012 · Deps: T104, T080 · Out: MTTR evidence · TDD: N/A · Val: hand calculation equals the report · Doc: new doc · Trace: evidence id · Risk: presenting demo data as production. Guardrail: label in the doc and the report · Done: default · Appr: default
- [ ] T106 [US7] **Logs, metrics, traces**: test first in `test/orchestration/metrics/MetricsTest.java` (meters of plan §7 plus the `sdlc.executor.active` gauge (review RC-6) registered and incremented; `sdlc.stage` observation recorded; MDC `runId`, `stage`, `attempt` present in captured logs), then implement meters in `main/orchestration/metrics/OrchestrationMeters.java` and `main/shortener/ShortenerMeters.java`
  - Req: NFR-OBS-02, FR-AUD-05 · Scn: — · ADR: ADR-012 · Deps: T041 · Out: telemetry · TDD: Red→Green · Val: `mvnw test -Dtest=MetricsTest` · Doc: docs/architecture/overview.md · Trace: tags NFR-OBS-02 · Risk: high-cardinality tags. Guardrail: no run id as a meter tag · Done: default · Appr: default
- [ ] T107 [P] [US7] Add verification test `test/orchestration/RunReconstructionTest.java` (a completed run's states, attempts, decisions, and plan versions rebuilt from persisted evidence only match the live view)
  - Req: NFR-OBS-01 · Scn: SCN-A · ADR: ADR-012 · Deps: T101 · Out: verification test · TDD: Verification · Val: `mvnw test -Dtest=RunReconstructionTest` · Doc: — · Trace: tags NFR-OBS-01 · Risk: — · Done: default · Appr: default
- [ ] T132 [US7] Implement the governance-invariant checker `test/support/GovernanceInvariants.java` (from a run's audit trail and stage attempts: no stage downstream of a gate started before that gate's valid decision; no `RELEASE` after a mandatory `FAIL` without an approved, unexpired exception; no gate `SUCCEEDED` without a valid human decision). First prove it detects violations in `test/e2e/GovernanceInvariantsTest.java` using deliberately violating event sequences, then call it at the end of every scenario and drill end-to-end test (T057, T080, T089, T098)
  - Req: SC-004, FR-GOV-02, FR-GOV-03, FR-POL-03 · Scn: SCN-A, SCN-B, SCN-C, RDR-01..RDR-07 · ADR: ADR-008, ADR-019 · Deps: T097, T080 · Out: invariant checks on every end-to-end run · TDD: Red→Green (checker proven against violations first) · Val: `mvnw test -Dtest="GovernanceInvariantsTest,*E2ETest"` · Doc: docs/architecture/governance.md (invariants) · Trace: tags SC-004 · Risk: checker too lenient. Guardrail: negative fixtures must fail it · Done: default · Appr: default

### Traceability

- [ ] T108 [US7] Implement `test/traceability/TraceabilityMatrixTest.java`: parse `feature/spec.md` for every FR, NFR, SCN, and RDR id; scan `src/test/java` for `@Tag` values; fail on any FR or SCN without a test (orphan requirement); fail on any test class without a requirement-, scenario-, or drill-tag (orphan test); parse `feature/tasks.md` and fail on any task without a `Req` field (orphan task); write `target/traceability/requirements-to-tests.md`
  - Req: SC-003, Constitution X · Scn: all · ADR: ADR-013 · Deps: T097, T132 · Out: executable traceability · TDD: Verification · Val: `mvnw test -Dtest=TraceabilityMatrixTest` · Doc: docs/traceability/requirements-to-tests.md (T119) · Trace: generates the matrix · Risk: tag typos. Guardrail: unknown-id detection · Done: default · Appr: default
- [ ] T109 [US7] Add `scripts/export-evidence.ps1` and `scripts/export-evidence.sh` (copy `target/evidence/**` verbatim into `docs/scenarios/evidence/`; never hand-edited — review RC-4) and write `docs/scenarios/README.md`: evidence index mapping every evidence type (requirement-to-test traceability, architecture decisions, approvals, policy versions, compliance evaluations, failed-policy blocking, approved exception, compensating control, change request and impact analysis, state transitions, sequential path, parallel path and synchronization, retry, compensation or rollback, safe-stop, resume, replanning, measured metrics) to files and regeneration commands
  - Req: SC-009 · Scn: SCN-A, SCN-B, SCN-C, RDR-* · ADR: — · Deps: T105, T108 · Out: evidence index · TDD: N/A · Val: every entry resolves to a committed file · Doc: new doc · Trace: evidence ids · Risk: — · Done: default · Appr: default

**Checkpoint (sync)**: evidence suites green; traceability complete.

---

## Phase 10: Polish & Cross-Cutting Concerns

**Purpose**: API/schema deliverables completion, quality gates, documentation, convergence, and
release readiness.

### API and schema deliverables

- [ ] T110 [P] Add `test/contract/ContractDriftTest.java` (every controller mapping appears in `openapi.yaml` and every contract path is implemented) and complete `test/contract/WorkflowApiContractTest.java` for governance, operations, and evidence responses
  - Req: NFR-CHG-01 · Scn: — · ADR: ADR-013 · Deps: T101 · Out: contract drift test · TDD: Verification · Val: `mvnw test -Dtest=ContractDriftTest,WorkflowApiContractTest` · Doc: — · Trace: tags NFR-CHG-01 · Risk: — · Done: default · Appr: default
- [ ] T111 [P] Add `test/contract/ArtifactSchemaTest.java`: every artifact produced by the SCN-A, SCN-B, and SCN-C runs validates against `feature/contracts/schemas/*.schema.json`
  - Req: FR-ORC-10, NFR-CHG-01 · Scn: SCN-A, SCN-B, SCN-C · ADR: ADR-013 · Deps: T097 · Out: schema validation · TDD: Verification · Val: `mvnw test -Dtest=ArtifactSchemaTest` · Doc: — · Trace: tags FR-ORC-10 · Risk: schema drift. Guardrail: failing test names the artifact · Done: default · Appr: default
- [ ] T112 Write the contract compatibility review `docs/api/compatibility-review.md` (1.0.0 → 1.1.0 → 1.2.0 changes classified, consumers, examples, migrations V3/V4, rollback) and confirm `feature/contracts/CHANGELOG.md` matches; **change-control approval** of contract versions 1.1.0 and 1.2.0 by the candidate
  - Req: NFR-CHG-01, NFR-CHG-02 · Scn: SCN-A, SCN-B · ADR: ADR-013 · Deps: T110, T111 · Out: compatibility review · TDD: N/A · Val: review document complete · Doc: new doc · Trace: — · Risk: — · Done: document committed · Appr: **REQUIRED — PENDING (candidate change-control approval)**

### Quality gates

- [ ] T113 [P] Add `test/performance/PerformanceMeasurementTest.java` (20 concurrent clients: redirect and creation p95 measured; generous regression bound) and write `docs/assessment/performance.md` (labeled demonstration measurement, machine, load profile)
  - Req: NFR-PRF-01, NFR-PRF-02 · Scn: — · ADR: ADR-016 · Deps: T031 · Out: measurement · TDD: Verification · Val: `mvnw test -Dtest=PerformanceMeasurementTest` · Doc: new doc · Trace: tags NFR-PRF-01 · Risk: flaky timing. Guardrail: only a generous bound is asserted · Done: default · Appr: default
- [ ] T114 [P] Add `test/orchestration/ExtensibilityTest.java` (a new policy rule bean plus YAML entry is evaluated without scheduling-core changes; the coordinator runs arbitrary scripted stage graphs)
  - Req: NFR-MNT-02 · Scn: — · ADR: ADR-005, ADR-019 · Deps: T052 · Out: verification test · TDD: Verification · Val: `mvnw test -Dtest=ExtensibilityTest` · Doc: — · Trace: tags NFR-MNT-02 · Risk: — · Done: default · Appr: default
- [ ] T115 Enforce the JaCoCo check (≥ 80% line coverage for `shortener.domain`, `shortener.service`, `orchestration.engine`, `orchestration.planning`, `orchestration.governance`, `orchestration.reliability`, `orchestration.policy`) in `pom.xml`; close gaps with tests
  - Req: NFR-TST-01 · Scn: — · ADR: ADR-013 · Deps: T114 · Out: coverage gate · TDD: Verification · Val: `mvnw verify` (JaCoCo check passes); report `target/site/jacoco/index.html` · Doc: testing doc (T117) · Trace: tags NFR-TST-01 · Risk: gaming coverage. Guardrail: behavior-focused tests only · Done: default · Appr: default

### Documentation

- [ ] T116 [P] Write `README.md` (purpose, quick start, architecture summary, scenario commands, evidence map, gates status), `SECURITY.md` (threat model summary, demo-credential notice, residual risks, reporting), and `CONTRIBUTING.md` (SpecKit workflow, TDD evidence, commit conventions, gate register)
  - Req: CON-02, Constitution X · Scn: — · ADR: ADR-014, ADR-015 · Deps: T109 · Out: root docs · TDD: N/A · Val: every command in the README executed in T120 · Doc: new docs · Trace: — · Risk: doc-behavior drift; overclaiming "agentic" as LLM reasoning. Guardrail: agents described as deterministic, knowledge-driven workers (review RC-7) · Done: default · Appr: default
- [ ] T117 [P] Write `docs/architecture/overview.md` (components, orchestration model, control flow, key decisions with ADR links, stateless request tier and horizontal-scaling path) and `docs/assessment/testing-limitations-tradeoffs.md` (testing approach, executed suites, limitations, trade-offs)
  - Req: A§5 deliverables, NFR-SCA-01 · Scn: — · ADR: all · Deps: T109 · Out: docs · TDD: N/A · Val: review · Doc: new docs · Trace: — · Risk: — · Done: default · Appr: default
- [ ] T118 [P] Write `docs/assessment/risk-register.md` (risks with likelihood, impact, owner, mitigation, residual level; limitations; residual risks, including unthrottled failed-authentication attempts from analysis finding L7)
  - Req: Constitution XI · Scn: — · ADR: all · Deps: T109 · Out: risk register · TDD: N/A · Val: every ADR risk and plan threat represented · Doc: new doc · Trace: — · Risk: — · Done: default · Appr: default
- [ ] T119 Refresh `docs/traceability/requirements-to-tests.md` from `target/traceability/requirements-to-tests.md` and add `docs/traceability/adr-evidence.md` (each ADR's validation section mapped to executed tests and evidence)
  - Req: SC-003 · Scn: all · ADR: all · Deps: T108 · Out: committed matrices · TDD: N/A · Val: matrix regenerated by `mvnw test -Dtest=TraceabilityMatrixTest` matches the committed copy · Doc: new docs · Trace: matrices · Risk: stale copy · Done: default · Appr: default

### Release-readiness security checks (constitution V)

- [ ] T127 [P] Test first in `test/security/RepositorySecretScanTest.java`: scan every git-tracked text file for private-key blocks, cloud access keys, bearer tokens, and `password=` or `secret=` values; allow-list only the labeled demo tokens and their SHA-256 hashes; prove detection with planted fixtures under `src/test/resources/secret-scan-fixtures/` (excluded from the real scan); fail on any finding
  - Req: NFR-SEC-03, Constitution V · Scn: — · ADR: ADR-015 · Deps: T116 · Out: executable secret scan · TDD: Red→Green · Val: `mvnw test -Dtest=RepositorySecretScanTest` · Doc: docs/assessment/security-scans.md · Trace: tags NFR-SEC-03 · Risk: false negatives. Guardrail: fixture per pattern · Done: default · Appr: default
- [ ] T128 [P] Run a dependency vulnerability scan: OSV-Scanner (pinned release) against `target/classes/META-INF/sbom/application.cdx.json`; record every finding with severity, affected component, and disposition (upgrade, not reachable, accepted) in `docs/assessment/security-scans.md`. If the scanner cannot run in this environment, record the gap as a release limitation that needs the candidate's exception at G6
  - Req: NFR-SEC-05, Constitution V · Scn: — · ADR: ADR-002, ADR-019 · Deps: T115 · Out: scan report · TDD: N/A · Val: scan command and its actual output recorded · Doc: security-scans doc · Trace: — · Risk: known vulnerable dependency shipped. Guardrail: HIGH/CRITICAL findings block T122 until dispositioned · Done: report committed · Appr: an accepted finding needs the candidate's exception (PENDING if any)

### Validation, convergence, release readiness

- [ ] T120 Validate the quickstart from a clean clone: clone into a scratch directory, run `quickstart.md` §2–§6 exactly, and record commands, durations, and outcomes in `docs/assessment/quickstart-validation.md`
  - Req: SC-001 · Scn: SCN-A · ADR: ADR-014 · Deps: T116 · Out: validation record · TDD: N/A · Val: record with actual outputs · Doc: new doc · Trace: — · Risk: environment-specific success · Done: default · Appr: default
- [ ] T121 Run `/speckit-converge` and the full quality suite (`mvnw clean verify`); write `docs/assessment/convergence-report.md` (requirement verification, architecture drift check, orchestration evidence, scenarios, test results including flaky or order-dependent checks, documentation, compliance and change control, evidence integrity)
  - Req: all · Scn: all · ADR: all · Deps: T112, T115, T119, T120, T127, T128 · Out: convergence report · TDD: N/A · Val: suite green; report complete · Doc: new doc · Trace: — · Risk: unsupported claims. Guardrail: every claim cites evidence · Done: default · Appr: default
- [ ] T122 Write the release-readiness proposal in `docs/assessment/release-readiness.md` (one of READY / READY WITH ACCEPTED LIMITATIONS / NOT READY; mandatory-validation status; policy and change-control results; secret-scan and dependency-scan results from T127/T128; pending human gates, which keep it `NOT READY` until ratified)
  - Req: FR-RDY-01, Constitution Governance · Scn: — · ADR: — · Deps: T121 · Out: readiness proposal · TDD: N/A · Val: consistency with the gate register · Doc: new doc · Trace: — · Risk: premature READY. Guardrail: constitution release-blocking rules · Done: default · Appr: decided in T125
- [ ] T123 Write the final engineering summary `docs/assessment/final-engineering-summary.md` using the mandatory 21-section schema in order, each material claim citing a path, identifier, command, and observed result; agents described as deterministic, knowledge-driven workers (review RC-7); findings of the pre-implementation review and their disposition included
  - Req: A§4.8, A§5 · Scn: all · ADR: all · Deps: T122 · Out: final summary · TDD: N/A · Val: 21 sections present in order; claims cite evidence · Doc: new doc · Trace: — · Risk: — · Done: default · Appr: default
- [ ] T124 Write the reviewer navigation guide `docs/assessment/reviewer-guide.md` covering the 25 navigation items (path, command, expected result, requirement or scenario id for each)
  - Req: A§5, SC-009 · Scn: all · ADR: — · Deps: T123 · Out: reviewer guide · TDD: N/A · Val: every command executed in T120/T121 · Doc: new doc · Trace: — · Risk: — · Done: default · Appr: default
- [ ] T125 [GATE] Human checkpoint G6: the candidate decides release readiness in `docs/governance/human-gate-register.md` based on T121–T124
  - Req: Constitution III, FR-RDY-01 · Scn: — · ADR: — · Deps: T124, T004 · Out: candidate decision · TDD: N/A · Val: decision recorded with name and date · Doc: gate register · Trace: — · Risk: — · Done: **only the candidate may check this task** · Appr: **REQUIRED — PENDING (candidate)**
- [ ] T126 [GATE] Human checkpoint G7: the candidate verifies the clean tree and the reviewer instructions from a fresh checkout, then tags `assessment-submission-v1.0`
  - Req: Constitution III · Scn: — · ADR: — · Deps: T125 · Out: tag · TDD: N/A · Val: `git show assessment-submission-v1.0` · Doc: — · Trace: — · Risk: — · Done: **only the candidate may check this task** · Appr: **REQUIRED — PENDING (candidate)**

---

## Dependencies & Execution Order

### Phase dependencies

- **Setup (Phase 1)**: T001 → T002 → T003; T004 is the human gate (PENDING; provisional progression).
- **Foundational (Phase 2)**: depends on T002/T005; blocks every story.
- **US1 (Phase 3)**: depends on Foundational only.
- **US2 (Phase 4)**: depends on Foundational; reuses US1's creation service (T022) for capabilities.
- **US3 (Phase 5)**: depends on US2's basic gates (T042).
- **US4 (Phase 6)**: depends on US2's engine (T041) and agents (T053); T076 also depends on US3 (T071 chain).
- **US5 (Phase 7)**: depends on US2 (engine, agents, probes) and US4 (fallback T069).
- **US6 (Phase 8)**: depends on US3 (T064) and US2.
- **US7 (Phase 9)**: depends on US2's API (T056) and US4's recovery (T073) for MTTR.
- **Polish (Phase 10)**: depends on all stories.

### Critical path

T002 → T005 → T007 → T012 → T036 → T041 → T042 → T048 → T049 → T053 → T057 → T064 → T068 →
T070 → T071 → T080 → T086 → T089 → T088 → T094 → T098 → T097 → T132 → T108 → T127/T128 → T121 →
T123 → T125.

### Synchronization points

Phase checkpoints (after T016, T033, T058, T066, T082, T090, T100, T109) and the convergence run
(T121). Tasks touching the same critical files (`RunCoordinator`, `GateService`,
`LinkCreationService`, `RedirectService`, `GovernanceController`, `pom.xml`) are never marked [P].

### Architecture-sensitive tasks blocked by ADR approval (G4, T004)

Every task citing an ADR formally depends on that ADR's acceptance. Under Delegated Provisional
Progression these tasks proceed provisionally; if the candidate rejects an ADR, every task citing it
is reopened and re-planned through `/speckit-plan` → `/speckit-tasks` → `/speckit-analyze`.

### Release readiness blocked by mandatory validation

T122 depends on T121 (full suite, convergence); T125 (G6) depends on every mandatory validation
task and on T004.

---

## Parallel Execution Examples

```text
# Foundational, after T005:
T008 (time/json utilities) ‖ T010 (correlation filter) ‖ T013 (architecture rules) ‖ T015 (test support)

# US1, after T016:
T017 (URL policy tests) ‖ T019 (code generator) ‖ T026 (rate limiter)

# US2 knowledge, after T041:
T044 (capability catalog) ‖ T045 (ambiguity lexicon)

# US3 red tests, after T042:
T059 ‖ T060 ‖ T061 ‖ T062 ‖ T063

# US4, after T068:
T074 (budget) ‖ T075 (fault injection)

# Polish, after the stories:
T110 ‖ T111 ‖ T113 ‖ T114 ‖ T116 ‖ T117 ‖ T118
```

---

## Evidence Task Index

| Evidence required by the guide | Producing tasks |
|--------------------------------|-----------------|
| Requirement-to-test traceability | T108, T119 |
| Architecture-decision evidence | T119 (`adr-evidence.md`), ADR validation tests |
| Approval evidence | T057, T066 |
| Policy-version evidence | T052, T057 (`policy_set_version` on runs and evaluations) |
| Compliance evaluation evidence | T057, T089, T098 exports |
| Failed-policy blocking evidence | T076, T080 (RDR-06) |
| Approved exception evidence | T080 (RDR-06) |
| Compensating-control evidence | T080 (RDR-06 exception record) |
| Change-request and impact-analysis evidence | T095, T086 |
| State-transition evidence | T057/T089/T098 audit exports |
| Sequential-path evidence | T057 timeline |
| Parallel-path and synchronization evidence | T040, T057 timeline overlap |
| Retry evidence | T067, T080 (RDR-01) |
| Compensation or rollback evidence | T070, T080 (RDR-03) |
| Safe-stop evidence | T071, T080 (RDR-03/04/07) |
| Resume evidence | T073 (RDR-05) |
| Replanning evidence | T093, T098 |
| Measured demonstration metrics | T105, T113 |
| Limitations | T117 |
| Residual risks | T118 |
| Reviewer navigation | T124 |
| Governance invariants over every end-to-end run (SC-004) | T132 |
| Repository secret scan and dependency vulnerability scan (constitution V) | T127, T128 |
| Refusal of an undelivered capability (red scenario runs) | T089, T098 |

---

## Implementation Strategy

### MVP first (User Story 1)

1. Phase 1 → Phase 2 (foundation, walking skeleton).
2. Phase 3 (US1) → **stop and validate** (quickstart §4) → CP1.

### Incremental delivery

1. US2: the orchestration engine with SCN-A (the assessment differentiator).
2. US3: governance hardening.
3. US4: reliability controls with drills → CP2.
4. US5: brownfield (impact gate **before** click-limit code).
5. US6: ambiguity and replanning → CP3.
6. US7: evidence; then Polish, convergence, release-readiness proposal, and the human gates.

### Task-group boundary (constitution)

One coherent task group, plus its tests, documentation, and traceability, forms one autonomous
unit; each ends with a checkpoint review and a commit.

## Notes

- `[P]` tasks touch different files and have no incomplete dependencies.
- Red runs are recorded in `docs/assessment/tdd-evidence.md`; implementation-first work is never
  described as TDD.
- `[GATE]` tasks are checked only by the candidate.
