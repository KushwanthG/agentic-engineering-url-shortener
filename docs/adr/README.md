# Architecture Decision Records

Produced by the ADR generation gate (SDD guide §10) after `/speckit-plan`, before
`/speckit-tasks`. **Every ADR is `Proposed`.** Only the human candidate may mark an ADR `Accepted`
or `Rejected` (Gate G4, see
[`../governance/human-gate-register.md`](../governance/human-gate-register.md)).

## 1. ADR inventory

| ADR | Decision | Guide topic | Candidate directive involved |
|-----|----------|-------------|------------------------------|
| [ADR-001](ADR-001-application-architecture.md) | Modular monolith; planes separated by a port; ArchUnit-enforced | 1 Application architecture | — |
| [ADR-002](ADR-002-language-and-framework.md) | Java 21 + Spring Boot 4.1.1 + Maven Wrapper | 2 Language and framework | Java 21, Spring Boot |
| [ADR-003](ADR-003-persistence.md) | H2 runtime database (file, PostgreSQL mode) + Flyway + JPA | 3 Persistence | H2 runtime database |
| [ADR-004](ADR-004-short-code-generation.md) | SecureRandom base62 × 7, unique index, ≤ 5 regenerations | 4 Short codes and collisions | — |
| [ADR-005](ADR-005-orchestration-model.md) | Purpose-built persisted DAG engine | 5 Orchestration model | — |
| [ADR-006](ADR-006-workflow-state-persistence.md) | Relational state, per-run lock, optimistic versions | 6 Workflow state persistence | — |
| [ADR-007](ADR-007-dependency-graph-representation.md) | Adjacency lists + versioned plan snapshots + validator | 7 Dependency graph representation | — |
| [ADR-008](ADR-008-human-approval-model.md) | Roles, separation of duties, fingerprint-bound approvals, deadlines | 8 Human approval model | — |
| [ADR-009](ADR-009-retry-fallback-safe-stop.md) | Classified failures, bounded retries, declared fallbacks, safe-stop | 9 Retry, fallback, safe-stop | — |
| [ADR-010](ADR-010-rollback-versus-compensation.md) | Rollback for flag state; compensation for data and exposure | 10 Rollback vs. compensation | — |
| [ADR-011](ADR-011-dynamic-replanning.md) | Content-addressed incremental re-execution | 11 Dynamic replanning | — |
| [ADR-012](ADR-012-observability-and-audit.md) | Hash-chained audit; metrics computed from evidence | 12 Observability and audit | — |
| [ADR-013](ADR-013-testing-strategy.md) | Layered, contract-first tests; executable traceability | 13 Testing strategy | — |
| [ADR-014](ADR-014-deployment-and-local-execution.md) | Executable jar, wrapper, `default`/`demo`/`json-logs` profiles | 14 Deployment and local execution | — |
| [ADR-015](ADR-015-authentication-and-authorization.md) | Hashed bearer tokens mapped to roles | 15 Authentication and authorization | — |
| [ADR-016](ADR-016-analytics-consistency.md) | Exact synchronous analytics; fail-open vs. fail-closed | 16 Analytics consistency | — |
| [ADR-017](ADR-017-deterministic-agents.md) | Deterministic knowledge-driven agents behind a permissioned SPI | (material, added) | — |
| [ADR-018](ADR-018-capability-release.md) | Governed capability flags with in-process preview | (material, added) | — |
| [ADR-019](ADR-019-policy-engine.md) | Versioned YAML policy set with Java rules | (material, added) | — |

## 2. Decisions requiring human approval

All nineteen. The most consequential, where a different choice would change large parts of the
implementation:

1. **ADR-005** — build a custom orchestration engine instead of adopting Temporal or Camunda.
2. **ADR-017** — deterministic agents; no LLM at runtime (depends on G3 Q1).
3. **ADR-011** — fingerprint-based replanning semantics.
4. **ADR-008 / ADR-015** — approval model and authentication stand-in (depends on G3 Q2).
5. **ADR-010 / ADR-018** — release, rollback, and compensation semantics (depends on G3 Q4).
6. **ADR-016** — exact synchronous analytics (depends on G3 Q5).
7. **ADR-002** — Spring Boot 4.1.1 rather than an end-of-life 3.5 line.

## 3. Decisions safely deferrable

Implementation can start while these parts are revisited later without rework of the core:

- ADR-014: containerization and CI pipeline (backlog BL-03).
- ADR-003: a PostgreSQL profile (BL-02).
- ADR-012: external anchoring of audit chain heads (BL-06).
- ADR-017: an LLM-backed agent for a stage, registered with the deterministic agent as fallback (BL-01).
- ADR-013: exact coverage thresholds per package (proposed 80% lines, PVT-23).

## 4. Conflicts with the current plan

| Finding | Resolution |
|---------|------------|
| The quickstart's drill RDR-02 forced a fallback on `IMPACT_ANALYSIS` and expected `READY_WITH_ACCEPTED_LIMITATIONS`, but mandatory policy `CHG-002` (plan §9, ADR-019) requires a non-degraded impact analysis, so the run would block at compliance | Fixed: RDR-02 now uses the `DOCUMENTATION` template fallback; the impact-analysis fallback is documented as a path that requires a policy exception (covered by a test) |
| ADR-010 adds a rule the plan did not state: rollback restores a flag only if it still holds this run's value | Refinement, consistent with plan §6; tasks will include it |
| ADR-018 adds short-lived caching of flags, invalidated on change | Refinement; no plan conflict |

## 5. Missing information (to be confirmed during implementation, not blocking)

- Exact Spring Boot 4.1 package names for test auto-configuration: confirmed at milestone M1 by
  compilation.
- `openapi-request-validator-core` 3.0.0 API surface: compile spike at M1; fallback per research
  R-19.
- Whether `spring-boot:run` produces the CycloneDX SBOM on the classpath: verified at M0; affects
  policy `LIC-001` evidence.
- Human answers at G3 (Q1–Q5) and ADR acceptance at G4.
