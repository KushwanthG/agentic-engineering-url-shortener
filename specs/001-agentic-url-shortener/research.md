# Research: Agentic Software Engineering System — URL Shortener

**Feature**: `001-agentic-url-shortener` | **Date**: 2026-09-26 | **Phase**: 0 (plan)

Every decision below is an **assistant proposal pending Gate G4**, except those marked
**Candidate directive**, which the candidate decided (recorded verbatim in
[`docs/governance/human-gate-register.md`](../../docs/governance/human-gate-register.md)). Material
decisions are expanded into ADRs under [`docs/adr/`](../../docs/adr/).

## Evidence gathered (2026-09-26)

| Fact | Value | Source |
|------|-------|--------|
| Installed JDKs | JDK 21.0.12.1 LTS (`C:\Program Files\Java\jdk-21.0.12.1`); JDK 11 is the machine default | `java -version` |
| Installed Maven | 3.8.4 | `mvn -version` |
| Spring Boot support windows | 4.1: OSS to 2027-07-31 · 4.0: to 2026-12-31 · 3.5: OSS ended 2026-06-30 | endoflife.date API |
| Latest Spring Boot releases | 4.1.1, 4.0.8, 3.5.16 | Maven Central metadata |
| Boot 4.1.1 managed versions | Spring Framework 7.0.9, Spring Security 7.1.1, Hibernate 7.4.5.Final, Jackson 3.1.5 (Jackson 2 BOM 2.21.5), Flyway 12.4.0, H2 2.4.240, Micrometer 1.17.1, JUnit Jupiter 6.0.3, AssertJ 3.27.7, Mockito 5.23.0, Awaitility 4.3.0, Tomcat 11.0.24, CycloneDX plugin 2.9.3 | `spring-boot-dependencies-4.1.1.pom` |
| Boot 4.1 starter names | `spring-boot-starter-webmvc`, `-data-jpa`, `-flyway`, `-security`, `-validation`, `-actuator`; test starters `-webmvc-test`, `-data-jpa-test`, `-flyway-test`, `-security-test`, `-validation-test`, `-actuator-test` | start.spring.io generated POM |
| Unmanaged test libraries | ArchUnit 1.5.1; `com.atlassian.oai:openapi-request-validator-core` 3.0.0 (renamed from `swagger-request-validator-core`, depends on swagger-parser v3, networknt json-schema-validator, Jackson 2); JaCoCo 0.8.15 | Maven Central metadata and POMs |

---

## R-01 Language and runtime

- **Decision**: Java 21 (LTS). **Candidate directive.**
- **Rationale**: LTS; records, sealed interfaces, pattern matching for `switch`, and sequenced
  collections simplify the state-machine and result types; supported by Spring Boot 4.1.
- **Alternatives considered**: Java 17 (older LTS baseline, fewer language features); Java 25
  (not installed).

## R-02 Framework and version

- **Decision**: Spring Boot **4.1.1** (framework: **candidate directive**; version: proposal).
- **Rationale**: 4.1 is the current line with the longest OSS support (to 2027-07-31); 3.5's OSS
  support ended 2026-06-30, so choosing it would create a dependency-risk finding at release.
- **Alternatives considered**: 3.5.16 (familiar APIs, but OSS end-of-life); 4.0.8 (support ends
  2026-12-31).
- **Risks**: Boot 4 moved test auto-configuration packages and switched to Jackson 3
  (`tools.jackson.*`); compile errors will surface these, and the walking-skeleton task verifies
  them first.

## R-03 Build and reproducibility

- **Decision**: Maven with the Maven Wrapper pinned to Maven 3.9.x; `maven-enforcer-plugin` requires
  Java 21+ and Maven 3.6.3+; JaCoCo for coverage; CycloneDX (managed by the Boot parent) generates
  the SBOM into `META-INF/sbom/`, where the Actuator `sbom` endpoint and the license policy read it.
- **Rationale**: Maven is installed locally; the wrapper removes version drift for reviewers; the
  enforcer fails fast when `JAVA_HOME` points at JDK 11 (the machine default here).
- **Alternatives considered**: Gradle (not installed; no advantage for this scope).

## R-04 Application architecture

- **Decision**: a **modular monolith**: one deployable with three top-level modules as
  packages — `platform` (shared infrastructure), `shortener` (application plane), and
  `orchestration` (control plane). The control plane reaches the application plane only through
  the `ApplicationPlanePort` interface; ArchUnit tests enforce the boundaries.
- **Rationale**: satisfies Principle VII's separation without distributed-system complexity
  (Delivery Constraints); a single process keeps restart and resume demonstrations deterministic.
