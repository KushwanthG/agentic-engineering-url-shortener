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
