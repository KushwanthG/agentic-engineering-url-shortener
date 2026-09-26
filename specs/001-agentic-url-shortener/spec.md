# Feature Specification: Agentic Software Engineering System — URL Shortener

**Feature Branch**: `001-agentic-url-shortener`

**Created**: 2026-09-26

**Status**: Draft — pending human approval at Gate G2 (see
[`docs/governance/human-gate-register.md`](../../docs/governance/human-gate-register.md))

**Input**: User description: SDD guide Prompt 2 — "Build a production-grade prototype titled
'Agentic Software Engineering System: URL Shortener'" — together with the official assignment brief
("Interview Assignment: Build an Agentic Software Engineering System – URL Shortener", sections 1–7).

> **Source handling.** The assignment brief is classified *Schwab Internal* and the SDD guide is the
> candidate's private methodology document, so neither is committed to this repository. Requirements
> below paraphrase them and cite sections as `A§n` (assignment; `A§4.4` = core requirement 4) and
> `G§n` (guide; `G§7` = Prompt 2). `C:n` cites a constitution principle. `D` marks a requirement
> derived by engineering analysis; derived requirements need explicit approval at Gate G2.

## Overview

**Business objective** (A§1, G§7): demonstrate how a governed agentic execution system transforms a
software requirement into a reviewable engineering outcome across the whole software development
lifecycle, with humans owning oversight, approvals, and final quality.

The system has two planes:

- **Application plane — URL shortener** (the demonstration domain, A§2): short-link creation,
  short-code generation, redirect resolution, basic redirect analytics, expiry, validation,
  reliability controls, failure handling, and operational health.
- **Control plane — agentic SDLC orchestration** (the primary capability, A§4.4): takes a
  requirement through ingestion, normalization, ambiguity detection, human clarification,
  decomposition, design, implementation, testing, documentation, security and compliance
  validation, release readiness, and a final engineering summary. It runs as a governed, stateful,
  non-linear dependency graph with human gates.

The control plane governs change to the application plane: a successful run ends with the
governed **release** of a URL-shortener capability. Capabilities are delivered through this
repository's development process and stay unavailable to consumers until a run releases them.

### Glossary

| Term | Meaning |
|------|---------|
| Run | One execution of the orchestration system for one submitted requirement |
| Plan | The run's dependency graph of stages; every change creates a new plan version |
| Stage | One node of the plan, performed by an agent or by a human |
| Agent | An automated worker that performs one stage type within declared permissions |
| Gate | A stage that requires a human decision |
| Artifact | A versioned stage output with recorded provenance and content fingerprint |
| Decision | A recorded choice (approval, rejection, clarification answer, exception, change request, fallback use, replan) with actor and rationale |
| Capability | A URL-shortener behavior that a run can release to consumers or withdraw |
| Release | Making a capability available to all consumers |
| Rollback | Restoring state that is fully under system control to its previous value |
| Compensation | A semantic counter-action for a side effect that cannot simply be undone |
| Safe-stop | Deterministic halt: no new stages start, compensation is applied, state is persisted, and a terminal outcome is recorded |
| Replan | Recomputing the plan and invalidating affected downstream work after an upstream change |
| Policy set | Versioned collection of guardrail policies evaluated during runs |
| Autonomy budget | Per-run limits on automated effort (stage attempts, processing time) |
| Synthetic data | Data created by automated checks; always labeled and cleaned up |

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Shorten, share, and track a link (Priority: P1)

**Actor**: API consumer. An API consumer submits a long URL and receives a short link. Anyone who
follows the short link is redirected to the original URL. The consumer can see how often the link
was used, and a link can be given an expiry time.

**Why this priority**: the demonstration domain must work end-to-end; every orchestration scenario
changes it.

**Independent Test**: create a link, follow it, read its analytics, and observe expiry, without
using the orchestration system.

**Acceptance Scenarios**:

1. **Given** a valid `https` URL, **When** the consumer creates a short link, **Then** a unique
   short code, the full short URL, the normalized target URL, and the creation time are returned.
   *(FR-LNK-01, FR-LNK-04)*
2. **Given** an active short link, **When** a client resolves its code, **Then** the client is
   redirected to the target URL and the redirect is marked as not cacheable. *(FR-RED-01, FR-RED-02)*
3. **Given** a link resolved three times, **When** the consumer reads its analytics, **Then** the
   total click count is 3, the last-accessed time equals the third resolution time, and today's
   daily count is 3. *(FR-ANL-01, FR-ANL-03)*
4. **Given** a link whose expiry time has passed, **When** it is resolved, **Then** the expired
   outcome is returned and no redirect occurs. *(FR-RED-04)*
5. *(negative)* **Given** a URL whose scheme is `javascript`, `data`, `file`, `ftp`, or `mailto`,
   **When** creation is requested, **Then** it is rejected with a validation error naming the
   unsupported scheme and no link is created. *(FR-LNK-02)*
6. *(negative)* **Given** a URL that embeds credentials, has no host, targets a loopback, private,
   or link-local address, names `localhost`, or points at the shortener's own host, **When**
   creation is requested, **Then** it is rejected with a specific reason code. *(FR-LNK-03)*
7. *(negative)* **Given** an unknown code, **When** it is resolved, **Then** the not-found outcome
   is returned. *(FR-RED-03)*
8. *(negative)* **Given** an expiry time in the past or beyond the maximum horizon, **When**
   creation is requested, **Then** it is rejected. *(FR-LNK-12)*
9. **Given** an identical creation request repeated with the same idempotency key, **When** both
   complete, **Then** the same short link is returned twice and only one link exists;
   *(negative)* reusing the key with a different payload is rejected. *(FR-LNK-08)*
10. *(negative)* **Given** a client that has exceeded the creation rate limit, **When** it requests
    another link, **Then** the request is rejected with a retry-after indication. *(FR-LNK-10)*
11. **Given** 200 concurrent resolutions of the same active link, **When** all complete, **Then**
    the total click count equals the number of successful redirects. *(FR-ANL-05)*
12. **Given** analytics recording fails, **When** an active link without click-dependent rules is
    resolved, **Then** the redirect still succeeds and the failure is counted. *(FR-ANL-04)*
13. **Given** the link store is unavailable, **When** creation or resolution is requested, **Then**
    a temporarily-unavailable outcome is returned promptly and readiness reports not-ready.
    *(FR-OPS-01, FR-OPS-02)*

---

### User Story 2 - Deliver a well-defined requirement through the governed lifecycle (Priority: P1)

**Actor**: software engineer. The engineer submits a complete, consistent, testable requirement.
The system normalizes it, records why clarification is not required, decomposes and designs it,
obtains approvals, verifies it through executed checks on parallel branches, evaluates policies,
and releases it after release approval. This is scenario **SCN-A**.

**Why this priority**: orchestration is the assessment's critical differentiator (A§4.4).

**Independent Test**: submit the SCN-A requirement, supply the gate decisions, and observe the run
reach `COMPLETED` with the capability released.

**Acceptance Scenarios**:

1. **Given** an authenticated engineer, **When** they submit a requirement with a title and a
   narrative, **Then** a run is created with a run identifier, a persisted plan, and the recorded
   policy-set version. *(FR-ORC-01, FR-ORC-02, FR-POL-01)*
2. **Given** the SCN-A requirement, **When** requirement analysis completes, **Then** the
   quality-check record lists the completeness, consistency, testability, policy, and
   architecture-boundary checks as passed, states why clarification is not required, and the
   clarification stage is skipped with that reason. *(FR-ORC-13, FR-ORC-06)*
