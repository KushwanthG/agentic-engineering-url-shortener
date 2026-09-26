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
