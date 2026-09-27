# ADR-003: H2 Runtime Database with Flyway-Owned Schema and Spring Data JPA

## Status

Proposed (2026-09-26) — pending acceptance at Gate G4. **H2 as the runtime database** is a
candidate directive ("make sure you use h2 database runtime"); the remaining choices (file mode,
PostgreSQL compatibility, Flyway, JPA) are assistant proposals.

## Context

Both planes need durable, transactional storage. Resumption after a process restart (FR-REL-08,
SC-007) requires state that survives the process. The versioned persistence schema is an
assessment deliverable (NFR-CHG-01/02). Reviewers must be able to run everything without
installing a database server (CON-02).

## Decision Drivers

- Zero-install for reviewers; durability across restarts
- Versioned, reviewable schema migrations
- Atomic conditional updates for exact analytics and click limits
- Portability towards a production database

## Options Considered

| Option | Advantages | Disadvantages / risks |
|--------|------------|-----------------------|
| **H2 2.4 file database (PostgreSQL mode) + Flyway + JPA** | zero-install; durable; fast; SQL close to PostgreSQL | not a production database; single-process file lock |
| H2 in-memory at runtime | simplest | loses all state on restart, so no resumption evidence |
| PostgreSQL in Docker (+ Testcontainers) | production-like | Docker not guaranteed on reviewer machines; slower tests |

## Decision

- Runtime: H2 file database at `./data/agentic-sdlc` (`MODE=PostgreSQL`, `DB_CLOSE_ON_EXIT=FALSE`,
  `AUTO_RECONNECT` disabled), declared with Maven `runtime` scope; the H2 web console is **not**
  included.
- Tests: in-memory H2 per Spring context (`jdbc:h2:mem:<unique>`); the restart test uses a
  temporary file database.
- Flyway owns the schema (`V1`–`V4`); Hibernate `ddl-auto=validate`.
- Spring Data JPA for aggregates; explicit `@Modifying` JPQL/SQL for atomic counters and
  conditional updates.
- HikariCP `connection-timeout` = 2 s so store unavailability fails fast (PVT-24).

## Rationale

It meets the directive and every driver except production-grade scale, which the constitution
explicitly does not require. PostgreSQL mode plus Flyway keeps the path to PostgreSQL (backlog
BL-02) a configuration change.

## Consequences

- **Positive**: one command to run; restart and resume demonstrations are real.
- **Negative**: single writer process (ASM-01); some SQL dialect differences vs. PostgreSQL remain.
- **Operational**: delete `./data/` to reset; the backup is a file copy.
- **Testing**: every context runs all migrations, so migrations are continuously tested.
- **Governance**: schema changes are visible as reviewable migration files.

## Risks and Mitigations

| Risk | Mitigation |
|------|------------|
| File lock when two instances start | documented single-instance constraint; the second start fails fast |
| Dialect drift vs. PostgreSQL | PostgreSQL mode; only standard SQL in migrations; BL-02 |

## Reversibility

High: switching the JDBC URL and driver plus running the same Flyway migrations on PostgreSQL.

## Traceability

- Requirements: FR-ORC-08, FR-REL-08, FR-OPS-02, NFR-CHG-01/02, CON-02; directive (gate register)
- Plan: Technical Context; data-model.md migration plan; research R-05
- Tasks: baseline migration tasks; restart test task

## Validation

Flyway migrates on every test context start; `RestartResumeTest` proves durability; the
`FR-OPS-02` test proves the fail-fast bound.
