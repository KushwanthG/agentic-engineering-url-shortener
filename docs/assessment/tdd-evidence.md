# TDD Evidence Log

Red-green-refactor evidence required by constitution Principle IV and the task definition of done.
Each entry records the command that was actually executed and a short summary of the actual
result, including the reason the red run failed. `Verification` tests (written after the behavior
they check) are listed separately and never presented as TDD.

**Environment**: Windows 11, JDK 21.0.12.1 (`JAVA_HOME` set per command), Maven via the
project wrapper once it exists (task T003).

| Task | Test(s) | Red run (command → observed failure) | Green run (command → result) |
|------|---------|--------------------------------------|------------------------------|
| T007 | `MigrationTest` (6) | `mvnw test -Dtest=MigrationTest` → context failed: `Schema validation: missing table [audit_chain_head]` (no migrations existed) | same command after V1/V2 → 5/6 passed; the 6th failed because the test's own query did not exclude Flyway's null-version schema-creation row (test defect, fixed) → 6/6 passed |
| T008 | `CanonicalJsonTest` (6) | `mvnw test` (Phase 2 red run) → 5 failures: stub returned input unchanged / empty fingerprint | `mvnw test` → 6/6 passed |
| T009 | `ProblemDetailsHandlerTest` (6) | Phase 2 red run → 2 failures, 4 errors: exceptions propagated as `ServletException` (no advice) | `mvnw test` → 6/6 passed |
| T010 | `CorrelationIdFilterTest` (4) | Phase 2 red run → 3 failures: `Response header 'X-Correlation-Id' expected:<client-abc-12345> but was:<null>` | `mvnw test` → 4/4 passed |
| T011 | `SecurityMatrixTest` (12), `TokenNotLoggedTest` (1) | Phase 2 red run → 8 + 1 failures: permit-all stub answered 404 where 401/403 were expected; no "Rejected bearer token" log line | first green run: 11/12 — `/actuator/prometheus` with the auditor token returned 404 because Spring Boot disables metrics export in tests; positive check moved to `/actuator/metrics` (security behavior unchanged) → 12/12 and 1/1 passed |
| T012 | `AuditChainTest` (4), `AuditTamperDetectionTest` (5) | Phase 2 red run → 9 errors: `UnsupportedOperationException: T012 not implemented` | `mvnw test` → 4/4 and 5/5 passed (80 concurrent appends contiguous and valid; modification, deletion, truncation, insertion, reordering detected) |
| T014 | `ContractHarnessSpikeTest` (1) | Phase 2 red run → `Status expected:<401> but was:<404>` (security stub) | `mvnw test` → 1/1 passed; openapi-request-validator-core 3.0.0 works (API: `OpenApiInteractionValidator.createForSpecificationUrl`, `validateResponse`) — research R-19 fallback not needed |
| T016 | `WalkingSkeletonIT` (4) | Phase 2 red run → 2 failures (401 expected, 404 returned) | `mvnw verify` → 54/54 tests passed, BUILD SUCCESS, jar repackaged, JaCoCo report written |

**Verification tests (not TDD)**: T013 `ArchitectureTest` (5) — passed on the first run with
`allowEmptyShould(true)` (rules precede the code they constrain).

## Phase 3 — US1 URL shortener core (T017–T032)

All Phase 3 tests were written before any production class of the shortener existed.

**Red run** (2026-09-26): `mvnw -B -ntp test-compile` → compilation failed, 200 errors; the tests
referenced 15 types that did not exist yet (`UrlPolicy`, `ShortCodeGenerator`,
`SecureRandomShortCodeGenerator`, `ShortLink`, `ClickEvent`, `LinkStatus`, `ShortLinkRepository`,
`ClickEventRepository`, `LinkCreationService`, `CreateLinkCommand`, `CreatedLink`, `LinkView`,
`RedirectService`, `ClickRecorder`, `Resolution`). This is a compile-level red for the whole group:
it proves the tests preceded the code, not that each assertion failed individually.

| Task | Test(s) | Green run (command → result) |
|------|---------|------------------------------|
| T017/T018 | `UrlPolicyTest` (43: 36-entry catalog + normalization) | first `mvnw verify` → 43/43 passed |
| T019 | `ShortCodeGeneratorTest` (4) | first run → 4/4 |
| T020 | `ShortLinkRepositoryTest` (3) | first run → 2/3: **defect found** — a click at 23:59 UTC was counted on the next day because H2 converted timestamps to dates in the JVM zone (+05:30). Fix: H2 session `TIME ZONE=UTC` in both JDBC URLs → 3/3 |
| T021/T022 | `LinkCreationServiceTest` (7) | first run → 7/7 |
| T023 | `IdempotencyServiceTest` (6, incl. 10 concurrent identical requests) | first run → 6/6 |
| T024/T025 | `RedirectServiceTest` (5, incl. fail-open with spied `ClickRecorder`) | first run → 5/5 |
| T026 | `TokenBucketRateLimiterTest` (4) | first run → 4/4 |
| T027/T028 | `LinkControllerTest` (8), `RateLimitHttpTest` (2) | first run → 8/8, 2/2 |
| T029 | `RedirectControllerTest` (4); `ArchitectureTest` switched to `allowEmptyShould(false)` | first run → 4/4, 5/5 |
| T030 | `StoreUnavailableTest` (3) | first run → 3/3 |