3. **Given** the design has been approved, **When** implementation completes, **Then** testing and
   security verification run concurrently with each other and with documentation (overlapping
   execution intervals are recorded), and validation starts only after all of them succeed.
   *(FR-ORC-04, FR-ORC-05, FR-AUD-06)*
4. **Given** a run waiting at a gate, **When** the engineer inspects it, **Then** they see every
   stage's state, artifact versions, pending human actions, and the timeline. *(FR-ORC-09)*
5. *(negative)* **Given** a plan containing a cycle or an unknown dependency, **When** the run
   would start, **Then** execution is refused and the reason is recorded. *(FR-ORC-02)*
6. *(negative)* **Given** a synchronization stage with one failed predecessor, **Then** the
   synchronization stage never starts. *(FR-ORC-05)*
7. **Given** release approval by an authorized release owner, **When** the release stage completes,
   **Then** the custom-alias capability is available to consumers and a final engineering summary
   exists for the run. *(FR-RDY-03, FR-ORC-17, FR-CAP-02)*

---

### User Story 3 - Govern high-impact decisions (Priority: P1)

**Actor**: human reviewer or approver. Approvers decide clarification, architecture, and policy
exception gates. Their decisions are enforced, bound to what they reviewed, and cannot be bypassed.

**Why this priority**: controlled autonomy is a core requirement (A§4.7, C:III).

**Independent Test**: drive a run to each gate type and exercise approve, reject, unauthorized,
self-approval, and deadline paths.

**Acceptance Scenarios**:

1. **Given** a run waiting at architecture approval, **When** an approver who is not the requester
   approves with a rationale, **Then** the decision is recorded with actor, role, timestamp,
   rationale, and fingerprints of the reviewed artifacts, and dependent stages start.
   *(FR-GOV-03, FR-GOV-09)*
2. *(negative)* **Given** a run waiting at a gate, **When** an unauthenticated caller, a principal
   without the required role, or an automated agent attempts the decision, **Then** it is refused,
   the gate stays open, and the attempt is audited. *(FR-GOV-03)*
3. *(negative)* **Given** the requester of a run, **When** they try to approve their own run's
   architecture or release gate, **Then** the attempt is refused. *(FR-GOV-04)*
4. **Given** an approved design, **When** an upstream change alters an artifact the approval was
   bound to, **Then** the approval is invalidated and the gate re-opens. *(FR-GOV-05)*
5. **Given** a gate with no decision by its deadline, **When** the deadline passes, **Then** the
   run safe-stops and is never approved by default. *(FR-GOV-07)*
6. **Given** an approver rejects a gate, **Then** completed side effects are compensated and the
   run ends `REJECTED`. *(FR-GOV-06)*
7. **Given** a gate is waiting, **Then** stages that do not depend on the gate continue to run.
   *(FR-GOV-02)*

---

### User Story 4 - Decide release readiness and operate runs safely (Priority: P2)

**Actor**: release owner. The release owner sees a computed readiness outcome, approves or rejects
the release, and can pause, resume, or safe-stop runs.

**Why this priority**: release readiness is a mandatory lifecycle stage (A§4.4); run operations
provide the safe-stop and resumption controls.

**Independent Test**: exercise readiness outcomes, release approval, post-release failure,
pause/resume, and operator safe-stop on runs with simulated faults.

**Acceptance Scenarios**:

1. **Given** all validations passed and every mandatory policy is `PASS`, **When** readiness is
   computed, **Then** the outcome is `READY`; with an approved, unexpired exception or a failed
   advisory policy it is `READY WITH ACCEPTED LIMITATIONS`; with a mandatory `FAIL` and no approved
   exception it is `NOT READY`. *(FR-RDY-01)*
2. *(negative)* **Given** readiness `NOT READY`, **When** the release owner attempts to approve the
   release, **Then** the approval is refused. *(FR-RDY-02)*
3. *(negative)* **Given** a principal without the release-owner role, **When** they attempt to
   approve a release, **Then** the attempt is refused. *(FR-RDY-04)*
4. **Given** post-release verification fails, **Then** the capability is withdrawn, the run
   safe-stops, and the compensation is recorded. *(FR-RDY-03, FR-REL-04)*
5. **Given** an active run, **When** the release owner pauses it, **Then** no new stage starts,
   in-flight stages finish, and after resumption the run continues where it paused. *(FR-REL-07)*
6. **Given** an active run, **When** the release owner requests safe-stop, **Then** the run halts
   deterministically with compensation applied. *(FR-REL-06)*

---

### User Story 5 - Change existing behavior safely (Priority: P2)

**Actor**: software engineer. The engineer submits a change to existing URL-shortener behavior.
Before any design or implementation, the system identifies what the change touches. This is
scenario **SCN-B**.

**Why this priority**: brownfield reasoning is a core requirement (A§4.3).

**Independent Test**: submit the SCN-B requirement and inspect the impact-analysis artifact and
the regression-testing results.

**Acceptance Scenarios**:

1. **Given** the SCN-B requirement, **When** the run executes, **Then** impact analysis completes
   before design and identifies impacted components, interfaces, data flows, tests, documentation,
   regression risks, and rollout/rollback considerations derived from the current codebase.
   *(FR-ORC-14)*
2. **Given** a completed impact analysis, **Then** the plan contains regression testing alongside
   new-behavior testing, and the architecture approval presents the data-compatibility and
   rollback considerations. *(FR-ORC-06, FR-GOV-01)*
3. **Given** the capability is released, **Then** links created before the release remain
   unlimited and new links can be click-limited. *(FR-CAP-03)*
4. *(negative)* **Given** regression testing fails, **Then** the run does not reach release
   approval and ends in safe-stop after compensation. *(FR-REL-06)*

---

### User Story 6 - Resolve an ambiguous requirement without unsafe progress (Priority: P2)

**Actors**: software engineer and approver. The engineer submits a vague and self-contradictory
requirement. The system detects the ambiguity, prevents unsafe progress, asks a human, records the
decision, re-plans, and resumes at the correct stage. This is scenario **SCN-C**.

**Why this priority**: ambiguity management is a core requirement (A§4.1, A§3).

**Independent Test**: submit the SCN-C requirement, confirm the run waits with no downstream work
started, answer the questions, and observe replanning and completion.

**Acceptance Scenarios**:

1. **Given** the SCN-C requirement, **When** analysis completes, **Then** each detected ambiguity
   is recorded with classification, severity, and affected items, and the run waits at
   clarification before any decomposition, design, or implementation starts.
   *(FR-ORC-13, FR-GOV-08)*
2. *(negative)* **Given** the run waiting for clarification, **When** anyone attempts to resume the
   run or decide a downstream gate, **Then** no downstream stage starts. *(FR-GOV-02, FR-ORC-11)*
3. **Given** an approver answers all blocking questions, **Then** the answers are recorded as
   decisions, a new requirement version is produced, affected artifacts are invalidated and
   regenerated, a new plan version with a recorded difference is created, and the run resumes
   from the correct stage. *(FR-GOV-08, FR-RPL-01, FR-RPL-02)*
4. **Given** a material change request after architecture approval, **Then** change-control
   approval is required before affected stages re-execute. *(FR-RPL-03)*

---

### User Story 7 - Verify evidence independently (Priority: P3)

**Actor**: assessment reviewer. The reviewer verifies that the evidence is authentic, complete,
and reproducible.

