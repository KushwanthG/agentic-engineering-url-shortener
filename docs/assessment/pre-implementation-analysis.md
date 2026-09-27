# Pre-Implementation Analysis (`/speckit-analyze`)

**Feature**: `001-agentic-url-shortener` · **Date**: 2026-09-26 · **Mode**: strict, read-only over the
analyzed artifacts. This file records the reports; remediation happened in separate commits, as
the SDD guide instructs ("correct issues at their proper upstream source and re-run").

Coverage figures were computed mechanically: a script extracts every `FR-*`, `NFR-*`, and `SC-*`
identifier defined in `spec.md` and every reference in `tasks.md`, expanding `..` ranges, `/`
lists, and `*` wildcards. They are not estimates.

---

## Run 1

### Executive finding

The artifact set is broadly consistent and the orchestration model is not a linear chain: an
explicit persisted graph with fan-out, joins, conditions, gates, replanning, and recovery is
specified end-to-end and mapped to tasks. There is **one CRITICAL constitutional gap** (release
readiness lacks repository secret and dependency-vulnerability checks) and **one HIGH governance
dependency** (all human gates unratified). **Not ready for implementation until C1 is corrected
upstream.**

### Findings

| ID | Category | Severity | Location(s) | Summary | Recommendation |
|----|----------|----------|-------------|---------|----------------|
| C1 | Constitution | CRITICAL | constitution V ("Release readiness MUST include dependency-risk and secret checks"); tasks.md Phase 10 | No task performs a repository secret scan or a dependency vulnerability scan before release readiness; `SEC-002` scans run artifacts only; `LIC-001` checks licenses only | Add tasks before T122: repository secret scan (executable test over tracked files, labeled demo tokens allow-listed) and dependency vulnerability scan of the SBOM (OSV-Scanner or equivalent), with results in `docs/assessment/` |
| G1 | Governance | HIGH | gate register G1–G5; ADR statuses; checklists (0/167 reviewed) | Every mandatory gate is unratified; Q1–Q5 are provisional; all ADRs are `Proposed`; custom checklists are unreviewed. Implementation may proceed only under the Delegated Provisional Progression clause, itself part of the unratified constitution | Keep all of these visible as PENDING; record in G5 that implementation starts provisionally; never self-check checklists or gates; a rejection at any gate reopens the affected tasks |
| I1 | Inconsistency | MEDIUM | plan.md §14 vs. tasks.md phases | Plan milestones M3–M8 are ordered by technical layer (engine → governance → reliability → policy/replanning → agents → evidence); tasks are ordered by user story as SpecKit requires (US2 bundles engine, agents, and policy core) | Update plan §14 with the story-based execution order and a milestone↔phase mapping |
| I2 | Inconsistency | MEDIUM | tasks.md T006, T011, T012, T035, T042, T049, T052, T068, T072 | Stale cross-references from renumbering, e.g. T035 points to T066 for `orchestration.md` (written in T058); T052 → T071 for `policy-set.md` (T079); T068/T072 → T083 for reliability/runbook docs (T081); T011 → T101 for SECURITY.md (T116); T012 → T102 for the overview (T117); T049 → T081 for the finalizer (T071); T006 → T101 (T116) | Correct every cross-reference |
| I3 | Inconsistency | MEDIUM | spec FR-RDY-03, US4 scenario 4 vs. FR-REL-05, ADR-010 | FR-RDY-03 says "compensate by withdrawing the capability", while FR-REL-05 and ADR-010 classify flag restoration as rollback and consumer exposure as compensation | Reword FR-RDY-03: roll back the release state, record the exposure window as compensation, then safe-stop |
| I4 | Inconsistency | MEDIUM | spec FR-ORC-03 vs. plan §3, data-model, openapi `StageType` | The spec's stage catalog lists 18 stages and omits change approval; plan, data model, and contract define 19 including `CHANGE_APPROVAL` | Add change approval to FR-ORC-03 |
| I5 | Ambiguity | MEDIUM | openapi `decideGate`; plan §5 | It is not stated which endpoint decides `CHANGE_APPROVAL`, `CLARIFICATION`, and policy exceptions; `decideGate` accepts any `StageType` | State that `decideGate` decides only `ARCHITECTURE_APPROVAL` and `RELEASE_APPROVAL` (others → `409 ILLEGAL_STATE`); the dedicated endpoints decide the rest |
| U1 | Underspecification | MEDIUM | tasks.md T048, T052 | Mega-tasks: T048 builds four agents; T052 builds the policy engine plus 14 rules plus the compliance agent | Split into independently reviewable tasks |
| T1 | TDD | MEDIUM | tasks.md T089, T098 | SCN-B and SCN-C end-to-end tests are marked `Verification` and ordered after their capability code, although they can be red first (the orchestrator itself refuses an undelivered capability at `IMPLEMENTATION`, FR-ORC-15) | Write each scenario test before its capability implementation and record the red run |
| E1 | Evidence | MEDIUM | constitution (task-group boundary); tasks.md | No designated location for task-group checkpoint reviews and pre-commit reviews (guide §16–17) | Add `docs/assessment/task-group-checkpoints.md` to the task-group definition of done |
| E2 | Coverage | MEDIUM | spec SC-004; tasks.md | SC-004 (0 runs past a pending gate or failed mandatory policy) has no task | Add an executable governance-invariant check over every end-to-end run's audit trail |
| V1 | Coverage | LOW | spec NFR-REL-01; tasks T024/T025 | Behavior covered (fail-open analytics) but the NFR id is not referenced | Add NFR-REL-01 to T024/T025 |
| V2 | Coverage | LOW | spec NFR-SCA-01 | Stateless request tier has no task reference | Add to T011 (stateless security) and T117 (scaling path) |
| L1 | Terminology | LOW | constitution/spec vs. contract | Outcome formatting differs: `EXCEPTION-REQUESTED` / `NOT-APPLICABLE` / "READY WITH ACCEPTED LIMITATIONS" vs. `EXCEPTION_REQUESTED` / `NOT_APPLICABLE` / `READY_WITH_ACCEPTED_LIMITATIONS` | Record the mapping in the spec glossary |
| L2 | Format | LOW | tasks.md T003 | Marked `[P]` while depending on incomplete T002 | Remove `[P]` |
| L3 | Underspecification | LOW | spec FR-REL-11 vs. openapi `SimulationOptions.gateDeadlineSeconds` | Per-run gate deadline override exists only in plan and contract | Mention it in FR-REL-11 |
| L4 | Inconsistency | LOW | plan §13 vs. T108 | Plan describes only orphan-requirement detection; T108 also detects orphan tests and tasks | Update plan §13 |
| L5 | Inconsistency | LOW | plan Technical Context | States 89 functional requirements; spec defines 91 | Correct the count |
| L6 | Underspecification | LOW | spec SCN-A failure paths; tasks T080 | Which input RDR-01 uses is unspecified, yet SCN-A cites it as its failure path | State that RDR-01 runs the SCN-A input |
| L7 | Security | LOW | ADR-015 risks | No throttling of failed authentication attempts | Record as a residual risk in the risk register |
| L8 | Underspecification | LOW | spec ASM/FR-ANL | Click-event retention period unspecified | Add an assumption (retained for the prototype; production retention policy deferred) |