- **Alternatives considered**: separate services for the two planes (network failure modes and
  deployment overhead with no requirement driving them); a Maven multi-module build (stronger
  compile-time boundaries, but more build ceremony; ArchUnit gives equivalent enforcement).

## R-05 Persistence

- **Decision**: Spring Data JPA (Hibernate 7) on **H2 2.4** as the runtime database (**candidate
  directive**: "make sure you use h2 database runtime"; Maven `runtime` scope), in PostgreSQL
  compatibility mode. The application runs on a file database in every profile (`./data/`, so
  state survives restarts, which the resume demonstrations need); tests use in-memory H2. Flyway
  owns the schema (`V1`..`V4`); Hibernate only validates it. The H2 web console is not included.
- **Rationale**: zero-install, durable, fast tests; Flyway migrations are the versioned persistence
  schema deliverable; PostgreSQL mode keeps SQL portable to the documented production target.
- **Alternatives considered**: PostgreSQL in Docker (production-like, but Docker is not guaranteed
  on reviewer machines — documented as the production path); in-memory maps (no durability, so no
  restart evidence).
- **Risk**: H2 is not a production database; single-writer file locking limits the prototype to one
  process — accepted per ASM-01.

## R-06 Short-code generation

- **Decision**: 7-character codes over the 62-letter-and-digit alphabet from `SecureRandom`;
  uniqueness is enforced by a unique index; on a collision the whole create transaction retries,
  up to 5 attempts (PVT-02/03).
- **Rationale**: 62⁷ ≈ 3.52 × 10¹² codes. At 10 million stored links a single attempt collides with
  probability ≈ 2.8 × 10⁻⁶, so five consecutive collisions are negligible (NFR-SCA-02). Random codes
  are not enumerable (FR-LNK-06).
- **Alternatives considered**: base62 of a database sequence (predictable, so enumerable); hash of
  the URL (deterministic collisions, and it implies de-duplication, which Q3 rejected).

## R-07 URL validation and normalization

- **Decision**: strict `java.net.URI` parsing; `http`/`https` allow-list; reject user-info, a missing
  host, length > 2,048, `localhost` and the `.localhost` / `.local` / `.internal` suffixes, and the
  configured public host. IP literals are classified by a custom IPv4 parser that accepts inet_aton
  forms (decimal, octal, hex, 1–4 parts) and by `InetAddress` for IPv6 literals; loopback,
  private, link-local, unspecified, multicast, CGNAT (100.64/10), and unique-local (fc00::/7)
  ranges are rejected. Normalization lower-cases the scheme and host, converts IDN hosts with
  `java.net.IDN`, and removes the default port. **No DNS resolution** is performed.
- **Rationale**: prevents redirects to internal targets written in obfuscated notations; the service
  never fetches target URLs, so DNS-rebinding style checks are unnecessary. That limitation is
  recorded as residual risk: a public hostname can still resolve to a private address.
- **Alternatives considered**: DNS resolution at creation (latency, and a false sense of safety
  because DNS can change); third-party reputation checks (network dependency; EXC-06).

## R-08 Redirect and analytics consistency

- **Decision** (Clarifications Q5, provisional): `302 Found` with `Cache-Control: no-store`. The
  click is recorded synchronously in its own short transaction: an atomic
  `UPDATE … SET click_count = click_count + 1` plus a `click_event` insert. For links without a
  click limit, analytics failures fail open: the redirect proceeds and a metric increments. For
  click-limited links the conditional update `… WHERE click_count < max_clicks` decides the
  redirect and fails closed.
- **Rationale**: exact counts under concurrency without application-level locks; one extra write
  per redirect is acceptable at prototype scale.
- **Alternatives considered**: `301` (cached by browsers, so repeat clicks are invisible);
  asynchronous buffered counting (lossy on crash; needs a queue).

## R-09 Idempotency

- **Decision**: optional `Idempotency-Key` header, scoped per API consumer. The link and the
  idempotency record (request fingerprint SHA-256 plus stored response) are written in **one
  transaction**; a unique index on `(consumer_id, idem_key)` serializes concurrent duplicates. The
  losing transaction rolls back its link and replays the committed response. The same key with a
  different fingerprint is rejected with `409 IDEMPOTENCY_KEY_REUSED`. Window: 24 h (PVT-04).
- **Alternatives considered**: an in-memory cache (not durable across restarts, not safe under
  races); an `IN_PROGRESS` placeholder row (extra state for no benefit at this scale).

## R-10 Authentication and authorization