**Why this priority**: evidence integrity underpins every other claim (C:X, C:XI).

**Independent Test**: follow the quickstart from a clean checkout, run the scenarios, and query
audit integrity, lineage, and the reliability report.

**Acceptance Scenarios**:

1. **Given** a completed run, **When** the reviewer verifies its audit trail, **Then** integrity
   verification passes; **Given** any stored audit event is altered or removed, **Then**
   verification fails and identifies the first broken position. *(FR-AUD-02)*
2. **Given** recorded runs, **When** the reviewer requests the reliability report, **Then** it
   returns success rate, failure rate, retry frequency, compensation frequency, MTTR over recovered
   failure events only, unrecovered failure count, and latency statistics, together with the
   measurement population, exclusions, and a demonstration-data label. *(FR-AUD-04)*
3. **Given** any artifact, **When** the reviewer requests its lineage, **Then** the decisions and
   input artifact versions that produced it are listed. *(FR-AUD-03, FR-ORC-10)*
4. **Given** a clean checkout, **When** the reviewer follows the quickstart, **Then** the system
   builds, the automated tests pass, and the scenarios run with documented commands. *(SC-001)*

---

### Edge Cases

- A URL exactly at the maximum length is accepted; one character longer is rejected.
- Internationalized host names are normalized to their ASCII (punycode) form; scheme and host case
  is normalized; a default port is removed.
- Loopback addresses written as IPv6 (`[::1]`), IPv4-mapped IPv6, or integer/hex/octal IPv4
  encodings are recognized and rejected.
- A generated code collides with an existing code: a new code is generated; if the bounded attempts
  are exhausted, creation fails with a retryable error and no partial link exists.
- Two concurrent creations with the same idempotency key produce exactly one link.
- An expiry time equal to the current instant counts as expired.
- Resolving an expired link does not increment its click count.
- A requested alias equals an existing generated code: the request is rejected as a conflict.
- A click limit is reached under concurrency: exactly the limit's number of redirects succeed.
- The click cannot be recorded for a click-limited link: the redirect is refused (fail closed).
- A stage attempt completes after its timeout: the late result is discarded; the retry's result is
  used.
- The process restarts while a stage is running: the interrupted stage is re-executed; completed
  stages and completed side effects are not repeated.
- The process restarts while a gate is waiting: the gate remains pending with its original deadline.
- A decision arrives after the gate deadline: it is refused because the run has already safe-stopped.
- Two approvers decide the same gate concurrently: exactly one decision is recorded; the other is
  refused.
- A change request arrives while an affected stage is in flight: the in-flight result is discarded
  and the stage re-executes under the new plan version.
- Clarification answers introduce new ambiguity: another clarification round is opened; after the
  maximum number of rounds the run safe-stops.
- A policy exception expires between compliance evaluation and the release decision: readiness
  becomes `NOT READY` and release approval is refused.
- Compensation itself fails: the run safe-stops with a manual-intervention flag.
- A run requests fault injection while fault injection is disabled: the run is rejected at creation.

## Required Scenarios *(assignment deliverable, A§5)*

All three scenarios run through the implemented orchestration system and must be materially
different. Each lists its fixed input, expected orchestration path, gates, failure paths, required
evidence, and expected terminal outcome.

### SCN-A — Greenfield: well-defined new capability (custom aliases)

**Input** (fixed text, requirement `GF-001`):

> **Title**: Custom aliases for short links.
> **Type**: new capability.
> **Narrative**: As an API consumer, I want to optionally choose a custom alias when I create a
> short link so that I can share memorable links.
> **Acceptance criteria**:
> AC-1: Given the capability is released, when a consumer creates a link for a valid URL with the
> alias `spring-sale`, then the link is created and its short code is `spring-sale`.
> AC-2: Given the alias `spring-sale` is in use, when another link is created with the alias
> `spring-sale`, then creation is rejected with error code `ALIAS_CONFLICT`.
> AC-3: Given an alias containing characters other than letters, digits, hyphen, or underscore,
> when a link is created, then creation is rejected with error code `INVALID_ALIAS`.
> AC-4: Given an alias shorter than 3 or longer than 32 characters, when a link is created, then
> creation is rejected with error code `INVALID_ALIAS`.
> AC-5: Given a reserved alias such as `api`, when a link is created, then creation is rejected with
> error code `RESERVED_ALIAS`.
> AC-6: Given a link created with an alias, when the alias is resolved, then the client is
> redirected to the target URL.
> **Constraints**: aliases are case-sensitive, like generated codes, and share the code namespace.

**Interpretation**: complete, consistent, testable, and within the architecture boundary; it
changes the public API (new optional input, new error codes) and therefore requires architecture
approval, but it does not require clarification.

**Expected path**: ingestion → analysis (all quality checks pass; clarification skipped with
recorded reason) → decomposition ‖ threat assessment → design (API and schema impact recorded) →
architecture approval → implementation → testing ‖ security verification, with documentation
running in parallel from design approval → validation (synchronization) → compliance evaluation →
release approval → release → final summary.

**Failure paths exercised**: a transient testing failure recovers through bounded retry (simulated
fault, labeled). If material ambiguity emerges later, only the affected path is suspended
(FR-RPL-06).

**Expected terminal outcome**: `COMPLETED`, readiness `READY`, custom-alias capability released.

**Required evidence**: E-A1 requirement-quality record with the no-clarification rationale;
E-A2 decomposition with task dependencies; E-A3 API and schema impact; E-A4 plan graph and timeline
showing parallel execution and synchronization; E-A5 architecture and release decisions;
E-A6 per-acceptance-criterion test results; E-A7 policy outcomes with the policy-set version;
E-A8 audit trail with passing integrity verification; E-A9 final engineering summary.

### SCN-B — Brownfield: change existing behavior (click-limited links)

**Input** (fixed text, requirement `BF-001`):

> **Title**: Click-limited short links.
> **Type**: change to existing behavior.
> **Narrative**: As an API consumer, I want to limit how many times a short link can be used so
> that I can share one-time or limited-use links.
> **Acceptance criteria**:
> AC-1: Given the capability is released, when a link is created with a maximum of 2 clicks, then
> the link is created and reports its click limit.
> AC-2: Given a link with a maximum of 2 clicks, when it is resolved 3 times, then the first 2
> resolutions redirect and the third returns the expired outcome.
> AC-3: Given a link with a maximum of N clicks resolved concurrently by more than N clients, then
> exactly N resolutions redirect.
> AC-4: Given a link created before the capability was released, when it is resolved, then it
> continues to redirect without a limit.
> AC-5: Given a click limit outside 1 to 1,000,000, when a link is created, then creation is
> rejected with error code `INVALID_CLICK_LIMIT`.
> AC-6: Given a click-limited link whose click cannot be recorded, when it is resolved, then the
> redirect is refused with a temporarily-unavailable outcome.
> **Constraints**: existing links and consumers that do not use the limit must be unaffected; the
> change must be reversible without data loss.

**Interpretation**: well-defined change to existing redirect, analytics, and link-creation
behavior; it changes stored link data and the public API, and it conflicts with the existing
fail-open analytics rule for click-limited links (resolved by AC-6).

**Expected path**: ingestion → analysis → impact analysis ‖ decomposition ‖ threat assessment →
design (data-compatibility and rollback plan) → architecture approval → implementation → testing ‖
regression testing ‖ security verification, with documentation in parallel → validation →
compliance evaluation → release approval → release → final summary.

