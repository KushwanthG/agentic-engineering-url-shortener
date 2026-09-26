# Timebox and Scope Control

**Task**: T001 · **Requirements**: CON-01; constitution Delivery Constraints · **Owner of decisions**:
the human candidate (checkpoint records below are written by the assistant during the delegated
session and are subject to review at G5/G6).

The assessment is timeboxed to 2–3 days. This document fixes what must be delivered, what is
deferred, the order of work, and the rules for cutting scope without dropping mandatory validation.

## 1. Must-have scope

1. URL shortener core (US1): validated creation, unpredictable codes, redirect with exact
   analytics, expiry, idempotency, rate limits, health, error model, authentication.
2. Orchestration core (US2): persisted dependency graph, coordinator with parallel dispatch and
   joins, conditions, artifacts with provenance, deterministic agents for every stage, policy
   evaluation, capability release, SCN-A end-to-end.
3. Governance (US3): roles, separation of duties, fingerprint-bound approvals, deadlines,
   concurrency safety.
4. Reliability (US4): retry, timeout, fallback, compensation and rollback, safe-stop, pause and
   resume, restart recovery, autonomy budget, fault injection, exceptions, readiness, drills.
5. Brownfield (US5) and ambiguous (US6) scenarios with their capabilities, the impact gate, and
   replanning.
6. Evidence (US7): audit verification, lineage, MTTR report, metrics, traceability check,
   evidence index.
7. Delivery: contract and schema tests, security scans, documentation, quickstart validation,
   convergence report, final summary, reviewer guide.

## 2. Deferred backlog (not to displace must-have work)

| ID | Item | Why deferred |
|----|------|--------------|
| BL-01 | LLM-backed agents with deterministic fallback | Q1 (provisional); secrets and non-determinism |
| BL-02 | PostgreSQL profile with Testcontainers | Docker not guaranteed |
| BL-03 | Container image, CI pipeline, CI-hosted scans | timebox |
| BL-04 | OpenTelemetry trace export | needs a collector |
| BL-05 | Multi-instance coordination and distributed rate limiting | ASM-01 |
| BL-06 | External anchoring of audit chain heads | external service |
| BL-07 | Percentage-based rollout | EXC-04 |

## 3. Day-level milestones mapped to task phases

| Day | Milestones (plan §14) | Task phases |
|-----|-----------------------|-------------|
| Day 1 | M0 baseline, M1 walking skeleton, M2 core URL behavior | Phase 1, Phase 2, Phase 3 (US1) → CP1 |
| Day 2 | M3 state model and engine, M4 governance, M5 reliability, policy core of M6 | Phase 4 (US2, SCN-A), Phase 5 (US3), Phase 6 (US4) → CP2 |
| Day 3 | brownfield and replanning parts of M6/M7, M8 evidence, M9 release readiness | Phase 7 (US5), Phase 8 (US6) → CP3, Phase 9 (US7), Phase 10 |

## 4. Critical path

T002 → T005 → T007 → T012 → T036 → T041 → T042 → T048 → T129 → T130 → T049 → T053 → T057 → T064 →
T068 → T070 → T071 → T080 → T086 → T089 → T088 → T094 → T098 → T097 → T132 → T108 →
T127/T128 → T121 → T123 → T125 (gate).

## 5. Checkpoint decisions

| Checkpoint | When | Decision rule |
|------------|------|---------------|
| CP1 | after US1 (T033) | If US1 consumed more than 40% of the timebox: keep all US1 behavior but simplify not-found throttling to the same limiter as creation; never cut validation, tests, or security |
| CP2 | after US4 drills (T082) | If behind: implement late-ambiguity path suspension (T096) only at engine level (no scenario export) and keep the documentation agent's template path |
| CP3 | after SCN-C (T100) | If behind: shorten documentation prose (not evidence), keep every validation and evidence task |

Mandatory validation, evidence, and security tasks are never cut at any checkpoint.

## 6. Stop conditions

- A failing mandatory test that cannot be fixed within its task group → stop, record, escalate.
- Any needed change to an approved contract, ADR, state model, or security control → stop and
  route to the correct upstream SpecKit stage.
- A Spring Boot 4.1 incompatibility with no in-version workaround → stop and propose an ADR-002
  revision.
- Evidence that cannot be produced truthfully → stop; never fabricate.

## 7. Checkpoint records

_(appended at CP1, CP2, CP3)_

### Scope decision SD-1 (2026-09-26, before CP1)

**Decision owner**: the human candidate, who instructed on 2026-09-26: "implement the tasks which
needed before submission of this assignment". **Recorded by**: the assistant. **Status**: candidate
instruction; subject to confirmation at G6.

**Basis**: the assignment asks for the §4 capabilities and §5 deliverables within 2–3 days while
"demonstrating engineering judgment"; it does not require every task of this plan. Section 5 of this
document still applies: mandatory validation, evidence, and security tasks are not cut.

**Deferred unless time remains after T124** (each recorded as a limitation in the final summary):

| Task | Item | Why deferrable | Residual risk |
|------|------|----------------|---------------|
| T074 | Autonomy budget | beyond A§4.4; retries and safe-stop already bound agent work | a pathological loop is stopped by retry bounds only |
| T096 | Late-ambiguity mid-run path | CP2 rule; SCN-C covers ambiguity at intake | mid-run clarification is untested |
| T107 | Run reconstruction test | audit verification (T101) and lineage (T102) cover reconstruction | weaker restart-forensics evidence |
| T109 | Evidence export scripts | evidence stays under `target/evidence/` and is referenced by path | manual copy step for reviewers |
| T112 | Contract compatibility review doc | CHANGELOG plus contract tests cover 1.0→1.2 changes | no consumer-impact narrative |
| T114 | Extensibility test | ADR-019 and architecture overview describe the extension points | extension claims are argued, not tested |
| T115 | JaCoCo coverage gate | JaCoCo report still generated | coverage can regress unnoticed |
| T119 | Traceability docs refresh | T108 enforces the matrix in the build | committed matrix copy may be stale |

**Kept although optional-looking**: T062 (approval race), T110/T111 (contract and schema
validation), T113 (performance), T127/T128 (secret and dependency scans), T132 (governance
invariants) — they are validation, security, or critical-path evidence.

**Result**: 121 of 132 tasks in scope for the assistant; T004, T125, T126 are human gates.

### CP1 (after US1, T033) — 2026-09-26

- **US1 complete?** Yes: T017–T032 done; 158 tests green in three consecutive full runs; packaged
  jar smoke-tested (create, redirect, stats, SSRF rejection, 404 problem JSON, readiness).
- **Elapsed time vs. plan**: not measured in hours (no time tracking was kept, so no figure is
  claimed). Phase 3 also absorbed the package restructure requested by the candidate and a
  Phase 2 audit concurrency defect found during the runs.
- **Decision (CP1 rule)**: keep all US1 behavior; no simplification of not-found throttling was
  needed. Scope decision SD-1 stands. Next: Phase 4 (orchestration core, SCN-A).
