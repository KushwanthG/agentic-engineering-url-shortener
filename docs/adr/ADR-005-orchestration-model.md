# ADR-005: Purpose-Built Persisted DAG Orchestration Engine

## Status

Proposed (2026-09-26) — pending acceptance at Gate G4.

## Context

Workflow orchestration is the assessment's critical differentiator (A§4.4). It must support an
explicit dependency graph with entry and exit gates, sequential and parallel paths with
synchronization, persisted state and decision lineage, human approval checkpoints, bounded
retries, fallback, rollback or compensation, safe-stop, resumption, policy guardrails,
audit-grade observability, reliability metrics, and dynamic replanning under governance
(FR-ORC-*, FR-GOV-*, FR-REL-*, FR-RPL-*, FR-POL-*, FR-AUD-*). A linear agent chain earns no credit
(constitution II).

## Decision Drivers

- Fidelity to the governance semantics (approvals bound to artifact fingerprints, policy-blocked
  stages waiting for exceptions, permission-scoped agents, fingerprint-based reuse)
- Inspectability: reviewers must see the graph, states, and decisions directly
- No additional infrastructure; deterministic tests
- Timebox

## Options Considered

| Option | Approach | Advantages | Disadvantages / risks | Assessment implications |
|--------|----------|------------|-----------------------|-------------------------|
| **A** | Custom engine: persisted stage nodes with explicit dependencies, a per-run coordinator, bounded worker pool, transition tables | every governance rule is explicit, testable, and visible | the engine itself must be built and tested thoroughly | orchestration logic is the visible, gradeable asset |
| B | Temporal (workflow-as-code) | durable execution, retries, timers | server infrastructure; governance still custom; determinism constraints on workflow code | heavy setup; logic hidden in a framework |
| C | Camunda or Flowable (BPMN) | visual models, user tasks | heavy embedded engine; BPMN obscures fingerprint binding and replanning | reviewers grade BPMN plumbing rather than design |
| D | Spring State Machine | typed states | models one entity's states, not a dependency graph with joins | would still need a custom graph layer |
| E | LangGraph4j | graph runtime for LLM agents | oriented to LLM tool loops (Q1 chose deterministic agents); persistence and governance still custom | mismatch with the chosen agent model |

## Decision

Option A. Core types: `WorkflowRun`, `StageNode` (explicit `dependsOn`, `generation`, `status`,
`awaiting`), `StageAttempt`, `PlanVersion`; `RunCoordinator.advance()` is the only scheduler. It
runs under a per-run lock, evaluates conditions and entry criteria, opens gates, reuses stages
whose input fingerprints are unchanged, and dispatches ready stages to a bounded `stageExecutor`
(fan-out). Joins are expressed purely as multiple dependencies. `StageTransitions` and
`RunTransitions` tables define allowed transitions; everything else is rejected and audited.

## Rationale

The required semantics are the product. General-purpose engines supply retries and timers, which
are the easy part, while the governance-specific behavior would still have to be custom code
layered on top, plus infrastructure. A focused engine of a few thousand lines is fully testable
with scripted agents.

## Consequences

- **Positive**: explicit, inspectable orchestration; the plan graph is an API resource;
  governance rules sit next to the scheduling decisions they constrain.
- **Negative**: the engine's correctness is our responsibility; concurrency bugs are the main risk.
- **Operational**: single-instance coordination (see ADR-006 for multi-instance limits).
- **Testing**: transition-table tests, scripted-agent engine tests (parallelism, joins,
  conditions, reuse, budgets), restart tests.
- **Governance**: gates cannot be satisfied without decisions (transition guard).

## Risks and Mitigations

| Risk | Mitigation |
|------|------------|
| Race conditions between attempt completion and decisions | single-writer coordinator per run, optimistic versions, stale-result discard by (generation, attempt) |
| Deadlocks between run lock and database | locks never held across agent execution; short transactions |
| Scope creep | engine features limited to spec requirements; checkpoints CP2/CP3 |

## Reversibility

Low to medium. The engine is the core asset; the agent SPI and persistence model would survive a
move to another engine, but scheduling code would be replaced.

## Traceability

- Requirements: FR-ORC-02..11, FR-ORC-18, FR-GOV-02, FR-REL-*, FR-RPL-*; A§4.4; constitution II
- Plan: §3, §6; research R-12
- Tasks: orchestration state model, coordinator, and dispatcher task groups

## Validation

Orchestration test suites (transitions, parallel overlap evidence, join semantics, conditional
skips with reasons, reuse, budgets) and the three scenario end-to-end tests with timelines.
