# Human Gate Register

**Purpose**: single source of truth for the status of every mandatory human gate defined in the
constitution (Development Workflow and Quality Gates). Required by Constitution Principles III, X,
and XI.

**Owner**: the human candidate. The AI assistant may add recommendations and evidence pointers but
MUST NOT change a gate's decision fields.

## How to ratify a gate

1. Review the evidence listed for the gate.
2. Set **Decision** to `APPROVED`, `APPROVED WITH CONDITIONS`, or `REJECTED`, and fill in
   **Decided by**, **Date**, and **Notes** (conditions, rejected items, requested changes).
3. For G4, also change each accepted ADR's `Status` from `Proposed` to `Accepted` (or `Rejected`).
4. Commit the change yourself, e.g. `git commit -m "docs(governance): ratify gate G1"`.

If a gate is rejected, the affected SpecKit stage is re-run and downstream artifacts are re-analyzed
(`/speckit.analyze`) before work resumes.

## Delegation record

On 2026-09-26 the candidate instructed the assistant (verbatim):

> "I have to work on assingment project... Here is the project details.. and i want to build it
> thorugh spec driven development project and spring boot project... here is the sdd details
> "C:\Users\bsuna\Downloads\AI Native SDLC - User Guide.docx" Please build the project here"

and, during the session:

> "keep all sdd related files and project in the current folder only..."

and, as a technology directive:

> "I have installed jdk 21... please use java 21 version in the project"

> "make sure you use h2 database runtime"

These are the candidate's own decisions and are treated as approved inputs: **Spring Boot** as
the framework (first instruction), **Java 21** as the language level (third instruction), and
**H2 as the runtime database** (fourth instruction; declared with Maven `runtime` scope). The
specific Spring Boot version and every other technology choice remain assistant proposals, pending
G4.

The candidate was not available to answer gate questions during the session. Under the
constitution's **Delegated Provisional Progression** clause, stages proceeded provisionally. This
instruction delegates *execution*; it is **not** an approval of any specific artifact, decision, or
assumption. Every gate below therefore remains `PENDING RATIFICATION` until the candidate records a
decision, and release readiness is blocked until then.

## Gate status

| Gate | Decision required | Status | Decided by | Date |
|------|-------------------|--------|------------|------|
| G1 | Constitution ratified | PENDING RATIFICATION | — | — |
| G2 | Requirements approved | PENDING RATIFICATION | — | — |
| G3 | Clarifications decided | PENDING RATIFICATION | — | — |
| G4 | Architecture and ADRs accepted | PENDING RATIFICATION | — | — |
| G5 | Implementation may start | PENDING RATIFICATION | — | — |
| G6 | Release readiness decided | NOT YET REACHED | — | — |
| G7 | Final submission | NOT YET REACHED | — | — |

---

## G1 — Constitution

- **Artifact**: [`.specify/memory/constitution.md`](../../.specify/memory/constitution.md) v1.0.0
- **Assistant recommendation**: approve. The draft transcribes the guide's Prompt 1 principles and
  adds one governance clause the guide does not contain, **Delegated Provisional Progression**,
  so that this delegated session could proceed without fabricating approvals. Strike that clause if
  you do not accept it; the consequence is that implementation work done before ratification must
  be re-reviewed against the ratified gates.
- **Decision**: _pending_ · **Decided by**: _pending_ · **Date**: _pending_ · **Notes**: _pending_

### Gate 1 review checklist (from the guide)

| Guide check | Covered by |
|-------------|------------|
| Non-linear orchestration | Principle II (graph, parallel, join, branching) |
| State persistence | Principle II (persisted state, lineage), VIII (resume) |
| Human ownership | Principle III, Governance (gates, delegated progression) |
| Bounded retry | Principle VIII (bounded retries, backoff, timeouts) |
| Rollback or compensation | Principle VIII (rollback vs compensation distinction) |
| Safe-stop | Principle VIII (safe-stop semantics), III (timeouts resolve safely) |
| Dynamic replanning | Principle II, I (impact analysis on upstream change) |
| Audit-grade evidence | Principle IX (append-only, tamper-evident, verifiable) |
| TDD | Principle IV |
| Evidence-based completion | Principle XI |

