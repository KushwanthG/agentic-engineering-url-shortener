# ADR-013: Layered, Contract-First Test Strategy with Executable Traceability

## Status

Proposed (2026-09-26) — pending acceptance at Gate G4.

## Context

The constitution requires red-green-refactor TDD and unit, integration, API contract,
orchestration transition, reliability, security, and end-to-end tests (IV), and task completion
requires executed validation (XI). Every requirement must map to executed tests (SC-003).

## Decision Drivers

- Fast feedback during TDD
- Real confidence in asynchronous orchestration
- Contract conformance proven by execution, not by assertion
- Traceability that cannot silently rot

## Options Considered

| Option | Advantages | Disadvantages / risks |
|--------|------------|-----------------------|
| **Layered pyramid: domain unit → slices → `@SpringBootTest` integration → HTTP end-to-end; contract validation of recorded responses; ArchUnit; `@Tag`-based traceability check** | fast and thorough; evidence generated as a by-product | a longer total suite |
| End-to-end tests only | realistic | slow, poor diagnostics, weak TDD loop |
| Unit tests only | fast | misses integration, contract, concurrency faults |

## Decision

- Tools: JUnit Jupiter 6, AssertJ, Mockito, Spring Boot test starters (MockMvc), Awaitility (with
  explicit timeouts, never sleeps), ArchUnit core (plain `@Test` methods), openapi-request-validator
  3.0.0 for contract validation, JaCoCo (line coverage ≥ 80% for `shortener.domain`,
  `shortener.service`, `orchestration.engine`, `orchestration.planning`,
  `orchestration.governance`, `orchestration.reliability`, `orchestration.policy`; PVT-23).
- Orchestration tests use **scripted agents** (succeed, fail transiently N times, fail permanently,
  sleep, ask for clarification, produce policy failures) to test the engine independently of the
  real agents; the real agents are tested individually and in the end-to-end scenarios.
- Test methods carry `@Tag("FR-…")` / `@Tag("SCN-…")` / `@Tag("RDR-…")`; `TraceabilityMatrixTest`
  fails the build if any FR or SCN in `spec.md` lacks a tagged test, and writes the matrix.
- TDD evidence: red and green command results are recorded per task group in
  `docs/assessment/tdd-evidence.md`.

## Rationale

The layered suite gives fast TDD loops and end-to-end realism, and the traceability check makes
SC-003 a build property instead of a document claim.

## Consequences

- **Positive**: evidence (matrix, scenario exports, coverage) is produced by `mvnw verify`.
- **Negative**: suite duration of a few minutes; the restart test boots two contexts.
- **Testing**: the strategy itself is validated by the pre-implementation review.

## Risks and Mitigations

| Risk | Mitigation |
|------|------------|
| Flaky asynchronous tests | Awaitility with generous timeouts; deterministic scripted agents; no shared state between contexts |
| Contract validator API unknown (3.0.0) | compile spike in M1; fallback to a small in-repo validator (research R-19) |

## Reversibility

High.

## Traceability

- Requirements: constitution IV and XI; SC-002, SC-003; NFR-TST-01/02
- Plan: §10, §13
- Tasks: every task's validation field

## Validation

`mvnw verify` green; JaCoCo check; traceability matrix complete.
