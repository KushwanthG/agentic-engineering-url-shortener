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