- **Decision** (Clarifications Q2, provisional): Spring Security with a custom stateless bearer-token
  filter. Principals and roles come from configuration and store **SHA-256 hashes** of tokens, never
  raw tokens. Roles: `API_CONSUMER`, `REQUESTER`, `APPROVER`, `RELEASE_OWNER`, `AUDITOR`. The
  default profile defines no principals (secure default: authenticated APIs stay closed); the `demo`
  profile defines five labeled demo principals. Redirects and `health`/`info` are public. CSRF is
  disabled because there are no cookies or sessions.
- **Rationale**: demonstrates least privilege and separation of duties with no external identity
  provider (ASM-02); the production path is an OAuth2/OIDC resource server behind the same
  role model.
- **Alternatives considered**: HTTP Basic with in-memory users (passwords in configuration, weaker
  for APIs); an OAuth2 resource server with a local issuer (heavy setup for reviewers).

## R-11 Rate limiting

- **Decision**: in-process token buckets with an injectable `Clock`: creation at 30 per minute per
  API consumer, not-found outcomes at 60 per minute per client address (`request.getRemoteAddr()`;
  `X-Forwarded-For` is ignored unless a trusted proxy is configured — not in scope). The bucket map
  is bounded and evicts idle buckets.
- **Alternatives considered**: Bucket4j (extra dependency for ~80 lines); gateway-level limiting
  (no gateway in scope). Distributed limiting (Redis) is recorded as the scale-out path.

## R-12 Orchestration engine

- **Decision**: a **purpose-built, persisted DAG engine**. Each run has a versioned plan (graph
  snapshot) and one persisted `stage_node` row per stage with explicit `depends_on`. A per-run
  coordinator (in-JVM lock plus JPA optimistic locking) computes ready stages, dispatches them to a
  **bounded** stage-worker pool (default 8 threads), enforces per-attempt timeouts, and persists
  every transition in a short transaction. Retries and backoff run on a `TaskScheduler`; gate
  deadlines are enforced by a scheduled sweeper.
- **Rationale**: the governance semantics required here — approvals bound to artifact fingerprints,
  fingerprint-based selective re-execution, policy-blocked stages waiting for exceptions,
  permission-scoped agents, MTTR-grade failure episodes — are specific. General-purpose engines
  would still need all of them as custom code on top, plus infrastructure. A bounded pool also caps
  agent parallelism, which is itself an autonomy control.
- **Alternatives considered**: Temporal, Camunda, or Flowable (external servers or heavy embedded
  engines; BPMN or workflow-code semantics would hide the governance logic the assessment grades);
  Spring State Machine (models one entity's state, not a dependency graph); LangGraph4j (LLM-centric
  graph runtime; Q1 selected deterministic agents); virtual threads (unbounded concurrency works
  against the bounded-autonomy goal).

## R-13 Dynamic replanning

- **Decision**: **content-addressed incremental re-execution**. An upstream change (clarification
  answer, approved change request) creates a new requirement version and a new plan version. Every
  stage downstream of the change is invalidated (generation + 1; outputs superseded, history kept).
  When an invalidated stage becomes ready, the engine fingerprints its inputs. If they equal the
  inputs of its last successful execution, the stage is marked `SUCCEEDED` as **reused** without
  running; otherwise it runs again. Gate approvals are reused only if their bound artifact
  fingerprints are unchanged. Structural changes (brownfield stages added or removed,
  change-approval gate inserted) are recorded as a plan diff.
- **Rationale**: re-executes exactly the stages whose inputs changed (FR-RPL-01) and keeps approvals
  valid when the reviewed content did not change (FR-GOV-05) — the same principle as build systems
  such as Make or Bazel.
- **Alternatives considered**: restarting the run (throws away valid work and approvals);
  invalidating the whole downstream closure unconditionally (re-executes and re-approves stages
  whose inputs did not change).

## R-14 Runtime agents and knowledge

- **Decision** (Clarifications Q1, provisional): deterministic agents behind a `StageAgent` SPI, each
  declaring its permissions. Agent knowledge lives in versioned resources: a **capability catalog**
  (capabilities, keywords, components, API/schema deltas, release flag, probes, threats, docs), an
  **ambiguity lexicon** (vague terms, undefined-concept rules, conflict pairs), and a **policy set**.
  Brownfield impact analysis scans the live source tree (`src/main/java`, `src/test/java`, `docs/`)
  to build an import-dependency graph and reverse-dependency closure. If sources are unavailable, it
  falls back to catalog-only analysis, marked degraded.
- **Rationale**: reproducible, explainable, offline; the fallback path exercises FR-REL-03 with a
  genuine reason rather than a contrived one.
- **Alternatives considered**: LLM-backed agents (Q1 option B — deferred backlog item BL-01).

## R-15 Capability release (application-plane change control)

