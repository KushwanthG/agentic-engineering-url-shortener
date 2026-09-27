# Contributing

This repository follows **GitHub SpecKit spec-driven development**, governed by the
[constitution](.specify/memory/constitution.md). The working rules for AI assistants are in
[CLAUDE.md](CLAUDE.md).

## Workflow and authority

Work follows this order: constitution → specification → clarifications → plan → ADRs
(`docs/adr/`) → checklists → tasks (`specs/001-agentic-url-shortener/tasks.md`) → analyze →
implement → converge.

When sources conflict, the constitution's authority hierarchy decides. **Nothing is changed
silently.** A change to a requirement, an ADR, a contract (`openapi.yaml`, JSON schemas), or a state
model goes back through the SpecKit stage that owns it. A deviation found during implementation is
recorded in the task-group checkpoint and added to the gate register for human review.

## Human gates

[docs/governance/human-gate-register.md](docs/governance/human-gate-register.md) is the source of
truth.
- Gates G1–G7, ADR acceptance, and reviewer-owned checklist items belong to the **human candidate**.
- Tools and assistants never mark them approved.
- Simulated human input in tests is always labeled as simulated.

## Test-driven development

- **Cycle:** red → green → refactor. Record the red run (command and observed failure) and the
  green run in [tdd-evidence.md](docs/assessment/tdd-evidence.md). Work written before its test is
  recorded as a *verification* test, never as TDD.
- **Traceability:** tag each test class with the requirement, scenario, or drill ids it verifies,
  for example `@Tag("FR-LNK-01")` or `@Tag("SCN-B")`. `TraceabilityMatrixTest` fails on untested
  functional requirements and scenarios, untagged test classes, unknown ids, and tasks without a
  `Req` field.
- **Planes:** `shortener` must not depend on `orchestration`, and only `orchestration.integration`
  may call the shortener. `ArchitectureTest` enforces both rules.

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21.0.12.1"
.\mvnw.cmd -B -ntp verify                   # full suite, JaCoCo, SBOM; run it online
.\mvnw.cmd -B -ntp test "-Dtest=SomeTest"   # one class
```

## Task groups and commits

At each task-group boundary:
1. Write the checkpoint record in
   [task-group-checkpoints.md](docs/assessment/task-group-checkpoints.md).
2. Do a pre-commit review.
3. Make **one** [Conventional Commit](https://www.conventionalcommits.org/) for the group, for
   example `feat(orchestration): …`, `test(quality): …`, or `docs(assessment): …`.

AI-assisted commits carry a `Co-Authored-By:` trailer.

Never commit secrets or confidential material. The assignment brief is confidential and is not in
this repository. `RepositorySecretScanTest` scans every tracked file.