### Sync impact report (constitution v1.0.0)

- **Version change**: template (unversioned) → 1.0.0 (initial adoption; MAJOR baseline).
- **Principles defined**: I Specification Before Implementation; II Explicit Agentic Orchestration;
  III Human Governance; IV Test-Driven Engineering; V Security and Privacy by Design; VI Compliance
  and Change-Control Policy Enforcement; VII Architecture and Maintainability; VIII Reliability and
  Recovery; IX Observability and Auditability; X Traceability and Repository Integrity; XI
  Evidence-Based Completion. The template's five principle slots were expanded to eleven, as the
  guide specifies.
- **Sections added**: Delivery Constraints; Development Workflow and Quality Gates; Governance
  (with Delegated Provisional Progression).
- **Sections removed**: none (template placeholders only).
- **Templates**: `.specify/templates/*` are read at runtime and were not modified; the plan
  template's "Constitution Check" gate is satisfied by the Governance rule on compliance assessment.
- **Deferred items**: `TODO(RATIFICATION_DATE)`, set when the candidate ratifies G1.

---

## G2 — Requirements (feature specification)

- **Artifact**: [`specs/001-agentic-url-shortener/spec.md`](../../specs/001-agentic-url-shortener/spec.md)
  and its quality checklist [`checklists/requirements.md`](../../specs/001-agentic-url-shortener/checklists/requirements.md)
- **Assistant recommendation**: approve, after deciding the three open clarification markers at G3.
  Pay particular attention to every requirement tagged `[Derived · …]`: they are engineering
  inferences, not assignment text. Also review the three scenario payloads (custom alias, click
  limit, default expiry) and the Proposed Validation Targets (PVT-01..PVT-26), which are
  assumptions, not client requirements.
- **Decision**: _pending_ · **Decided by**: _pending_ · **Date**: _pending_ · **Notes**: _pending_

### Gate 2 review checklist (guide: reject a specification that ...)

| Rejection criterion | Where the specification addresses it |
|---------------------|--------------------------------------|
| chooses technologies prematurely | No language, framework, database, or platform named; CON-03 |
| silently resolves ambiguity | Ambiguity Register AMB-01..AMB-11; the 3 `[NEEDS CLARIFICATION]` markers were resolved only provisionally at clarify (G3); assumptions ASM-01..ASM-11 labeled as proposals |
| lacks negative behavior | `(negative)` acceptance scenarios in US1-US6; Edge Cases section |
| omits a required scenario | SCN-A, SCN-B, SCN-C with fixed inputs and evidence lists |
| presents orchestration as a linear chain | FR-ORC-02/04/05/06, parallel `‖` paths in every scenario, FR-RPL-* |
| lacks human approval | FR-GOV-01..09, FR-RDY-02/04, US3 |
| lacks recovery or safe-stop | FR-REL-01..11, drills RDR-01..07 |
| cannot be tested | every FR is a MUST statement with an observable outcome; NFR verification column |
| cannot be traced | stable identifiers; provenance tags `[Confirmed · …]` / `[Derived · …]`; Traceability Notes |

---

## G3 — Clarifications

- **Artifact**: `## Clarifications` → `### Session 2026-09-26` in
  [`spec.md`](../../specs/001-agentic-url-shortener/spec.md), and its Ambiguity Register.
- **How to decide**: answer each question below with an option letter (or your own short answer).
  Where your answer differs from the provisional one, tell the assistant, e.g. "G3 Q2: A". It
  will record your words, re-run the impact analysis (`/speckit-analyze`), and replan the affected
  artifacts.
- **Decision**: _pending_ · **Decided by**: _pending_ · **Date**: _pending_ · **Notes**: _pending_

### Q1 — Should runtime stage agents call an external AI (LLM) service, or run as deterministic rule-based workers without network access?

*Type*: architectural decision. *Why it matters*: decides whether runs are reproducible offline,
whether the prototype needs API keys, and what the implementation stage actually does.

