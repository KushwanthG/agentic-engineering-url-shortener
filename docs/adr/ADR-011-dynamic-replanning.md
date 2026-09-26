# ADR-011: Dynamic Replanning by Content-Addressed Incremental Re-execution

## Status

Proposed (2026-09-26) — pending acceptance at Gate G4.

## Context

The orchestration must re-plan dynamically when upstream outputs change while maintaining
governance (A§4.4). Required: re-execute only affected stages (FR-RPL-01), version the plan with
recorded differences (FR-RPL-02), require change approval for material changes after approval
(FR-RPL-03), never replan terminal runs (FR-RPL-05), and suspend only affected paths on late
ambiguity (FR-RPL-06). Approvals must stay valid exactly when the reviewed content is unchanged
(FR-GOV-05).

## Decision Drivers

- Selectivity: no needless re-execution or re-approval
- Governance: material changes gated; approvals bound to content
- Evidence: every replan explainable (trigger, diff, invalidated stages)

## Options Considered

| Option | Advantages | Disadvantages / risks |
|--------|------------|-----------------------|
| Restart the run | trivial | discards valid work and approvals; not "replanning" |
| Invalidate the entire downstream closure and re-run all of it | simple | re-executes and re-approves stages whose inputs did not change |
| **Invalidate the downstream closure, then re-execute a stage only if its input fingerprint changed** | exact selectivity; approvals reused only if bound content is unchanged | requires canonical fingerprints of inputs |

## Decision

1. **Triggers**: clarification answers, applied change requests, a late ambiguity raised by a
   stage, and analysis refinement (classification changes).
2. **New requirement version** (for clarifications and change requests) with a fingerprint and the
   decision that produced it.
3. **Invalidation**: every stage in the downstream closure of the changed stage moves to
   `PENDING` with `generation + 1`; outputs of the previous generation are marked `superseded`,
   and in-flight attempts are discarded when they finish.
4. **Re-evaluation**: when an invalidated stage becomes ready, `InputFingerprinter` computes
   SHA-256 over the canonical JSON of (stage type, agent id and version, the versions of every
   knowledge resource the agent reads — capability catalog, ambiguity lexicon, policy set —,
   input artifact fingerprints, relevant requirement fields, relevant decisions). The agent and
   knowledge versions were added after the pre-implementation review (RC-1): without them a
   changed catalog or agent could let a stale output be reused. If it equals the stage's last successful
   `input_fingerprint`, the stage is marked `SUCCEEDED` with `reused = true` (audited as
   `STAGE_REUSED`) without running; otherwise it runs.
5. **Structural change**: `PlanFactory` recomputes the stage set from the new classification
   (brownfield stages added or removed) and inserts `CHANGE_APPROVAL` for material change requests
   after an approval. The new graph is validated (ADR-007), and a `plan_version` with a diff and
   trigger is recorded, together with a `REPLAN` decision (actor: system or the human whose
   decision triggered it).
6. **Gates**: approvals are reused only if their bound fingerprints still match; otherwise they are
   invalidated with a reason and the gate re-opens.
7. **Late ambiguity**: a stage returning `NeedsClarification` moves to
   `AWAITING_DECISION(CLARIFICATION)`. Only its dependents wait; independent stages continue.

## Rationale

This is the incremental-build principle applied to SDLC work: correctness through
content-addressing, with governance preserved because approvals are content-bound.

## Consequences

- **Positive**: minimal rework; the evidence shows exactly which stages re-ran and why.
- **Negative**: fingerprints must be canonical (field ordering, whitespace). Mitigation: a
  canonical JSON writer with sorted keys, covered by unit tests.
- **Testing**: change-after-completion, reuse, approval invalidation, change-control gate, late
  ambiguity path suspension, terminal-run refusal.

## Risks and Mitigations

| Risk | Mitigation |
|------|------------|
| Hidden inputs (an agent reads data not included in the fingerprint) | agent context exposes only declared inputs; fingerprint covers exactly those |
| Replan storms | clarification rounds bounded (PVT-14); autonomy budget |

## Reversibility

Medium.

## Traceability

- Requirements: FR-RPL-01..06, FR-GOV-05, FR-ORC-06; A§4.4
- Spec: SCN-C; User Story 6
- Plan: §3 scheduling algorithm; §9 change-request workflow; research R-13
- Tasks: replanning task group

## Validation

`ReplanningServiceTest`, `InputFingerprinterTest`, `ChangeRequestFlowTest`,
`LateAmbiguityTest`, SCN-C end-to-end evidence (plan v1 → v2 diff).