**Regression found during the Phase 3 runs (pre-existing Phase 2 defect)**: `AuditChainTest.concurrentAppendsKeepTheChainContiguousAndValid`
failed intermittently in full-suite runs (1 of 4 runs, then 1 of 3) with a duplicate
`(chain_id, seq = 1)`. It had passed in every Phase 2 run. Investigation (all runs executed):

1. Re-reading the chain head after `SELECT … FOR UPDATE` → still failed in a full run (hypothesis rejected).
2. Sequence allocation rewritten as one atomic `UPDATE … SET last_seq = last_seq + 1` → still failed (1 of 3 full runs).
3. Temporary stress diagnostic (40 rounds × 8 threads × 10 appends, not committed): 7 failed appenders
   with concurrently created chain heads, 0 with a pre-created head → the defect needs racing head
   creation. Moving creation into its own transaction alone → still 7–8 failures per 40 rounds.
4. Serializing head creation in the process (so no duplicate head insert is ever rolled back) → 0
   failures in 2 × 40 rounds. Root cause: with H2, rolling back a losing duplicate insert of the head
   could reset the winning head, handing out sequence 1 twice.

The test now runs 10 times per build (`@RepeatedTest(10)`, fresh chain each time) as a regression guard.

**Final green**: `mvnw -B -ntp clean verify` executed 3 times consecutively → each run 158 tests,
0 failures, 0 errors, BUILD SUCCESS (~55 s).

**Verification tests (written before the code, classified as verification by the task list)**:
T031 `RedirectConcurrencyTest` (200 concurrent resolutions → count 200 = redirects 200 = click events 200),
`UrlSecurityCatalogIT` (36 catalog entries through HTTP, all 400 with the expected code, nothing stored);
T032 `LinkApiContractTest` (201, replayed 201, 302, 200 metadata/stats, 400, 401, 403, 404, 409, 410,
422, 429, 503 validated against `openapi.yaml`) — all passed on the first run.

**Live smoke test** (demo profile, packaged jar, port 18080): `POST /api/v1/links` → 201 with
`Location`; `GET /{code}` → 302, `Location` = target, `Cache-Control: no-store`; stats after 3
redirects → `totalClicks` 3, `daily` `[{"date":"2026-09-26","clicks":3}]`; metadata-service URL
`http://169.254.169.254/...` → 400; unknown code → 404 problem JSON with `correlationId`; readiness UP.

## Phase 4 — US2 orchestration core and SCN-A (T034–T058, T129–T131)

Tests were written in groups before the production classes of each group. Red runs are
**compile-level** unless stated otherwise: they prove the tests came before the code, not that each
assertion failed on its own. All times are 2026-09-26 UTC. Red and green outcomes were extracted
from the session's recorded tool output. No outcome in this table is reconstructed from memory.

