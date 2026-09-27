# Agentic Software Engineering System — URL Shortener

A **governed, agent-orchestrated software-engineering workflow** (the control plane) that takes
requirements through analysis, design, human approval, implementation checks, verification,
compliance, and release of capabilities in a **URL shortener** (the application plane). It was
built with GitHub SpecKit spec-driven development, Spring Boot 4.1.1, Java 21, and H2.

> **What "agent" means here.** The agents are **deterministic, knowledge-driven workers**. Each
> reads a versioned knowledge base (capability catalog, ambiguity lexicon, policy set) and the
> repository, and produces schema-validated artifacts. None calls a large language model at runtime.
> The system's "agentic" properties are autonomy within gates and budgets, dependency-aware
> orchestration, retry and fallback, safe-stop, and re-planning. The agents do not reason freely.
> See [ADR-017](docs/adr/ADR-017-deterministic-agents.md).


## Quick start

Prerequisites: **JDK 21** (the build fails fast on older JDKs). Maven is not needed; the wrapper
downloads it.

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21.0.12.1"      # adjust to your JDK 21
.\mvnw.cmd -B -ntp clean verify                               # full suite, JaCoCo, SBOM (run online)
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=demo"  # app on http://localhost:8080
```

```bash
export JAVA_HOME=/path/to/jdk-21 && ./mvnw -B -ntp clean verify
./mvnw spring-boot:run -Dspring-boot.run.profiles=demo
```

**Demo profile.** It uses labeled, **non-production** demo credentials: only their SHA-256 hashes
are configured.

| Token | Principal | Role |
|---|---|---|
| `demo-consumer-token` | demo-consumer | API consumer |
| `demo-requester-token` | alice | requester |
| `demo-approver-token` | bob | approver |
| `demo-release-token` | carol | release owner |
| `demo-auditor-token` | rita | auditor |

**Which token for which API.** Send the token as `Authorization: Bearer <token>`. These rules are
enforced by `SecurityConfig`; `…` stands for `/api/v1/workflows/{runId}`.

| API | Token |
|---|---|
| `GET /{code}` (redirect), `GET /actuator/health`, `GET /actuator/info` | none |
| `POST /api/v1/links`, `GET /api/v1/links/{code}`, `GET /api/v1/links/{code}/stats` | `demo-consumer-token` |
| `POST /api/v1/workflows` (submit a requirement), `POST …/change-requests`, `POST …/policy-exceptions` | `demo-requester-token` |
| `POST …/gates/ARCHITECTURE_APPROVAL/decision`, `POST …/clarifications`, `POST …/change-requests/{id}/decision`, `POST …/policy-exceptions/{id}/decision` | `demo-approver-token` |
| `POST …/gates/RELEASE_APPROVAL/decision`, `POST …/pause`, `POST …/resume`, `POST …/safe-stop` | `demo-release-token` |
| every `GET /api/v1/workflows/**` (run, timeline, artifacts, decisions, audit, summary, …), `GET /api/v1/policies`, `GET /api/v1/capabilities`, `GET /api/v1/reliability/report` | any of the requester, approver, release, or auditor tokens |
| other `/actuator/**` (metrics, prometheus, sbom) | `demo-auditor-token` |

- **Error codes.** A missing or unknown token returns `401`; a valid token with the wrong role
  returns `403`.
- **Separation of duties.** Whoever submitted a run cannot decide its architecture, change, or
  release gate, even with the right role.

**Walkthrough.** The end-to-end walkthrough (shorten a link, drive a governed run, decide a gate
yourself, run the drills) is in
[quickstart.md](specs/001-agentic-url-shortener/quickstart.md) §3–§6.

## Architecture in one picture

```text
             requirements (REST)                        human decisions (REST, role-checked)
                    │                                                │
 ┌──────────────────▼────────────────────────────────────────────────▼────────────────┐
 │ CONTROL PLANE  com.agentic.urlshortener.orchestration                              │
 │  RunCoordinator: persisted DAG, per-run lock, dispatch after commit, retry/timeout  │
 │  agents: ingestion → analysis → [clarification gate] → decomposition ∥ threat model │
 │          → design → [architecture gate] → implementation → testing ∥ security ∥ docs│
 │          → validation → compliance (policy set) → [release gate] → release → summary│
 │  governance · policy engine · re-planning · safe-stop · recovery · hash-chained audit│
 └──────────────────────────────────┬──────────────────────────────────────────────────┘
                                    │ ApplicationPlanePort (permission-scoped; ArchUnit-enforced)
 ┌──────────────────────────────────▼──────────────────────────────────────────────────┐
 │ APPLICATION PLANE  com.agentic.urlshortener.shortener                               │
 │  create / redirect / stats · URL safety policy · rate limits · idempotency          │
 │  capabilities released only by a governed run: custom-alias, click-limit, default-expiry │
 └──────────────────────────────────────────────────────────────────────────────────────┘
```

Details are in the following documents:
- [architecture overview](docs/architecture/overview.md)
- [orchestration](docs/architecture/orchestration.md)
- [governance](docs/architecture/governance.md)
- [reliability](docs/architecture/reliability.md)
- [the 19 ADRs](docs/adr/README.md)

## The three scenarios

Each scenario is an end-to-end test over real HTTP, with evidence exported to `target/evidence/`.
The human decisions in the tests are **simulated**: they come from labeled demo principals. Each
scenario document also describes a live walkthrough where you decide yourself.

| Scenario | What it shows | Command | Document |
|---|---|---|---|
| SCN-A greenfield | custom aliases: clarification skipped with evidence, parallel branches, two human gates, release | `.\mvnw.cmd test "-Dtest=ScenarioAGreenfieldE2ETest"` | [scn-a](docs/scenarios/scn-a-greenfield.md) |
| SCN-B brownfield | click-limited links: impact analysis gate before code, regression tests, V4 migration | `.\mvnw.cmd test "-Dtest=ScenarioBBrownfieldE2ETest"` | [scn-b](docs/scenarios/scn-b-brownfield.md) |
| SCN-C ambiguous | vague and conflicting request: stops for clarification, re-plans, resumes | `.\mvnw.cmd test "-Dtest=ScenarioCAmbiguousE2ETest"` | [scn-c](docs/scenarios/scn-c-ambiguous.md) |
| Drills RDR-01..07 | retry, fallback, rollback, gate deadline, resume, policy exception, operator controls | `.\mvnw.cmd test "-Dtest=ReliabilityDrillsE2ETest,RestartResumeTest"` | [drills](docs/scenarios/drills.md) |

A governance-invariant checker runs at the end of every scenario and drill run. It checks three
invariants: no stage runs past an undecided gate, no gate passes without a human decision, and no
release happens after a mandatory policy failure without an approved exception
([governance §8](docs/architecture/governance.md)).

## Evidence map

| Question | Where |
|---|---|
| What was required, and why? | [spec](specs/001-agentic-url-shortener/spec.md), [plan](specs/001-agentic-url-shortener/plan.md), [constitution](.specify/memory/constitution.md) |
| Is every requirement tested? | `.\mvnw.cmd test "-Dtest=TraceabilityMatrixTest"` → `target/traceability/requirements-to-tests.md` |
| Was it built test-first? | [tdd-evidence.md](docs/assessment/tdd-evidence.md), [task-group checkpoints](docs/assessment/task-group-checkpoints.md) |
| API contract | [openapi.yaml](specs/001-agentic-url-shortener/contracts/openapi.yaml), [artifact schemas](specs/001-agentic-url-shortener/contracts/schemas/), `ContractDriftTest` |
| Reliability and MTTR | [mttr-validation.md](docs/assessment/mttr-validation.md), `GET /api/v1/reliability/report` |
| Security | [SECURITY.md](SECURITY.md), [security-scans.md](docs/assessment/security-scans.md) |
| Performance | [performance.md](docs/assessment/performance.md) (targets **not** reliably met) |
| Testing approach, limitations, trade-offs | [testing-limitations-tradeoffs.md](docs/assessment/testing-limitations-tradeoffs.md), [risk register](docs/assessment/risk-register.md) |
| Final summary and reviewer guide | [final-engineering-summary.md](docs/assessment/final-engineering-summary.md), [reviewer-guide.md](docs/assessment/reviewer-guide.md) |


## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md): the SpecKit workflow, TDD evidence, commit conventions, and
human gates.