| Option | Description | Engineering impact |
|--------|-------------|--------------------|
| **A (provisional)** | Deterministic rule-based agents; no external AI at runtime; an AI-backed agent can be registered later with the deterministic agent as fallback | Reproducible, offline, testable; no secrets; "intelligence" limited to explicit rules and knowledge models |
| B | LLM-backed agents for analysis, design, and documentation, with deterministic fallback | Richer outputs; needs API key and network; non-deterministic tests need mocks; adds a secret to manage |
| C | LLM agents that also generate and apply source code at runtime | Highest autonomy and risk; unreviewed code changes at runtime conflict with Principle III |

**Recommended: A**, because the constitution requires local runnability without paid services or real
secrets. It also keeps the assessment focus on orchestration; B is recorded as a deferred
enhancement.

### Q2 — Should creating short links be open to anonymous clients, or require an authenticated API consumer?

*Type*: architectural decision (security posture). *Why it matters*: sets the abuse surface of the
public API and what the security tests must prove.

| Option | Description | Engineering impact |
|--------|-------------|--------------------|
| A | Anonymous creation, per-IP rate limiting; control plane authenticated | Simplest demo; open to anonymous phishing/spam link creation; IP limits weak behind NAT |
| **B (provisional)** | Creation, metadata, and analytics require an authenticated API consumer; redirects public | Secure default; per-consumer limits and accountability; demo needs a consumer credential |
| C | Everything authenticated, including redirects | Breaks the core use of a shortener (sharing links) |

**Recommended: B**, because Principle V requires secure defaults, and a financial-services
reviewer will expect authenticated write APIs.

### Q3 — When the same long URL is shortened twice, should the service return the existing short link or create a new one?

*Type*: functional rule (proposed assumption). *Why it matters*: decides uniqueness rules in the
data model and what idempotency means.

| Option | Description | Engineering impact |
|--------|-------------|--------------------|
| **A (provisional)** | Always create a new link; idempotency keys make retries safe | Simple; no cross-consumer linkage; compatible with per-link expiry, limits, aliases |
| B | Global de-duplication by target URL | Reveals that another consumer shortened the URL; conflicts with per-link options |
| C | De-duplicate per consumer and identical options | Extra lookup index; surprising when a consumer wants two tracked links |

**Recommended: A.**

### Q4 — When a released capability is withdrawn (by rollback or compensation), what should happen to links that consumers already created with it?

*Type*: architectural decision (compensation semantics). *Why it matters*: consumers may already
have shared these links, so this is a case where rollback is impossible and compensation
decides the outcome.

| Option | Description | Engineering impact |
|--------|-------------|--------------------|
| **A (provisional)** | Withdrawal stops new use only; existing links keep their stored behavior | Non-destructive; withdrawal is a true rollback of release state; stored rules (for example click limits) are never silently dropped |
| B | Disable affected links | Destructive for consumers; would require a human approval gate for every withdrawal |
| C | Delete affected links | Irreversible data loss; prohibited for automated execution |

**Recommended: A.**

### Q5 — How accurate must click analytics be?

*Type*: architectural decision. *Why it matters*: trades redirect speed against counting accuracy
and decides whether redirects may be cached.

| Option | Description | Engineering impact |
|--------|-------------|--------------------|
| **A (provisional)** | Exact: count synchronously before responding; temporary, non-cacheable redirects | One extra write per redirect; exact counts (required anyway for click-limited links in SCN-B) |
| B | Eventually consistent: asynchronous counting | Faster redirects; clicks can be lost on crash; needs a queue or buffer |
| C | Best effort: permanent, cacheable redirects | Lowest load; repeat visits invisible; analytics undercount |

**Recommended: A.**

### Deferred questions (question quota of 5 reached)

| ID | Question | Impact | Current assumption | Owner | Decision point |
|----|----------|--------|--------------------|-------|----------------|
| AMB-04 | How long do gates wait before safe-stop? | Governance, demo timing | Configurable per gate; default 24 h (PVT-13) | Candidate | G3 |
| AMB-07 | What is the audit retention period? | Compliance policy | 365 days (PVT-18) | Candidate | G3 |
| AMB-08 | Is safe-stop resumable? | State model | Terminal; continuing requires a new run (ASM-08) | Candidate | G3 |
| AMB-09 | Which capabilities serve as scenario payloads? | Scenario credibility | Custom alias, click limit, default expiry | Candidate | G2 |
| AMB-11 | Who may answer clarification questions? | Governance | A human with the approver role (ASM-11) | Candidate | G3 |