| Tasks | Test(s) | Red run (command → observed failure) | Green run (command → result) |
|-------|---------|--------------------------------------|------------------------------|
| T034–T042 | `StageTransitionsTest` (37), `RunTransitionsTest` (20), `OrchestrationPersistenceTest` (3), `PlanFactoryTest` (6), `PlanValidatorTest` (7), `AgentPermissionTest` (3), `RunCoordinatorTest` (6), `GateDecisionBasicsTest` (6) | 12:44 `mvnw -o test-compile` → exit 1, 200 errors, 29 missing types (`StageStatus`, `RunStatus`, `StageNode`, `PlanValidator`, `StageAgent`, `GateService`, `WorkflowService`, …) | 12:58 `mvnw test -Dtest=StageTransitionsTest,RunTransitionsTest,OrchestrationPersistenceTest,PlanFactoryTest,PlanValidatorTest,AgentPermissionTest,RunCoordinatorTest` → 82/82; 13:00 `-Dtest=GateDecisionBasicsTest,RunCoordinatorTest` → 12/12 |
| T043 | `ApplicationPlaneAdapterTest` (5), `PreviewIsolationTest` (4) | 13:02 `mvnw -o test-compile` → exit 1: `withPreview(Capability, Map, …)` and `parameters(Capability)` not found | 13:04 `-Dtest=ApplicationPlaneAdapterTest,PreviewIsolationTest,ArchitectureTest,LinkCreationServiceTest` → 21/21 |
| T044–T045 | `CapabilityCatalogTest` (6), `AmbiguityLexiconTest` (3) | 13:08 first run → 6 errors: YAML `Parser while parsing a flow mapping` (unquoted values in the catalog); 13:08 second run → 6 errors: `Cannot map null into type boolean` (primitive field, catalog entry without the key) | 13:09 same command → 9/9 (values quoted; field made `Boolean`) |
| T046–T047 | `RequirementAnalysisAgentTest` (6) | 13:11 `mvnw -o test-compile` → exit 1, missing `RequirementAnalysisAgent`, `RequirementIngestionAgent` | 13:12 `-Dtest=RequirementAnalysisAgentTest` → 6/6 |
| T048, T129, T130 | `DecompositionAgentTest` (2), `ThreatAssessmentAgentTest` (2), `DesignAgentTest` (2), `ImplementationAgentTest` (3) | 13:14 `mvnw -o test-compile` → exit 1, missing `DecompositionAgent`, `DesignAgent`, `ImplementationAgent`, `ThreatAssessmentAgent` | 13:16 `-Dtest=DecompositionAgentTest,ThreatAssessmentAgentTest,DesignAgentTest,ImplementationAgentTest,AgentPermissionTest` → 12/12 |
| T054–T055 | `AliasPolicyTest` (14), `LinkCreationAliasTest` (6); regression `LinkCreationServiceTest`, `LinkControllerTest`, `MigrationTest`, `IdempotencyServiceTest`, `LinkApiContractTest` | 13:17 `mvnw -o test-compile` → exit 1, missing `AliasPolicy`, `isCustomAlias()` | 13:18 `-Dtest=AliasPolicyTest,LinkCreationAliasTest,LinkCreationServiceTest,LinkControllerTest,MigrationTest,IdempotencyServiceTest` → 47/47; 13:19 `-Dtest=LinkApiContractTest` → 5/5 |
| T049 | `TestingAgentTest` (2), `SecurityVerificationAgentTest` (1) | 13:21 `mvnw -o test-compile` → exit 1, package `orchestration.agent.probes` does not exist; missing `ProbeRegistry`, `TestingAgent`, `SecurityVerificationAgent` | 13:23 first run → 2 failures: report schema violation `required property 'description' not found` (probe results lacked it); 13:24 → 3/3 |
| T050 | `DocumentationAgentTest` (2), `ValidationAgentTest` (2) | 16:23 `mvnw -o test-compile` → exit 1, missing `DocumentationAgent`, `ValidationAgent` | 16:24 first run → 2 failures (`DocumentationAgentTest...:36`, `ValidationAgentTest...:57`: `Expecting value to be true but was false`; the documentation fix was path normalization of repository doc anchors); 16:24 → 4/4 |
| T051–T052 | `PolicyEngineTest` (9), `PolicyRulesTest` (12) | 16:27 `mvnw -o test-compile` → exit 1, missing `PolicyEngine`, `PolicyRule`, `ReadinessEvaluator` and 14 rule classes | 16:29 `-Dtest=PolicyEngineTest,PolicyRulesTest` → 21/21 |
| T131 | `ComplianceEvaluationAgentTest` (2) | 16:31 `mvnw -o test-compile` → exit 1, missing `ComplianceEvaluationAgent`, `RunFacts` | 16:31 `-Dtest=ComplianceEvaluationAgentTest` → 2/2 |
| T053 | `ReleaseAgentTest` (3), `FinalSummaryAgentTest` (1) | 16:33 `mvnw -o test-compile` → exit 1, missing `ReleaseAgent`, `FinalSummaryAgent`, `DecisionSummary` | 16:34 `-Dtest=ReleaseAgentTest,FinalSummaryAgentTest,ComplianceEvaluationAgentTest,AgentPermissionTest` → 9/9 |
| T056 | `WorkflowApiTest` (5), `WorkflowApiContractTest` (1) | 16:42 `mvnw test -Dtest=WorkflowApiTest,WorkflowApiContractTest` → 5 failures: `Status expected:<201> but was:<404>`, `RUN_NOT_FOUND` expected but `RESOURCE_NOT_FOUND` (no controller) | 16:49 first run → 11/12: contract validator rejected `ArtifactDetail` (see note 2); 16:50 `-Dtest=*ContractTest,WorkflowApiTest,GateDecisionBasicsTest` → 17/17 |
| T057 | `ScenarioAGreenfieldE2ETest` (1) | 16:53 `mvnw test -Dtest=ScenarioAGreenfieldE2ETest` → **behavioral red**: timeout waiting for `RELEASE_APPROVAL`; the run failed in `TESTING`/`SECURITY_VERIFICATION` with a unique-key violation on `in-use-<run>` (see note 3) | 16:58 `-Dtest=ScenarioAGreenfieldE2ETest,SyntheticScopeTest` → 3/3 (run `COMPLETED`, `READY`; E-A1..E-A9 exported) |
| T057 (fix) | `SyntheticScopeTest` (2) | 16:56 `mvnw test -Dtest=SyntheticScopeTest` → compile error, `SyntheticScope` not found | 16:57 → 2/2 |

