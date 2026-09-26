# Agentic Software Engineering System: URL Shortener Constitution

This constitution governs an engineering assessment submission. Reviewers assess working behavior,
architecture, engineering decisions, the AI-assisted development process, task decomposition, testing
discipline, governance, traceability, change history, documentation, and engineering judgment.
The URL shortener is the demonstration domain. The central differentiator is a governed, stateful,
non-linear agentic orchestration system that turns requirements into reviewable engineering outcomes
across the software development lifecycle.

## Core Principles

### I. Specification Before Implementation

- Production code MUST NOT be written for a behavior until that behavior is described in the feature
  specification and the specification has passed its human gate (or is progressing under a recorded
  delegation, see Governance: Delegated Provisional Progression).
- Every requirement MUST be testable and carry a stable identifier; functional (FR) and
  non-functional (NFR) requirements MUST be kept in separate, distinguishable lists.
- Every ambiguity MUST be either resolved by a recorded human decision or recorded as an explicit,
  labeled assumption awaiting approval. Silent resolution is prohibited.
- A material change to an upstream artifact (constitution, specification, plan, contract, ADR) MUST
  trigger a recorded downstream impact analysis before dependent work continues.
- Existing code MUST NOT be treated as an undocumented substitute for the specification; where they
  disagree, the specification wins and the discrepancy is recorded.

Rationale: reviewers must be able to trace every behavior to an agreed requirement.

### II. Explicit Agentic Orchestration

- The orchestration system MUST be more than sequential agent chaining. It MUST execute an explicit,
  persisted dependency graph (or an equivalent explicit state model).
- The orchestration MUST support sequential paths, parallel paths, synchronization (join) points,
  conditional branching, dynamic replanning, interruption, and resumption, and each capability MUST
  be demonstrated by an executed test.
- Every stage MUST declare its inputs, outputs, entry criteria, exit criteria, responsible actor,
  timeout, retry policy, and failure behavior.
- Workflow state, cross-stage context, artifact provenance, and decision lineage MUST be persisted
  so that a run can be reconstructed and resumed after interruption.
- Agent autonomy MUST be bounded (explicit limits on retries, duration, and permitted actions),
  observable (every agent action emits evidence), and auditable.

Rationale: orchestration quality is the primary assessment criterion; a linear chain earns no credit.

### III. Human Governance (NON-NEGOTIABLE)

- Humans own requirements interpretation, architecture, technology selection, security-sensitive
  decisions, material exceptions, destructive or irreversible changes, risk acceptance, release
  readiness, and final quality.
- High-impact actions MUST require an explicit, recorded human approval; an agent MUST NOT be able
  to approve, and the approval path MUST NOT be bypassable through any API or configuration.
- Approval, rejection, escalation, timeout, and safe-stop behavior MUST be defined for every gate.
- Mandatory gates MUST NOT be skipped silently. Absence of a response MUST NOT be interpreted as
  approval; a gate that times out MUST resolve to a safe outcome, never to approval.
- The AI assistant MAY propose, compare, and recommend; it MUST NOT mark its own proposals approved,
  accepted, or ratified.

Rationale: controlled autonomy means agents execute within boundaries that humans set and own.

### IV. Test-Driven Engineering

- Domain and orchestration behavior MUST follow red-green-refactor wherever technically practical:
  write a test, run it, confirm it fails for the expected reason, implement the minimum behavior,
  run it green, then refactor with tests green.
- Implementation-first work MUST NOT be described retrospectively as TDD.
- The test suite MUST include unit, integration, API contract, orchestration state-transition,
  reliability (retry/timeout/fallback/compensation/safe-stop/resume), security, and end-to-end tests.
- A task is complete only after its validation has actually been executed and passed; generated but
  unexecuted tests do not count.

Rationale: tests are the evidence that behavior matches the specification.

### V. Security and Privacy by Design

- All external input MUST be validated and normalized at the trust boundary before use.
- Accepted URL schemes MUST be restricted to an explicit allow-list; malicious-redirect,
  internal-address, and abuse scenarios MUST be addressed and tested.
- Secrets, credentials, and personal data MUST NOT be written to logs, audit events, artifacts, or
  the repository. Demonstration credentials MUST be labeled as non-production.
- Configuration MUST default to the secure option (e.g., fault injection and demo credentials are
  disabled unless explicitly enabled).
- Least privilege and explicit trust boundaries MUST be applied; authentication assumptions, rate
  limiting, threat scenarios, and security trade-offs MUST be documented.
- Release readiness MUST include dependency-risk and secret checks.

Rationale: a URL shortener is an abuse magnet (phishing, open redirect, enumeration).

### VI. Compliance and Change-Control Policy Enforcement

- Policy guardrails covering security, compliance, privacy, audit retention, approved dependencies,
  software licensing, and change control MUST be defined as a versioned policy set.
