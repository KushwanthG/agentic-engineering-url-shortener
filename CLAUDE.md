# CLAUDE.md — working in this repository

## What this is

Agentic Software Engineering System (control plane) governing a URL shortener (application plane),
built with GitHub SpecKit spec-driven development. Feature: `specs/001-agentic-url-shortener/`.

## Build and test

The machine default JDK may be 11; this project requires **JDK 21**.

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21.0.12.1"   # adjust to the local JDK 21
.\mvnw.cmd -B -ntp verify                                  # full suite + JaCoCo + SBOM
.\mvnw.cmd -B -ntp test "-Dtest=SomeTest"                  # one test class
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=demo"
```

Tests use the `test` profile (`@IntegrationTest` meta-annotation): in-memory H2 per context, demo
principals, fault injection enabled. The runtime database is an H2 file under `./data/`.

## SpecKit process — authority and order

Constitution (`.specify/memory/constitution.md`) → spec → clarifications → plan → ADRs
(`docs/adr/`) → checklists → tasks (`tasks.md`) → analyze → implement → converge. When instructions
conflict, follow the constitution's authority hierarchy. Do not change requirements, ADRs,
contracts, or state models silently: route changes through the correct SpecKit stage.

## Human gates

`docs/governance/human-gate-register.md` is the source of truth. Gates G1–G7 and ADR acceptance
belong to the human candidate: never mark a gate, ADR, or reviewer-owned checklist item as
approved, and never fabricate approvals, test results, or metrics.

## Implementation conventions

- TDD: red → green → refactor; record red and green runs in `docs/assessment/tdd-evidence.md`.
- Tag tests with the requirement/scenario ids they verify (`@Tag("FR-LNK-01")`); the traceability
  test enforces coverage.
- Planes: `shortener` must not depend on `orchestration`; only `orchestration.integration` may call
  the shortener (ArchUnit enforces this).
- Task-group boundary: checkpoint record in `docs/assessment/task-group-checkpoints.md`, then one
  Conventional Commit with the AI co-author trailer.
