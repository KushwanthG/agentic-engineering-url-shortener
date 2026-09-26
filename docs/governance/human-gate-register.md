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

These are the candidate's own decisions and are treated as approved inputs: **Spring Boot** as
the framework (first instruction) and **Java 21** as the language level (third instruction). The
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
| G3 | Clarifications decided | NOT YET REACHED | — | — |
| G4 | Architecture and ADRs accepted | NOT YET REACHED | — | — |
| G5 | Implementation may start | NOT YET REACHED | — | — |
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
  limit, default expiry) and the Proposed Validation Targets (PVT-01..PVT-25), which are
  assumptions, not client requirements.
- **Decision**: _pending_ · **Decided by**: _pending_ · **Date**: _pending_ · **Notes**: _pending_

### Gate 2 review checklist (guide: reject a specification that ...)

| Rejection criterion | Where the specification addresses it |
|---------------------|--------------------------------------|
| chooses technologies prematurely | No language, framework, database, or platform named; CON-03 |
| silently resolves ambiguity | Ambiguity Register AMB-01..AMB-09; 3 `[NEEDS CLARIFICATION]` markers; assumptions ASM-01..ASM-10 labeled as proposals |
| lacks negative behavior | `(negative)` acceptance scenarios in US1-US6; Edge Cases section |
| omits a required scenario | SCN-A, SCN-B, SCN-C with fixed inputs and evidence lists |
| presents orchestration as a linear chain | FR-ORC-02/04/05/06, parallel `‖` paths in every scenario, FR-RPL-* |
| lacks human approval | FR-GOV-01..09, FR-RDY-02/04, US3 |
| lacks recovery or safe-stop | FR-REL-01..11, drills RDR-01..07 |
| cannot be tested | every FR is a MUST statement with an observable outcome; NFR verification column |
| cannot be traced | stable identifiers; provenance tags `[Confirmed · …]` / `[Derived · …]`; Traceability Notes |