**Expected terminal outcome**: `COMPLETED`, readiness `READY`, click-limit capability released,
pre-existing links unaffected.

**Required evidence**: E-B1 impact analysis listing impacted components, interfaces, data flows,
tests, documentation, regression risks, and rollout/rollback considerations; E-B2 regression test
results; E-B3 data-compatibility and rollback statement; E-B4 plan graph showing the brownfield
stages; E-B5 decisions; E-B6 per-criterion test results; E-B7 policy outcomes; E-B8 audit trail;
E-B9 final engineering summary.

### SCN-C — Ambiguous: vague and conflicting request (link expiry)

**Input** (fixed text, requirement `AMB-001`):

> **Title**: Better link expiry.
> **Type**: not specified.
> **Narrative**: Links should expire after a while so old links don't pile up, but premium users'
> links should never expire. Also make the analytics better.
> **Acceptance criteria**: none.

**Expected detections**: an unquantified duration ("after a while"); an undefined concept
("premium users" — the system has no user tiers); conflicting expiry rules that depend on the
undefined concept; an unbounded scope item ("make the analytics better"); missing acceptance
criteria; unspecified change type.

**Expected path**: ingestion → analysis (blocking ambiguities) → clarification gate — the run waits
and no decomposition, design, or implementation starts → human answers → new requirement version →
re-analysis → replan (new plan version adds impact analysis and regression testing because the
clarified change modifies existing link-creation behavior) → brownfield path as in SCN-B →
release → final summary.