### Coverage summary (from the mechanical pass)

| Metric | Value |
|--------|-------|
| Requirement keys defined in spec (FR 91, NFR 26, SC 9) | 126 |
| Keys with ≥ 1 task | 123 |
| Coverage | 97.6% |
| Uncovered keys | NFR-REL-01, NFR-SCA-01, SC-004 |
| Task references to undefined ids | 0 |
| Tasks | 126 |
| Tasks without a `Req` field (unmapped tasks) | 0 |
| Ambiguity findings | 3 (I5, L3, L6) |
| Duplication findings | 0 |
| Critical issues | 1 (C1) |

Scenario mapping: SCN-A → design §11, T037–T058, T096 (escalation), evidence E-A1..E-A9; SCN-B →
§11, T083–T090 with the brownfield gate T086 before T087/T088; SCN-C → §11, T091–T099; drills
RDR-01..07 → T061, T067–T080, T073. All three scenarios differ in classification, stage set,
gates, and capability.

### Failure-trap scan

| Trap | Result |
|------|--------|
| Linear chaining; missing parallelism or synchronization | Not found: fan-out {DECOMPOSITION ‖ THREAT ‖ IMPACT}, {TESTING ‖ REGRESSION ‖ SECURITY ‖ DOCUMENTATION}; joins DESIGN, VALIDATION (plan §3; T040, T057) |
| Missing persistent state, context propagation, decision lineage | Not found (FR-ORC-08/10, FR-AUD-03; T036, T041, T102) |
| Unenforced approval; approval from silence | Not found (FR-GOV-03/07; T042, T061, T064, T065) |
| Unbounded retry; undefined timeout | Not found (PVT-11/12; per-stage table; T067/T068) |
| Rollback without feasibility; missing compensation | Not found (ADR-010; T070) — wording issue I3 only |
| Missing safe-stop, resume, replanning | Not found (T071, T073, T093–T096) |
| Missing audit evidence; claims without validation | Not found (T012, exports, every task has `Val`) |
| Weak brownfield analysis | Not found (source-scan impact analysis T083–T086, executed before the code) |
| Silent ambiguity resolution | Not silent, but provisional: see G1 |
| Implementation not preceded by tests | Minor: see T1 |
| Tasks inconsistent with ADRs | None found |
| Documentation disconnected from behavior | Fixed earlier (RDR-02); see I2 for stale references |