### Clarification findings recorded in the specification

- **Non-testable wording fixed**: "promptly" (FR-OPS-02, US1-13) now references PVT-24; "removed
  afterwards" (FR-ORC-16) now means "no later than the run's terminal state"; "oversize fields"
  (FR-ORC-01) now references PVT-26; bounded values in FR-LNK/ANL/REL/GOV/ORC now cite their
  PVT targets.
- **Missing negative behavior added**: unauthenticated creation, metadata, and analytics requests
  are rejected (US1 scenario 14).
- **Human approval points**: clarification, architecture approval, policy exception, change
  control after approval, release (including acceptance of limitations) — FR-GOV-01.
- **Rollback impossible, compensation required**: listed under Edge Cases (capability exposure,
  synthetic check data, append-only audit, superseded human decisions); FR-REL-05 limits
  compensation to synthetic data created by the same run.
- **Contradictions found**: none remaining; the anonymous-access wording was replaced throughout.

---

## G4 — Architecture (plan and ADRs)

- **Artifacts**: [`plan.md`](../../specs/001-agentic-url-shortener/plan.md),
  [`research.md`](../../specs/001-agentic-url-shortener/research.md),
  [`data-model.md`](../../specs/001-agentic-url-shortener/data-model.md),
  [`contracts/`](../../specs/001-agentic-url-shortener/contracts/), and the 19 ADRs indexed in
  [`docs/adr/README.md`](../adr/README.md) (inventory, decisions needing approval, deferrable
  decisions, plan conflicts found and fixed, missing information).
- **How to decide**: for each ADR, change `## Status` to `Accepted (YYYY-MM-DD, <your name>)` or
  `Rejected (…, reason)`, then record the overall decision below. A rejected ADR sends the work
  back to `/speckit-plan`, followed by `/speckit-analyze`.
- **Assistant recommendation**: accept all 19. Review ADR-005 (custom engine), ADR-017
  (deterministic agents), and ADR-011 (replanning semantics) most carefully: they shape most of
  the code.
- **Decision**: _pending_ · **Decided by**: _pending_ · **Date**: _pending_ · **Notes**: _pending_

| ADR | Candidate decision |
|-----|--------------------|
| ADR-001 … ADR-019 | _pending_ (record per ADR in each file's Status section) |

---

## G5 — Implementation may start (pre-implementation baseline)

- **Artifacts**: [`pre-implementation-analysis.md`](../assessment/pre-implementation-analysis.md)
  (run 1: 1 CRITICAL, 1 HIGH, 19 MEDIUM/LOW; run 2: 0 CRITICAL, coverage 100%) and
  [`pre-implementation-review.md`](../assessment/pre-implementation-review.md) (verdict PROCEED
  WITH CONDITIONS; RC-1..RC-8 applied upstream), [`tasks.md`](../../specs/001-agentic-url-shortener/tasks.md)
  (132 tasks), and the seven custom checklists in
  [`specs/001-agentic-url-shortener/checklists/`](../../specs/001-agentic-url-shortener/checklists/).
- **Checklist status**: 0 of 167 custom checklist items reviewed. They are reviewer-owned; the
  assistant did not mark any.
- **Assistant recommendation**: approve the start of implementation, subject to your ratification
  of G1–G4. Until then, implementation proceeds **provisionally** under the Delegated Provisional
  Progression clause. Any rejection reopens the affected tasks through the SpecKit stages
  (`/speckit-specify` → … → `/speckit-analyze`).
- **Decision**: _pending_ · **Decided by**: _pending_ · **Date**: _pending_ · **Notes**: _pending_

### Pending human review items raised during implementation

| Item | Raised by | Status |
|------|-----------|--------|
| Review of the brownfield impact analysis before the click-limit code (task T086) | tasks.md | pending — added when T086 runs |
| Change-control approval of contract versions 1.1.0 and 1.2.0 (task T112) | tasks.md | pending |
| Exception for any accepted dependency-scan finding (task T128) | tasks.md | pending if applicable |