**Reference decisions** (simulated human input used by automated tests and labeled as such; a live
demonstration uses the human's own answers): D1 links created without an explicit expiry receive a
default expiry of 30 days; D2 user tiers are out of scope, so no link is exempt; D3 analytics
improvements are deferred to the backlog; D4 existing links are not changed.

**Expected terminal outcome**: `COMPLETED`, readiness `READY`, default-expiry capability released
with the decided duration.

**Required evidence** (G§20): the original input; each detected ambiguity with classification and
severity; affected requirements or components; workflow state before detection; the transition to
the waiting state; the reason implementation cannot continue; the clarification request; the
recorded human decision; the updated requirement; downstream impact analysis; the replanning event;
invalidated and regenerated artifacts; the resumed state; final validation; the audit trail; the
terminal outcome.

### Supplementary reliability and governance drills (RDR)

These drills supply executed evidence for the reliability and governance controls. Every injected
fault is simulated and labeled in all evidence.

| Drill | Demonstrates | Requirements | Expected outcome |
|-------|--------------|--------------|------------------|
| RDR-01 | Transient failure → bounded retry → recovery | FR-REL-01, FR-REL-02, FR-AUD-04 | `COMPLETED`; failure event recovered; MTTR sample |
| RDR-02 | Retry exhaustion → fallback | FR-REL-03, FR-RDY-01 | Stage succeeds degraded; fallback decision recorded |
| RDR-03 | Post-release verification failure → compensation | FR-RDY-03, FR-REL-04, FR-REL-05 | Capability withdrawn; `SAFE_STOPPED` |
| RDR-04 | Gate deadline passes | FR-GOV-07, FR-REL-06 | `SAFE_STOPPED`; no implicit approval |
| RDR-05 | Process interruption → automatic resumption | FR-REL-08, FR-REL-09, FR-ORC-08 | `COMPLETED`; completed stages not repeated |
| RDR-06 | Mandatory policy failure → exception approved / rejected | FR-POL-03, FR-POL-04, FR-RDY-01 | `READY WITH ACCEPTED LIMITATIONS` / `SAFE_STOPPED` |
| RDR-07 | Operator pause, resume, and safe-stop | FR-REL-06, FR-REL-07 | Deterministic halt with compensation |

## Requirements *(mandatory)*

Provenance tags: `[Confirmed · source]` = stated by the assignment, the candidate's guide, or the
constitution; `[Derived · parent]` = engineering derivation that requires approval at Gate G2.

### Functional Requirements — Link management (LNK)

- **FR-LNK-01** [Confirmed · A§2, G§7]: The system MUST let an API consumer create a short link for
  a valid absolute URL and MUST return the short code, the full short URL, the normalized target
  URL, the creation time, and the expiry time (if any).
- **FR-LNK-02** [Confirmed · C:V, G§7]: The system MUST accept only the web schemes `http` and
  `https` and MUST reject any other scheme with a validation error that names the reason.
- **FR-LNK-03** [Derived · C:V]: The system MUST reject target URLs that embed user credentials,
  lack a host, exceed the maximum length, name a local host (`localhost` and its subdomains), use a
  loopback, private, link-local, unspecified, or multicast IP address in any notation, or point at
  the shortener's own host.
- **FR-LNK-04** [Derived · C:V]: The system MUST store and return the normalized form of an
  accepted URL: lower-case scheme and host, internationalized host converted to ASCII form, default
  port removed, and path, query, and fragment preserved.
- **FR-LNK-05** [Confirmed · G§7]: Short codes MUST be unique across all links. On a collision the
  system MUST generate a new code, up to a bounded number of attempts; if uniqueness cannot be
  achieved within the bound, creation MUST fail with a retryable error and no partial link.
- **FR-LNK-06** [Derived · C:V]: Generated short codes MUST have a fixed length over letters and
  digits and MUST NOT be predictable from previously issued codes.
- **FR-LNK-07** [Derived · G§7]: Two creation requests for the same target URL without an
  idempotency key MUST produce two distinct links. [NEEDS CLARIFICATION: should the system instead
  de-duplicate identical target URLs and return the existing link?]
- **FR-LNK-08** [Confirmed · G§7]: When a creation request carries an idempotency key, repeating the
  identical request with the same key within the idempotency window MUST return the original result
  without creating another link, and reusing the key with a different payload MUST be rejected as
  a conflict.
- **FR-LNK-09** [Confirmed · G§7]: Concurrent creation requests MUST NOT produce two links with the
  same code, nor more than one link for the same idempotency key.
- **FR-LNK-10** [Confirmed · C:V]: Link creation MUST be rate-limited per client; a request over the
  limit MUST be rejected with an indication of when to retry.
- **FR-LNK-11** [Derived · A§2]: A consumer MUST be able to retrieve a link's metadata by code:
  target URL, creation time, expiry time, status (active or expired), and click limit if set.
- **FR-LNK-12** [Confirmed · G§7]: A consumer MAY set an expiry time at creation. The expiry MUST be
  in the future and no later than the maximum expiry horizon; otherwise creation MUST be rejected.
- **FR-LNK-13** [Derived · C:V]: Link creation MUST be available to [NEEDS CLARIFICATION: anonymous
  clients protected by rate limiting, or only authenticated clients?].

### Functional Requirements — Redirect resolution (RED)

- **FR-RED-01** [Confirmed · A§2, G§7]: Resolving the code of an active link MUST redirect the client
  to the stored target URL.
- **FR-RED-02** [Derived · FR-ANL-01]: Redirects MUST be marked as not cacheable so that every
  resolution reaches the service and can be counted.
- **FR-RED-03** [Confirmed · G§7]: Resolving an unknown or malformed code MUST return the not-found
  outcome without redirecting.
- **FR-RED-04** [Confirmed · G§7]: Resolving an expired link MUST return an expired outcome that is
  distinguishable from not-found and MUST NOT redirect.
- **FR-RED-05** [Derived · C:V]: A client that produces an excessive number of not-found outcomes
  within a time window MUST be throttled, to slow code enumeration.

### Functional Requirements — Analytics (ANL)

- **FR-ANL-01** [Confirmed · A§2, G§7]: Each successful redirect MUST be counted exactly once in the
  link's analytics (total click count and last-accessed time) and recorded as a click event with
  its timestamp and, when present, the referrer's host.
- **FR-ANL-02** [Confirmed · C:V]: Analytics MUST NOT store raw client IP addresses or other direct
  personal identifiers.
- **FR-ANL-03** [Derived · A§2]: A consumer MUST be able to retrieve a link's analytics: total
  clicks, last-accessed time, and click counts per day for a bounded recent window.
- **FR-ANL-04** [Confirmed · G§7]: A failure to record analytics MUST NOT prevent the redirect of a
  link that has no click-dependent rule, and each such failure MUST be counted in an operational
  metric.
- **FR-ANL-05** [Confirmed · G§7]: Under concurrent resolution of the same link, the recorded click
  count MUST equal the number of successful redirects.

### Functional Requirements — Operations (OPS)

- **FR-OPS-01** [Confirmed · G§7]: The system MUST expose liveness and readiness indicators;
  readiness MUST report not-ready while the link store is unavailable.
- **FR-OPS-02** [Confirmed · G§7]: While the link store is unavailable, creation and resolution MUST
  fail promptly with a retryable temporarily-unavailable outcome and MUST NOT leave partial writes.
- **FR-OPS-03** [Derived · C:V]: All error outcomes MUST use one machine-readable format containing
  an error code, a human-readable message, and the correlation identifier, and MUST NOT expose
  internal details such as stack traces or query text.
- **FR-OPS-04** [Confirmed · C:IX]: Every request MUST carry a correlation identifier: a well-formed
  identifier supplied by the client is used, otherwise one is generated; it is returned in the
  response and included in logs.

### Functional Requirements — Scenario capabilities (CAP)

- **FR-CAP-01** [Derived · A§4.4, A§4.7]: Capabilities introduced by the scenarios MUST be delivered
  in an unreleased state and become available to consumers only when an orchestration run releases
  them. While unreleased, a request that uses the capability's input MUST be rejected with a
  "capability not available" error and MUST NOT be silently ignored.
- **FR-CAP-02** [Derived · SCN-A]: When released, the custom-alias capability MUST satisfy GF-001
  AC-1 to AC-6.
- **FR-CAP-03** [Derived · SCN-B]: When released, the click-limit capability MUST satisfy BF-001
  AC-1 to AC-6; links without a click limit MUST keep their existing behavior.
- **FR-CAP-04** [Derived · SCN-C]: When released, the default-expiry capability MUST assign the
  human-decided default expiry to links created without an explicit expiry and MUST NOT change
  existing links.

### Functional Requirements — Orchestration (ORC)

- **FR-ORC-01** [Confirmed · G§7]: An authorized requester MUST be able to submit a requirement
  (title, narrative, optional type, optional acceptance criteria, optional constraints) and receive
  a run identifier. Invalid submissions (missing title or narrative, oversize fields) MUST be
  rejected.
- **FR-ORC-02** [Confirmed · A§4.4]: Each run MUST execute an explicit, persisted dependency graph of
  stages (the plan). The plan MUST be validated as acyclic with resolvable dependencies before
  execution; an invalid plan MUST NOT execute.
- **FR-ORC-03** [Confirmed · A§4.4, G§7]: The stage catalog MUST cover requirement ingestion,
  requirement analysis (normalization, ambiguity detection, quality checks), clarification,
  decomposition, impact analysis, threat assessment, design, architecture approval,
  implementation, testing, regression testing, security verification, documentation, validation,
  compliance evaluation, release approval, release, and final summary.
- **FR-ORC-04** [Confirmed · A§4.4]: A stage MUST start as soon as all of its dependencies are
  satisfied, and independent stages MUST be able to execute concurrently.
- **FR-ORC-05** [Confirmed · A§4.4]: A synchronization stage MUST start only after all of its
  predecessors have succeeded; if any predecessor fails, it MUST NOT start.
- **FR-ORC-06** [Confirmed · A§4.4, G§7]: Conditional stages MUST be included or skipped according to
  recorded conditions (clarification only for blocking ambiguity; impact and regression testing
  only for changes to existing behavior; architecture approval only for material changes), and
  every skip MUST record its reason.
- **FR-ORC-07** [Confirmed · A§4.4]: Every stage MUST have entry criteria, evaluated before it
  starts, and exit criteria, evaluated when it finishes; a failed exit criterion MUST prevent its
  dependents from starting.
- **FR-ORC-08** [Confirmed · A§4.4, C:II]: Run state (run status, stage states, attempts, artifacts,
  decisions, plan versions) MUST be persisted at every transition so that the run can be inspected
  and resumed after a process restart.
- **FR-ORC-09** [Confirmed · G§7]: An authorized user MUST be able to inspect a run: its status, the
  current plan with stage states, artifacts with versions, decisions, pending human actions,
  policy outcomes, and its timeline.
- **FR-ORC-10** [Confirmed · A§4.4]: Stage outputs MUST be stored as versioned artifacts recording
  the producing stage, agent, attempt, input artifact versions, and content fingerprint, so that
  cross-stage context is preserved and every artifact can be traced to its inputs.
- **FR-ORC-11** [Confirmed · C:II]: Run and stage transitions MUST follow a defined state model; a
  prohibited transition MUST be rejected and recorded.
- **FR-ORC-12** [Confirmed · A§4.7]: Each agent MUST be limited to the actions declared for its stage
  type; in particular, agents MUST NOT decide gates, change policies, or release capabilities
  outside the release stage. Runtime agents MUST [NEEDS CLARIFICATION: run deterministically and
  offline, or may they call an external AI (LLM) service?].
- **FR-ORC-13** [Confirmed · G§7]: Requirement analysis MUST record each quality check performed
  (completeness, consistency, testability, policy, architecture boundary) with its result, and for
  a requirement that passes all checks MUST record why clarification is not required.
- **FR-ORC-14** [Confirmed · A§4.3, G§7]: For a change to existing behavior, impact analysis MUST
  complete before design and MUST identify impacted components, interfaces, data flows, tests,
  documentation, regression risks, and rollout/rollback considerations, derived from the current
  codebase.
- **FR-ORC-15** [Derived · A§4.5, C:X]: The implementation stage MUST produce a change set (planned
  changes per component mapped to tasks and acceptance criteria) and MUST verify that the delivered
  capability is present in the running system; if it is absent, the stage MUST fail permanently
  rather than report success.
- **FR-ORC-16** [Confirmed · A§4.5]: The testing stage MUST execute acceptance checks derived from
  the requirement's acceptance criteria against the running service and record a result per
  criterion; data created by these checks MUST be labeled synthetic and removed afterwards.
- **FR-ORC-17** [Confirmed · A§4.8]: Every run that reaches a terminal state MUST produce a final
  engineering summary covering plan and rationale, artifacts, decisions and approvals, policy
  outcomes, validation results, risks, assumptions, limitations, metrics, and the terminal outcome.
- **FR-ORC-18** [Derived · A§4.7, C:II]: Each run MUST have an autonomy budget (maximum total stage
  attempts and maximum processing time excluding time waiting for humans); exceeding it MUST
  trigger safe-stop.

### Functional Requirements — Human governance (GOV)

- **FR-GOV-01** [Confirmed · A§4.4, G§7]: Human gates MUST exist for blocking ambiguity
  (clarification), material changes (architecture approval), policy exceptions, change control
  after approval, and release; destructive actions on user data MUST also require approval.
- **FR-GOV-02** [Derived · A§4.4]: While a gate waits for a decision, stages that depend on it MUST
  NOT start, and stages that do not depend on it MAY continue.
- **FR-GOV-03** [Confirmed · C:III]: Only an authenticated human principal holding the required role
  MUST be able to decide a gate; decisions attempted by agents, by unauthenticated callers, or by
  principals without the role MUST be refused and audited.
- **FR-GOV-04** [Derived · C:III]: The requester of a run MUST NOT approve that run's architecture or
  release gate.
- **FR-GOV-05** [Confirmed · A§4.4]: An approval MUST be bound to the content fingerprints of the
  artifacts reviewed; if a bound artifact changes, the approval MUST be invalidated and the gate
  re-opened.
- **FR-GOV-06** [Confirmed · G§7]: A rejection MUST end the run with outcome `REJECTED` after
  compensating any completed side effects.
- **FR-GOV-07** [Confirmed · C:III]: A gate that receives no decision by its deadline MUST NOT be
  approved; the run MUST safe-stop and record the escalation.
- **FR-GOV-08** [Confirmed · G§7]: A clarification gate MUST present each question with the detected
  ambiguity, its severity, the affected items, and answer options with their impact; answers MUST
  be recorded as decisions and MUST produce a new requirement version.
- **FR-GOV-09** [Confirmed · C:IX]: Every decision MUST record the actor, the actor's role, the
  decision, the rationale, the timestamp, and the artifacts it is bound to.

### Functional Requirements — Reliability and recovery (REL)

- **FR-REL-01** [Confirmed · A§4.4, C:VIII]: Stage failures MUST be classified as transient or
  permanent; only transient failures MUST be retried, up to a per-stage maximum with increasing
  backoff.
- **FR-REL-02** [Confirmed · G§7]: Every stage attempt MUST be bounded by a timeout; a timed-out
  attempt MUST be treated as a transient failure and its late result discarded.
- **FR-REL-03** [Confirmed · A§4.4]: A stage MAY declare a fallback; after a permanent failure or
  retry exhaustion the fallback MUST execute, and its use MUST be recorded and marked as degraded.
- **FR-REL-04** [Confirmed · A§4.4]: Stages with side effects MUST declare compensation. When a run
  fails, is rejected, or safe-stops after side effects, compensation MUST execute in reverse
  completion order and its outcome MUST be recorded.
- **FR-REL-05** [Confirmed · C:VIII]: Rollback MUST be claimed only for state fully under system
  control; side effects on consumer-visible data MUST be handled by compensation, and the evidence
  MUST state which mechanism was used.
- **FR-REL-06** [Confirmed · A§4.4]: A run MUST safe-stop when a mandatory policy fails without an
  approved exception, retries and fallback are exhausted, a gate deadline passes, compensation
  fails, the autonomy budget is exceeded, the maximum clarification rounds are exceeded, or an
  operator requests it. Safe-stop MUST prevent new stage starts, apply compensation, persist state,
  and record a terminal outcome.
- **FR-REL-07** [Confirmed · G§7]: An operator MUST be able to pause a run (no new stage starts;
  in-flight stages finish) and resume it.
- **FR-REL-08** [Confirmed · G§7]: After a process restart, runs that were in progress MUST resume
  from persisted state; interrupted stages MUST be re-executed, and completed stages and side
  effects MUST NOT be repeated.
- **FR-REL-09** [Confirmed · C:VIII]: Duplicate stage completions (for example a late result after a
  re-dispatch) MUST be detected and ignored.
- **FR-REL-10** [Derived · C:VIII]: If compensation fails after bounded retries, the run MUST
  safe-stop with a manual-intervention flag.
- **FR-REL-11** [Derived · C:V, C:X]: For demonstrations and tests, a run MAY carry a fault-injection
  plan only when fault injection is explicitly enabled; injected faults MUST be labeled as simulated
  in all evidence, and fault injection MUST be disabled by default.

### Functional Requirements — Dynamic replanning (RPL)

- **FR-RPL-01** [Confirmed · A§4.4]: When an upstream artifact changes, the system MUST determine the
  affected downstream stages, invalidate their outputs while retaining history, and re-execute
  them; unaffected completed stages MUST NOT be re-executed.
- **FR-RPL-02** [Confirmed · A§4.4]: Replanning MAY add or remove stages; every plan change MUST
  create a new plan version recording the difference, the trigger, and the reason.
- **FR-RPL-03** [Confirmed · C:VI]: A material change (affecting the public API, stored data
  structure, security controls, or release criteria) after architecture approval MUST require a
  change-control approval before affected stages re-execute.
- **FR-RPL-04** [Derived · A§4.4]: A requester MUST be able to submit a change request (amended
  requirement) to an active run.
- **FR-RPL-05** [Derived · C:VIII]: Runs in a terminal state MUST NOT be replanned.
- **FR-RPL-06** [Confirmed · G§7]: When a stage reports a blocking ambiguity during execution, only
  the stages that depend on it MUST be suspended pending clarification; independent stages MUST
  continue.

### Functional Requirements — Policy and change control (POL)

- **FR-POL-01** [Confirmed · A§4.4, C:VI]: A versioned policy set covering security, privacy,
  compliance (audit retention), licensing and approved dependencies, testing, documentation, and
  change control MUST be evaluated for each run, and each run MUST record the policy-set version.
- **FR-POL-02** [Confirmed · C:VI]: Each applicable policy evaluation MUST produce exactly one of
  `PASS`, `FAIL`, `EXCEPTION-REQUESTED`, or `NOT-APPLICABLE`, with evidence.
- **FR-POL-03** [Confirmed · C:VI]: A `FAIL` on a mandatory policy MUST block progression towards
  release.
- **FR-POL-04** [Confirmed · C:VI]: A requester MUST be able to request, and an authorized human
  approve or reject, a time-bound policy exception recording the policy, reason, scope,
  compensating control, approving authority, approval time, and expiry; an expired exception MUST
  be treated as unapproved.
- **FR-POL-05** [Derived · C:VI]: A failed advisory (non-mandatory) policy MUST be recorded and
  reported but MUST NOT block progression.
- **FR-POL-06** [Confirmed · C:VI]: Policy outcomes and exceptions MUST appear in the audit trail and
  the final engineering summary.

### Functional Requirements — Audit, lineage, and metrics (AUD)

- **FR-AUD-01** [Confirmed · A§4.4, C:IX]: Every state transition, decision, approval, rejection,
  retry, failure, fallback, compensation, replan, policy evaluation, and terminal outcome MUST be
  recorded as an audit event with the run identifier, actor type, actor, action, timestamp,
  target, result, and reason.
- **FR-AUD-02** [Confirmed · C:IX]: Audit events MUST be append-only and tamper-evident, and the
  system MUST provide an integrity verification that detects modification, insertion, or deletion
  of events.
- **FR-AUD-03** [Confirmed · G§7]: An authorized user MUST be able to retrieve a run's audit trail
  and the decision lineage of any artifact.
- **FR-AUD-04** [Confirmed · A§4.4]: The system MUST compute, from recorded evidence, the run success
  rate, failure rate, retry frequency, rollback/compensation frequency, mean time to recovery
  (MTTR = total recovery duration of recovered failure events ÷ number of recovered failure
  events), unrecovered failure count, and end-to-end latency statistics, and MUST report the
  measurement population, exclusions, and a demonstration-data label.
- **FR-AUD-05** [Confirmed · C:IX, C:V]: Logs MUST include the run identifier for orchestration
  activity and the correlation identifier for request activity, and MUST exclude secrets.
- **FR-AUD-06** [Derived · A§4.4]: The system MUST record the start and end time of every stage
  attempt so that parallel execution, synchronization, and latency can be verified.

### Functional Requirements — Release readiness (RDY)

- **FR-RDY-01** [Confirmed · G§7]: Before release approval, the system MUST compute a readiness
  outcome of `READY`, `READY WITH ACCEPTED LIMITATIONS`, or `NOT READY` from validation results,
  policy outcomes, exceptions, degraded stages, and open risks.
- **FR-RDY-02** [Confirmed · C:VI]: Release approval MUST be refused while readiness is `NOT READY`;
  readiness MUST be re-evaluated at decision time.
- **FR-RDY-03** [Derived · A§4.4]: The release stage MUST make the capability available, verify it
  after release, and on verification failure compensate by withdrawing the capability and then
  safe-stop.
- **FR-RDY-04** [Confirmed · C:III]: Only a principal holding the release-owner role MUST be able to
  decide a release gate.

### Key Entities *(include if feature involves data)*

Application plane:

- **Short Link**: code, target URL, creation time, optional expiry, optional click limit, click
  count, last-accessed time, whether the code is a custom alias, whether it is synthetic.
- **Click Event**: link, occurrence time, referrer host; no personal identifiers.
- **Idempotency Record**: key, request fingerprint, original result, creation and expiry time.
- **Capability Release State**: capability, released or not, parameters, who changed it, when, and
  the run that changed it.

Control plane:

- **Requirement**: submitted input and its versions (original, clarified, amended).
- **Workflow Run**: identifier, requirement, requester, status, current plan version, policy-set
  version, timestamps, terminal outcome and reason, readiness outcome.
- **Plan Version**: the dependency graph for a run at a point in time, its difference from the
  previous version, trigger, and reason.
- **Stage**: type, dependencies, conditions, entry and exit criteria, state, attempt count,
  generation (increments on invalidation), deadline for gates.
- **Stage Attempt**: start, end, outcome, failure classification, whether it was simulated.
- **Artifact**: type, version, content, fingerprint, producing stage and attempt, input artifacts,
  superseded flag.
- **Decision**: type, actor, role, rationale, time, bound artifact fingerprints, superseded flag.
- **Clarification Question**: ambiguity, severity, affected items, options with impact, answer.
- **Change Request**: amended requirement, requester, materiality, approval.
- **Policy Set / Policy Evaluation / Policy Exception**: as defined in FR-POL-01 to FR-POL-06.
- **Audit Event**: sequence, time, actor type, actor, action, target, result, reason, integrity
  link to the previous event.
- **Failure Event**: stage, classification, detection time, recovery start and completion times,
  recovery mechanism, recovered or not.
- **Principal**: identity and roles (requester, approver, release owner, auditor).

## Non-Functional Requirements

Each NFR names its verification method. Numeric targets refer to Proposed Validation Targets
(PVT), which are assumptions requiring approval at Gate G2, not confirmed client requirements.

| ID | Area | Requirement | Verification |
|----|------|-------------|--------------|
| NFR-SEC-01 | Security | 100% of the malicious and invalid URL patterns in the security test catalog (PVT-15) are rejected. | Security test suite |
| NFR-SEC-02 | Security | Every control-plane operation requires an authenticated principal with the operation's role; 100% of unauthorized attempts are refused. | Per-operation authorization tests |
| NFR-SEC-03 | Security | No secret or credential appears in logs, audit events, or artifacts. | Secret-scan policy + log capture test |
| NFR-SEC-04 | Security | Creation and not-found throttling apply per client at PVT-05 and PVT-06. | Rate-limit tests |
| NFR-SEC-05 | Security | Dependencies are restricted to approved licenses and scanned for known vulnerabilities before release readiness. | License policy + dependency report |
| NFR-REL-01 | Reliability | Redirect availability is not reduced by analytics failures for links without click rules. | Fault-injection test |
| NFR-REL-02 | Reliability | Retries are bounded per PVT-11; no stage exceeds its maximum attempts. | Orchestration tests |
| NFR-REL-03 | Reliability | Every run reaches exactly one terminal outcome (`COMPLETED`, `REJECTED`, `SAFE_STOPPED`). | Orchestration tests over all drills |
| NFR-SCA-01 | Scalability | The request-handling tier keeps no per-client session state; horizontal scaling needs only a shared store (documented). | Architecture review + ADR |
| NFR-SCA-02 | Scalability | The short-code space holds at least 3.5 × 10¹² codes, so collision retries stay negligible at 10 million links. | Calculation in design + collision test |
| NFR-MNT-01 | Maintainability | Module boundaries (application plane, control plane, shared platform) are enforced by automated architecture tests. | Architecture tests |
| NFR-MNT-02 | Maintainability | Adding a stage type or policy requires no change to the scheduling core. | Extension test |
| NFR-OBS-01 | Observability | A run's full history (plan versions, attempts, decisions, audit) can be reconstructed from persisted evidence alone. | Reconstruction test |
| NFR-OBS-02 | Observability | Operational metrics for requests, redirects, analytics failures, runs, retries, and compensations are exposed. | Metrics test |
| NFR-AUD-01 | Auditability | Audit retention is configured at no less than PVT-18. | Compliance policy |
| NFR-AUD-02 | Auditability | Integrity verification detects 100% of single-event modifications, insertions, and deletions in tests. | Tamper tests |
| NFR-PRF-01 | Performance | Redirect p95 ≤ PVT-19 and creation p95 ≤ PVT-20 at the demonstration load. | Labeled demonstration measurement |
| NFR-PRF-02 | Performance | Orchestration overhead for a scenario run, excluding human wait, ≤ PVT-21. | Scenario timing evidence |
| NFR-RCV-01 | Recoverability | Interrupted runs resume within PVT-22 of process start. | Restart test |
| NFR-RCV-02 | Recoverability | MTTR and unrecovered failures are reported per FR-AUD-04 over the drill population. | Reliability report |
| NFR-TST-01 | Testability | Line coverage of domain and orchestration code ≥ PVT-23, measured automatically. | Coverage report |
| NFR-TST-02 | Testability | The full automated suite runs with one command, needs no network services, and is order-independent. | Build run |
| NFR-CHG-01 | Change safety | Every public API and stored-data change carries a version and a compatibility classification; breaking changes require change approval. | Contract and change-control policy |
| NFR-CHG-02 | Change safety | Stored-data changes are additive and backward compatible so that withdrawing a capability loses no data. | Migration review + test |
| NFR-AUT-01 | Controlled autonomy | Every agent's permitted actions are declared and enforced; out-of-scope actions are refused. | Agent permission tests |
| NFR-AUT-02 | Controlled autonomy | The autonomy budget (PVT-24) bounds every run. | Budget exhaustion test |

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A reviewer goes from a clean checkout to a running system and a created short link in
  at most 15 minutes by following the quickstart (PVT-25).
- **SC-002**: All three scenarios (SCN-A, SCN-B, SCN-C) and all seven drills (RDR-01 to RDR-07)
  reach their expected terminal outcomes in automated end-to-end runs: 10 out of 10.
- **SC-003**: 100% of functional requirements map to at least one executed test in the traceability
  matrix; there are no orphan requirements, tasks, or tests.
- **SC-004**: 0 runs progress past a pending mandatory gate or a failed mandatory policy across the
  whole automated suite.
- **SC-005**: 0 lost clicks: with 200 concurrent resolutions, the recorded count equals the number
  of successful redirects.
- **SC-006**: 100% of audit tampering cases in the test catalog are detected by integrity
  verification.
- **SC-007**: 100% of runs interrupted in the restart tests resume and reach a terminal outcome
  without repeating completed stages.
- **SC-008**: The reliability report states MTTR over recovered failure events only, lists
  unrecovered failures separately, and labels all figures as demonstration data.
- **SC-009**: Every scenario's evidence set (E-A1..E-A9, E-B1..E-B9, SCN-C list) is present in the
  repository and each item names the command that regenerates it.

## Assumptions

Each assumption is proposed and requires approval at Gate G2 or G3. Owner: human candidate.

- **ASM-01**: The prototype runs as a single instance with a local persistent store; multi-instance
  deployment is designed for but not delivered.
- **ASM-02**: Human principals authenticate with pre-provisioned, clearly labeled demonstration
  credentials; enterprise identity integration is out of scope.
- **ASM-03**: Short-link statistics are readable by anyone who knows the code; there is no link
  ownership.
- **ASM-04**: Expired links are retained (not purged) so the expired outcome can be returned.
- **ASM-05**: Time is the service's UTC clock; daily analytics buckets use UTC days.
- **ASM-06**: Scenario capabilities are written through this repository's governed development
  process; the runtime orchestration verifies, tests, governs, and releases them, and does not
  generate source code at runtime (see AMB-01).
- **ASM-07**: Release means availability to all consumers; percentage-based rollout is excluded.
- **ASM-08**: Safe-stop is terminal for a run; continuing the work requires a new run that
  references the stopped one.
- **ASM-09**: Redirects are temporary (not permanent) so they are not cached and every click is
  counted (see AMB-05).
- **ASM-10**: Analytics are recorded synchronously with the redirect, giving exact counts at the
  cost of one extra write per redirect (see AMB-06).

## Constraints

- **CON-01**: 2–3 day timebox; must-have scope precedes optional enhancements (A§2, C: Delivery
  Constraints).
- **CON-02**: Runs locally end-to-end without paid services or real secrets (C: Delivery
  Constraints).
- **CON-03**: Technology choices are made in the plan and recorded as ADRs, not in this
  specification.
- **CON-04**: Production-grade discipline without production-scale infrastructure.
- **CON-05**: Evidence must be truthful; simulated inputs and demonstration measurements are
  labeled (C:X).

## Ambiguity Register

Owner of every item: human candidate. Decision point: Gate G3 unless stated otherwise.

| ID | Question | Impact | Current assumption |
|----|----------|--------|--------------------|
| AMB-01 | May runtime agents call an external AI (LLM) service, and may they generate source code at runtime? | Scope, reproducibility, secrets, test determinism | Deterministic offline agents; capabilities delivered through the governed repository process (ASM-06) |
| AMB-02 | Is link creation anonymous (rate-limited) or authenticated? | Security posture, API usability | Anonymous with rate limiting; control plane authenticated |
| AMB-03 | Should identical target URLs be de-duplicated? | Data model, API semantics | No; idempotency keys cover retries (FR-LNK-07/08) |
| AMB-04 | How long do gates wait before safe-stop? | Governance, demonstrability | Configurable per gate (PVT-13) |
| AMB-05 | Permanent or temporary redirects? | Analytics accuracy vs. client caching | Temporary, not cacheable (ASM-09) |
| AMB-06 | Exact synchronous analytics or eventually consistent? | Redirect latency vs. accuracy | Synchronous (ASM-10) |
| AMB-07 | Audit retention period? | Compliance policy, storage | PVT-18 |
| AMB-08 | Is safe-stop resumable? | State model | Terminal (ASM-08) |
| AMB-09 | Which capabilities serve as scenario payloads? | Scenario credibility | Custom alias (A), click limit (B), default expiry (C) |

## Exclusions

- **EXC-01**: User accounts, link ownership, dashboards, and user interfaces.
- **EXC-02**: Custom domains, QR codes, link previews, and bulk import.
- **EXC-03**: Multi-region or highly available deployment, distributed caches, and message queues.
- **EXC-04**: Percentage-based or cohort rollout of capabilities.
- **EXC-05**: Enterprise identity provider integration.
- **EXC-06**: Third-party URL reputation or malware scanning (recorded as a production enhancement).
- **EXC-07**: Runtime generation of source code by agents (pending AMB-01).

## Proposed Validation Targets

All values are proposals (assumptions requiring approval), not confirmed client requirements.

| ID | Target | Proposed value |
|----|--------|----------------|
| PVT-01 | Maximum target URL length | 2,048 characters |
| PVT-02 | Generated code format | 7 characters from 62 letters and digits |
| PVT-03 | Collision regeneration attempts | 5 |
| PVT-04 | Idempotency window | 24 hours |
| PVT-05 | Creation rate limit | 30 requests per minute per client |
| PVT-06 | Not-found throttle | 60 not-found outcomes per minute per client |
| PVT-07 | Maximum expiry horizon | 5 years |
| PVT-08 | Alias length | 3–32 characters |
| PVT-09 | Click-limit range | 1–1,000,000 |
| PVT-10 | Daily analytics window | 30 days |
| PVT-11 | Default retry policy | 3 attempts; backoff 200 ms doubling, capped at 5 s |
| PVT-12 | Default stage attempt timeout | 30 s |
| PVT-13 | Default gate deadline | 24 hours (shortened in tests and drills) |
| PVT-14 | Maximum clarification rounds | 3 |
| PVT-15 | Security URL test catalog | at least 25 malicious or invalid patterns |
| PVT-16 | Compensation retry attempts | 3 |
| PVT-17 | Autonomy budget | 60 stage attempts and 10 minutes processing time per run |
| PVT-18 | Audit retention | 365 days |
| PVT-19 | Redirect p95 at demonstration load | ≤ 50 ms (20 concurrent clients, local machine) |
| PVT-20 | Creation p95 at demonstration load | ≤ 150 ms |
| PVT-21 | Scenario run processing time excluding human wait | ≤ 10 s |
| PVT-22 | Resumption delay after process start | ≤ 30 s |
| PVT-23 | Line coverage of domain and orchestration code | ≥ 80% |
| PVT-24 | Autonomy budget reference | as PVT-17 |
| PVT-25 | Quickstart time | ≤ 15 minutes |

## Traceability Notes

- Stable identifiers: user stories `US1`–`US7`; scenarios `SCN-A`, `SCN-B`, `SCN-C`; drills
  `RDR-01`–`RDR-07`; requirements `FR-<area>-NN` and `NFR-<area>-NN`; success criteria `SC-NNN`;
  assumptions `ASM-NN`; constraints `CON-NN`; ambiguities `AMB-NN`; exclusions `EXC-NN`; targets
  `PVT-NN`; scenario evidence `E-A*`, `E-B*`.
- Every acceptance scenario cites the requirements it verifies. The plan, ADRs, tasks, tests, and
  the traceability matrix (`docs/traceability/`) cite these identifiers, which makes the chain
  requirement → scenario → design → ADR → task → code → test → evidence navigable both ways.