- **Decision** (Clarifications Q4, provisional): capabilities are delivered dark behind persisted
  **capability release flags** (`capability_release` table). Only the orchestration `RELEASE` stage
  may change a flag, through `ApplicationPlanePort`. A flag gates **new use only**; stored per-link
  behavior (aliases, click limits, assigned expiries) is always honored. Verification before
  release runs in **preview mode**: a scoped, in-process override that enables the capability only
  for synthetic probe calls made by the run's agents. It cannot be triggered over HTTP.
- **Rationale**: makes release a real, reversible, audited change to the running system; withdrawal
  is a true rollback of flag state and never touches consumer data.
- **Alternatives considered**: code deployment per scenario (not demonstrable in a single run);
  configuration-file flags (not changeable at runtime; no audit trail).

## R-16 Policy engine

- **Decision**: a versioned YAML policy set (`policy-set.yaml`, `version: 1.0.0`) declares each policy's
  id, domain, severity (`MANDATORY` or `ADVISORY`), and applicability; Java `PolicyRule` beans
  implement the evaluation. Each run pins the policy-set version at creation. Outcomes are `PASS`,
  `FAIL`, `EXCEPTION_REQUESTED`, or `NOT_APPLICABLE`, each with evidence. Exceptions are time-bound,
  require an approver other than the requester, and are re-checked for expiry when readiness is
  computed.
- **Alternatives considered**: OPA/Rego (separate runtime and language; heavy for ten rules); rules
  hard-coded in agents (no versioning or independent evidence).

## R-17 Audit integrity

- **Decision**: an append-only `audit_event` table with a per-run SHA-256 **hash chain**
  (`hash = SHA-256(prev_hash ‖ canonical event)`) and sequence numbers; a global chain covers
  events outside runs. Verification recomputes the chain and reports the first broken sequence.
  The application never issues `UPDATE` or `DELETE` on audit rows.
- **Rationale**: tamper-evident without external services (FR-AUD-02). Limitation: an attacker with
  database write access could rewrite the whole chain. The production mitigation — anchoring chain
  heads in WORM storage or an external timestamping service — is documented.

## R-18 Observability

- **Decision**: Micrometer meters (Prometheus registry at `/actuator/prometheus`), Micrometer
  `Observation`s around stage execution, MDC keys `runId`/`stage`/`correlationId`, and Spring Boot
  structured logging (ECS) in the `json-logs` profile. The reliability report (MTTR and related
  metrics) is computed from persisted evidence rather than from in-memory meters, so it survives
  restarts and is reproducible.
- **Alternatives considered**: exporting OpenTelemetry traces (needs a collector; the persisted
  timeline gives equivalent reviewer evidence; recorded as a production enhancement).

## R-19 Testing and contract validation

- **Decision**: JUnit Jupiter 6, AssertJ, Mockito, Spring Boot test slices and `@SpringBootTest` with
  MockMvc, Awaitility for asynchronous orchestration, **ArchUnit core** (plain `@Test` methods, which
  avoids depending on its JUnit engine) for boundaries, and **openapi-request-validator-core 3.0.0**
  to validate recorded MockMvc exchanges against `contracts/openapi.yaml`. A drift test compares
  controller mappings with contract paths in both directions. JaCoCo reports coverage against
  PVT-23. Artifact JSON Schemas (`contracts/schemas/`) are validated in tests.
- **Risk**: the validator's 3.0.0 API (package names) is unverified; the contract-test task starts
  with a compile spike, and the fallback is a small in-repository schema validator for the subset of
  OpenAPI used.

## R-20 Deployment and local execution

- **Decision**: an executable jar and `spring-boot:run`; profiles `default` (secure, closed APIs, fault
  injection disabled), `demo` (demo principals, fault injection enabled, file H2 under `./data`),
  and `json-logs`. No container is required; a Dockerfile is deferred (BL-03).

## Deferred backlog (scope control)

| ID | Item | Reason deferred |
|----|------|-----------------|
| BL-01 | LLM-backed agents with deterministic fallback (Q1 option B) | Not required; adds secrets and non-determinism |
| BL-02 | PostgreSQL profile with Testcontainers | Docker not guaranteed on reviewer machines |
| BL-03 | Container image and CI pipeline (dependency vulnerability scanning, secret scanning in CI) | Timebox; recommended for production |
| BL-04 | OpenTelemetry trace export | Needs a collector; persisted timeline used instead |
| BL-05 | Distributed rate limiting and multi-instance run coordination (DB row locks / leases) | ASM-01 single instance |
| BL-06 | Anchoring audit chain heads externally (WORM / timestamping) | External service |
| BL-07 | Percentage-based rollout | EXC-04 |