### Constitutional conflicts

- C1 (Principle V) — CRITICAL.
- No other principle conflicts. The gate situation (G1) is permitted by the Delegated
  Provisional Progression clause and is reported as HIGH.

### Corrective sequence

1. `tasks.md`: add the secret-scan and dependency-scan tasks (C1); split T048/T052 (U1); reorder
   SCN-B/SCN-C tests red-first (T1); fix cross-references (I2); add SC-004 invariant task (E2);
   add checkpoint-evidence location (E1); coverage and format fixes (V1, V2, L2, L6).
2. `spec.md`: FR-RDY-03 wording (I3), FR-ORC-03 catalog (I4), FR-REL-11 (L3), glossary mapping
   (L1), click-event retention assumption (L8).
3. `plan.md`: §14 order and mapping (I1), §13 orphan detection (L4), FR count (L5), constitution
   §8 release checks (C1).
4. `contracts/openapi.yaml`: `decideGate` scope (I5).
5. Gate register G5: record provisional start and checklist status (G1).
6. Re-run `/speckit-analyze`.

---

## Run 2 (after remediation)

Remediation was applied at the upstream source of each finding, as the guide instructs:

| Finding | Correction | Location |
|---------|------------|----------|
| C1 | Added repository secret scan (T127) and dependency vulnerability scan (T128) before the readiness proposal; plan §8 and §10 describe both | tasks.md, plan.md |
| G1 | Remains by design: only the candidate can ratify G1-G5, accept ADRs, and review checklists. Recorded in the G5 entry of the gate register | gate register |
| I1 | Plan §14 now maps milestones to the story-ordered task phases | plan.md |
| I2 | Nine stale cross-references corrected | tasks.md |
| I3 | FR-RDY-03 and US4 scenario 4 now say rollback of release state plus compensation for exposure | spec.md |
| I4 | FR-ORC-03 lists change approval | spec.md |
| I5 | `decideGate` decides only architecture and release approvals; other keys → `409 ILLEGAL_STATE` | openapi.yaml |
| U1 | T048 split into T048/T129/T130; T052 split into T052/T131 | tasks.md |
| T1 | SCN-B (T089) and SCN-C (T098) end-to-end tests now run red before their capability code | tasks.md |
| E1 | Default definition of done requires checkpoint and pre-commit review records in `docs/assessment/task-group-checkpoints.md` | tasks.md |
| E2 | T132 adds an executable governance-invariant check applied to every end-to-end run | tasks.md, plan.md §10 |
| V1, V2 | NFR-REL-01 referenced by T024/T025; NFR-SCA-01 by T011/T117 | tasks.md |
| L1 | Outcome spelling mapping added to the spec glossary | spec.md |
| L2 | `[P]` removed from T003 | tasks.md |
| L3 | FR-REL-11 mentions the per-run gate deadline override | spec.md |
| L4 | Plan §13 describes orphan requirement, test, and task detection | plan.md |
| L5 | Plan states 91 FRs, 26 NFRs, 9 success criteria | plan.md |
| L6 | SCN-A and T080 state that RDR-01 runs the SCN-A input | spec.md, tasks.md |
| L7 | Routed to the risk register task T118 | tasks.md |
| L8 | ASM-12 records click-event retention | spec.md |

### Mechanical re-check

| Metric | Run 1 | Run 2 |
|--------|-------|-------|
| Requirement keys | 126 | 126 |
| Keys with ≥ 1 task | 123 | 126 |
| Coverage | 97.6% | **100%** |
| Tasks | 126 | 132 |
| Tasks without `Req` | 0 | 0 |
| Duplicate task ids | 0 | 0 |
| `Deps` referencing unknown tasks | not checked | 0 |
| Stale cross-references | 9 | 0 |

### Result

- CRITICAL: **0** (C1 resolved).
- HIGH: **1** (G1, the human-gate dependency). It cannot be resolved by the assistant and is
  permitted by the Delegated Provisional Progression clause. It keeps release readiness
  `NOT READY` until the candidate acts.
- MEDIUM/LOW: 0 open.

Implementation may begin **provisionally** after the independent pre-implementation review
(guide §14) records no unresolved critical findings. G5 stays PENDING RATIFICATION.
