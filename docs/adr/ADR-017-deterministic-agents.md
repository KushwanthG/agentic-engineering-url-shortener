# ADR-017: Deterministic, Knowledge-Driven Stage Agents Behind a Permissioned SPI

## Status

Proposed (2026-09-26) — pending acceptance at Gate G4. Builds on Clarifications Q1 (provisional,
pending G3).

## Context

The system is "agentic": agents execute multi-step work under autonomy boundaries while humans own
oversight (A§4.7). Q1 decided (provisionally) that runtime agents run deterministically without
external AI services and do not generate source code at runtime. Capabilities are written through
the repository's SpecKit process by the AI coding assistant, and the runtime implementation stage
verifies and releases them (ASM-06).

## Decision Drivers

- Reproducibility and offline operation (Delivery Constraints)
- Honest behavior: never claim work that was not done (constitution X)
- Explainability of agent outputs for reviewers
- Extensibility towards AI-backed agents

## Options Considered

| Option | Advantages | Disadvantages / risks |
|--------|------------|-----------------------|
| **Deterministic agents using versioned knowledge (capability catalog, ambiguity lexicon, policy set, live source scan) behind a `StageAgent` SPI with declared permissions** | reproducible; testable; explainable; honest | reasoning limited to encoded knowledge |
| LLM-backed agents with deterministic fallback | richer language understanding | API keys, network, non-determinism, cost (backlog BL-01) |
| LLM code-generating agents | maximal autonomy | unreviewed runtime code changes contradict constitution III |

## Decision

- One agent per stage type, registered in `AgentRegistry`; fallbacks registered separately
  (ADR-009).
- Knowledge resources: `capability-catalog.yaml` (capabilities, keywords, components, API and
  schema deltas, release flag, probes, threats, docs anchors); `ambiguity-lexicon.yaml` (vague
  terms, undefined-concept patterns, conflict pairs, question templates); `policy-set.yaml`
  (ADR-019). `CodebaseScanner` reads `src/main/java`, `src/test/java`, and `docs/` to build an
  import graph for impact analysis.
- Permissions per agent (`READ_LINKS`, `WRITE_SYNTHETIC_LINKS`, `READ_CAPABILITIES`,
  `PREVIEW_CAPABILITY`, `CHANGE_CAPABILITY_RELEASE`) enforced by the port proxy; undeclared calls
  fail permanently and are audited.
- Acceptance checks are executable probes (Java classes) that map to acceptance criteria by
  declared patterns. An acceptance criterion with no passing probe fails `TESTING`: nothing is
  reported as verified without execution.
- The implementation stage verifies delivery (provider registered, migration applied, contract
  fields declared) and fails permanently when the capability is absent.

## Rationale

Reproducible, honest, and explainable agent behavior matters more for a governed SDLC prototype
than linguistic flexibility; the SPI keeps LLM agents a drop-in extension.

## Consequences

- **Positive**: identical inputs give identical outputs and plans; tests are deterministic.
- **Negative**: requirements outside the catalog's vocabulary are escalated as ambiguity (unknown
  capability), which is safe but limits breadth.
- **Testing**: per-agent unit tests; artifact schema validation; scenario end-to-end runs.
- **Governance**: the permission model makes agent autonomy bounded and auditable.

## Risks and Mitigations

| Risk | Mitigation |
|------|------------|
| Reviewers expect LLM usage | documented rationale; the repository itself was produced by an AI coding agent under SpecKit governance; BL-01 path described |
| Catalog drifts from code | `IMPLEMENTATION` verification checks provider, migration, and contract against the live system |

## Reversibility

High: an AI-backed agent can be registered for any stage, with the deterministic agent as
fallback.

## Traceability

- Requirements: FR-ORC-12..16, NFR-AUT-01, A§4.7; Clarifications Q1; ASM-06; EXC-07
- Plan: §3 agent contract; research R-14
- Tasks: agents and knowledge task group

## Validation

`AgentPermissionTest`, per-agent tests, `ArtifactSchemaTest`, scenario end-to-end tests.