- Every orchestration run MUST record the policy-set version it was evaluated against.
- Every applicable policy check MUST produce exactly one outcome: `PASS`, `FAIL`,
  `EXCEPTION-REQUESTED`, or `NOT-APPLICABLE`.
- A `FAIL` on a mandatory policy MUST block downstream progression of the affected run.
- A policy exception MUST require explicit human approval and MUST record: the policy, reason,
  scope, approving authority, compensating control, approval timestamp, and expiry or review
  condition. An expired exception MUST be treated as unapproved.
- Changes to approved requirements, architecture, schemas, workflow states, security controls, or
  release criteria MUST pass through impact analysis and a recorded change approval.
- Release readiness MUST fail while any mandatory policy remains violated or relies on an
  unapproved or expired exception. Policy outcomes and exceptions MUST appear in audit evidence.

Rationale: governance that does not block is decoration.

### VII. Architecture and Maintainability

- Domain logic, API delivery, persistence, orchestration, policy enforcement, telemetry, and
  infrastructure concerns MUST live in separate modules with explicit interfaces; these boundaries
  MUST be enforced by automated tests.
- The application plane (URL shortener) MUST NOT depend on the orchestration (control) plane; the
  control plane MUST reach the application plane only through declared ports.
- External dependencies SHOULD be accessed through interfaces so they can be substituted in tests.
- Material architectural decisions MUST be recorded as ADRs, including rejected alternatives.
- Complexity MUST be justified by a requirement or by demonstrability; unjustified distributed
  infrastructure is prohibited.

Rationale: modular, replaceable components keep the prototype reviewable and evolvable.

### VIII. Reliability and Recovery

- Failures MUST be classified as transient or permanent; only transient failures are retry-eligible.
- Retries MUST be bounded, with explicit backoff and per-attempt timeouts. Unbounded retry is
  prohibited.
- Repeatable operations MUST be idempotent; duplicate executions MUST be detected and discarded.
- Fallback behavior MUST be defined where a degraded path exists and MUST be recorded when used.
- Rollback (restoring a prior state that is fully under system control) MUST be distinguished from
  compensation (a semantic counter-action for side effects that cannot be undone); rollback MUST
  NOT be claimed where it is not technically feasible.
- Safe-stop conditions MUST be defined; entering safe-stop MUST halt new work, apply compensation,
  persist state, and produce a deterministic terminal outcome.
- Interrupted runs MUST be resumable from persisted state without repeating completed side effects.
- Evidence of success, failure, retry, fallback, compensation, recovery, and latency MUST be captured.

Rationale: reliability claims must be demonstrable, not asserted.

### IX. Observability and Auditability

- Every orchestration run MUST carry a correlation (run) identifier propagated to logs, metrics,
  and audit events.
- State transitions, decisions, approvals, rejections, retries, failures, fallbacks, compensations,
  replanning events, and terminal outcomes MUST be recorded as audit events containing actor type,
  actor, action, timestamp, affected artifact or state, result, and reason.
- Audit evidence MUST be append-only and tamper-evident, and its integrity MUST be verifiable.
- Logs, metrics, traces, and workflow history MUST be sufficient to reconstruct an execution.
- Reliability metrics (success rate, failure rate, retry frequency, rollback/compensation frequency,
  MTTR, unrecovered failures, end-to-end latency) MUST be computable from recorded evidence.
- Demonstration measurements MUST be labeled as such and MUST NOT be presented as production data.

Rationale: an execution that cannot be reconstructed cannot be governed.

### X. Traceability and Repository Integrity (NON-NEGOTIABLE)

- Traceability MUST be maintained across requirement, scenario, decision, design, task,
  implementation, test, validation, documentation, and evidence.
- Commits MUST be small, logically coherent, and describe engineering intent; history MUST reflect
  the actual execution sequence.
- Approval, test, execution, metric, log, trace, and operational evidence MUST NOT be fabricated.
  Simulated inputs, injected faults, mock dependencies, and illustrative examples MUST be labeled.
- AI-generated artifacts require human review and ownership; AI co-authorship MUST be disclosed.
- Documentation MUST be updated in the same task group as the behavior it describes.

Rationale: the repository is the evidence; its credibility depends on its truthfulness.

### XI. Evidence-Based Completion (NON-NEGOTIABLE)

A feature or task is complete only when all of the following hold:

1. Applicable requirements are identified.
2. Decisions and assumptions are recorded.
3. Acceptance criteria are satisfied.
4. Required tests have been executed successfully.
5. Security and reliability checks are complete.
6. Documentation is current.
7. Traceability is complete.
8. Residual risks and limitations are disclosed.
9. Required approval is recorded (or explicitly listed as pending ratification).
10. Evidence is available for reviewer verification.

Rationale: completion claims must be verifiable by someone who did not do the work.

## Delivery Constraints

