# ADR-007: Adjacency-List Dependency Graph with Versioned Plan Snapshots

## Status

Proposed (2026-09-26) — pending acceptance at Gate G4.

## Context

The plan must be an explicit dependency graph (A§4.4, FR-ORC-02), validated before execution,
inspectable by reviewers, and versioned with differences whenever it changes (FR-RPL-02).

## Decision Drivers

- Explicitness (a reviewer can read the graph)
- Cheap validation (acyclicity, reachability, gate invariants)
- Diffable versions for replanning evidence

## Options Considered

| Option | Advantages | Disadvantages / risks |
|--------|------------|-----------------------|
| **`stage_node.depends_on` lists + JSON snapshot per `plan_version` with a computed diff** | explicit; trivially validated (Kahn's algorithm); diffable | two representations (live rows and snapshots) to keep consistent |
| BPMN XML | standard notation | heavyweight; poor diffs; needs an engine |
| Graph defined only in code (builder DSL) | type-safe | not inspectable as data; versions not persisted |

## Decision

Live graph: one `stage_node` row per stage with comma-separated `depends_on` keys (each stage type
appears at most once per plan, so the key equals the type). Every structural or conditional change
writes a `plan_version` row with the full graph (stages, dependencies, gate flag, condition) and a
diff (`added`, `removed`, `invalidated`, `dependencyChanges`). `PlanValidator` runs on every new
version and enforces: acyclic, one root (`REQUIREMENT_INGESTION`), all dependencies resolvable,
`RELEASE` reachable only through `RELEASE_APPROVAL`, and every side-effecting stage downstream of
a gate. An invalid plan is never executed; the run is refused, or safe-stopped if the invalid plan
was produced by replanning.

## Rationale

Adjacency lists are the most direct representation of "explicit dependencies"; snapshots make
replanning auditable without reconstructing history from events.

## Consequences

- **Positive**: `GET .../plan-versions` shows the graph and diffs directly.
- **Negative**: snapshot and live rows could diverge. Mitigation: both are written in the same
  transaction by `PlanFactory` / `ReplanningService` only.
- **Testing**: validator tests (cycle, missing dependency, missing gate before release,
  multiple roots).

## Risks and Mitigations

| Risk | Mitigation |
|------|------------|
| Duplicate stage types needed in future plans | key column already separate from type; generalization possible |

## Reversibility

High.

## Traceability

- Requirements: FR-ORC-02, FR-ORC-06, FR-RPL-02, A§4.4
- Plan: §3 structural invariants; data-model.md `plan_version`
- Tasks: plan factory and validator tasks

## Validation

`PlanValidatorTest`, `PlanFactoryTest`, replanning tests asserting diffs.