**Notes**

1. **Full suite, 16:35**: `mvnw -o clean verify` → 334 tests, 2 failures, 1 error
   (`PolicyRulesTest.lic001PassesForTheBuildsRealSbom`: no SBOM; `ComplianceEvaluationAgentTest`
   ×2). Diagnosis (16:37 `mvnw -o generate-resources`): `makeAggregateBom requires online mode …
   skipping`. Offline builds do not produce the SBOM that LIC-001 evaluates. This was an
   environment defect, not a code defect. 16:38 `mvnw clean verify` (online) → 334/334, BUILD
   SUCCESS. Full builds must run online.
2. **Contract harness**: the Atlassian validator adds `additionalProperties: false` to each
   sub-schema, so `ArtifactDetail` (`allOf` = `ArtifactSummary` + detail fields) rejected every
   property. Fix in the test harness only (`withResolveCombinators(true)` merges `allOf` before
   validating). `openapi.yaml` is unchanged.
3. **Defect found by SCN-A**: `TESTING` and `SECURITY_VERIFICATION` are dispatched in the same
   scheduling cycle. Both create run-scoped synthetic aliases, and both clean up by run (ADR-010),
   so they collided on `in-use-<run>`. One stage's cleanup could also delete the other stage's
   in-flight probe data. The unit tests of each agent could not show this because they run one
   stage alone. Fix: `SyntheticScope` makes the probe-and-cleanup section exclusive per run. Both
   stages still dispatch in one cycle (asserted in SCN-A), and ADR-010 and the data model are
   unchanged.

**Final green**: 16:59 `mvnw -B -ntp verify` (online) → 343 tests, 0 failures, 0 errors, SBOM
generated (104 components), BUILD SUCCESS.

## Phase 5 — US3 govern high-impact decisions (T059–T066)

| Tasks | Test(s) | Red run (command → observed failure) | Green run (command → result) |
|-------|---------|--------------------------------------|------------------------------|
| T059–T063 | `SeparationOfDutiesTest` (3), `ApprovalBindingTest` (2), `GateDeadlineTest` (3), `ConcurrentGateDecisionTest` (1), `GateRejectionTest` (2), `WaitingGateIndependenceTest` (1) | 17:16 `mvnw test-compile` → compile error, `DeadlineSweeper` missing; an empty skeleton was added so that the red is behavioral. 17:17 `mvnw test -Dtest=SeparationOfDutiesTest,ApprovalBindingTest,GateDeadlineTest,ConcurrentGateDecisionTest,GateRejectionTest,WaitingGateIndependenceTest` → 12 tests, 8 failures: `Status expected:<403> but was:<200>` (requester approved own run), `expected:<409> but was:<200>` (late decision accepted), no invalidation on a changed DESIGN, both racers got a result other than `CONCURRENT_DECISION`, no escalation, IMPLEMENTATION not cancelled on rejection | 17:21 first run (with `GateDecisionBasicsTest`, `RunCoordinatorTest`, `WorkflowApiTest`) → 26/29: rejection never reached `REJECTED` (see note 1). 17:23 → 29/29 |
| T065 | `ArchitectureTest` (+3 rules), `GovernanceSecurityMatrixTest` (34) — verification | — | 17:25 → 40/41: the new rule flagged `ComplianceEvaluationAgent` → `PolicySetLoader.current()`, a read of the pinned policy set (rule too broad, see note 2). 17:26 → 8/8 |

**Verification, not TDD**: `WaitingGateIndependenceTest` passed in the red run. The scheduler has
blocked only the dependents of a waiting gate since T041, so this test documents existing behavior.

**Notes**

1. **Defect found by `GateRejectionTest`**: `ShortLinkRepository.deleteBySyntheticRunId` is
   `@Modifying(clearAutomatically = true)`. When compensation ran inside the rejection transaction,
   the persistence context was flushed and cleared, and the later `REJECTED` change to the (now
   detached) run was silently lost. Fix: re-load the run after compensation. The pitfall is
   documented on `CompensationCoordinator.compensate`.
2. **Rule correction**: the planned rule "agents must not depend on policy mutation" was first
   written as "no dependency on `PolicySetLoader`". That class has only `current()`. The rule became
   two rules: agents cannot reach governance, security, or network classes; and `PolicySetLoader`
   exposes no public mutators.

**Final green**: 17:27 `mvnw -B -ntp verify` (online) → 392 tests, 0 failures, 0 errors, SBOM
generated, BUILD SUCCESS.