- **Timebox**: the assessment is timeboxed to 2–3 days. Must-have scope (orchestration, governance,
  API/schema deliverables, three scenarios, tests, evidence, final reporting) MUST be delivered
  before optional enhancements; optional work is kept in a labeled deferred backlog and MUST NOT
  displace mandatory validation or reviewer evidence.
- **Local runnability**: the prototype MUST run end-to-end on a single developer machine with
  documented commands and MUST NOT require paid services or real secrets to build, test, or demo.
- **Production-grade discipline, not production-scale infrastructure**: engineering rigor is
  mandatory; distributed infrastructure is out of scope unless a requirement justifies it.
- **Technology neutrality of this document**: languages, frameworks, databases, and platforms are
  chosen in the technical plan and recorded as ADRs, not in this constitution.
- **Authority hierarchy** (highest first) when instructions conflict:
  1. Official assessment requirements
  2. This constitution
  3. Approved feature specification and clarifications
  4. Approved technical plan and contracts
  5. Approved Architecture Decision Records
  6. SpecKit-generated task plan
  7. Current implementation
  8. Informal conversation

## Development Workflow and Quality Gates

- **Lifecycle order** (SpecKit is the sole lifecycle framework): constitution → specify → clarify →
  requirements gate → plan → ADRs → architecture gate → checklist → tasks → analyze →
  pre-implementation review → implement (incremental TDD) → scenario demonstrations → converge →
  independent assessment → release-readiness decision.
- **Mandatory human gates** (owner: the human candidate for every gate):

  | Gate | Decision | Evidence reviewed |
  |------|----------|-------------------|
  | G1 | Constitution ratified | This document |
  | G2 | Requirements approved | `spec.md` |
  | G3 | Clarifications decided | Clarifications section, assumption register |
  | G4 | Architecture approved; each ADR accepted or rejected | `plan.md`, `docs/adr/` |
  | G5 | Implementation may start | analysis report, pre-implementation review |
  | G6 | Release readiness decided | convergence report, final summary |
  | G7 | Final submission | tagged repository |

- Gate status MUST be recorded in `docs/governance/human-gate-register.md`.
- **Autonomous execution boundary**: one coherent task group plus its tests, documentation, and
  traceability updates; then a checkpoint review and a commit.
- **Commits** follow Conventional Commits, one coherent change each, with AI co-authorship trailers.
- **Stop conditions**: implementation of a task MUST stop and escalate when it reveals a conflict
  with an upstream artifact, requires a change to an approved contract, ADR, state model, or
  security control, or cannot be validated.

## Governance

- **Supremacy**: this constitution supersedes all other practices except the official assessment
  requirements. Conflicts are resolved by the authority hierarchy above.
- **Compliance assessment during planning**: every plan MUST contain a Constitution Check that rates
  each principle `PASS`, `FAIL`, or `EXCEPTION-REQUESTED` with evidence. A `FAIL` blocks task
  generation until corrected upstream or covered by an approved exception.
- **Exceptions**: an exception is proposed in writing with the principle, reason, scope,
  compensating control, requested expiry or review condition, and requester; it takes effect only
  after the human candidate approves it, and it is recorded with the approval timestamp.
- **Non-waivable principles**: III (Human Governance), X (Traceability and Repository Integrity), and
  XI (Evidence-Based Completion) cannot be waived; nor can the rule that secrets never enter logs
  or the repository (Principle V).
- **Conflict resolution between principles**: security, privacy, and human governance prevail over
  delivery speed and convenience; when two principles conflict, the more restrictive interpretation
  applies until the human candidate records a decision.
- **Release blocking**: release readiness is `NOT READY` while any of the following holds: an
  unresolved CRITICAL or HIGH constitutional finding, an unratified mandatory gate, a failed
  mandatory policy check, an unapproved or expired exception, or an unexecuted mandatory validation.
- **Delegated Provisional Progression**: when the human candidate has delegated end-to-end execution
  (recorded verbatim in the gate register), downstream stages MAY proceed before a gate is ratified,
  provided that: the gate is recorded as `PENDING RATIFICATION` with the assistant's recommendation;
  no artifact is labeled Approved, Accepted, or Ratified by the assistant; ADRs remain `Proposed`;
  and release readiness remains blocked until every mandatory gate is ratified by the human.
- **Amendments**: amendments are proposed with a sync impact report, approved by the human
  candidate, and versioned semantically: MAJOR for removed or redefined principles, MINOR for new or
  materially expanded principles or sections, PATCH for clarifications. Each amendment MUST be
  followed by an impact review of the specification, plan, and tasks.
- **Review cadence**: constitutional compliance is re-checked at `/speckit.analyze`, at every
  task-group checkpoint, and at `/speckit.converge`.

**Version**: 1.0.0 | **Ratified**: TODO(RATIFICATION_DATE): pending human ratification at Gate G1 | **Last Amended**: 2026-09-26
