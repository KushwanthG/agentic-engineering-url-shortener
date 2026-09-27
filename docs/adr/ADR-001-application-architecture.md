# ADR-001: Modular Monolith with Port-Separated Application and Control Planes

## Status

Proposed (2026-09-26) — pending acceptance by the human candidate at Gate G4.

## Context

The system has two very different responsibilities: a latency-sensitive public URL shortener
(application plane) and a governed SDLC orchestration engine (control plane) that verifies and
releases changes to that shortener. Constitution Principle VII requires separated concerns with
explicit interfaces and forbids the application plane from depending on the control plane. The
Delivery Constraints require local runnability and forbid unjustified distributed-system
complexity. Relevant requirements: NFR-MNT-01/02, NFR-SCA-01, FR-CAP-01, FR-ORC-12, CON-02, CON-04.

## Decision Drivers

- Enforceable boundaries between planes (reviewers will look for them)
- Minimal setup for reviewers; deterministic restart and resume demonstrations
- No failure modes without a requirement behind them (network partitions, service discovery)
- Evolvability towards separate deployments if scale ever requires it

## Options Considered

| Option | Approach | Advantages | Disadvantages / risks | Implementation impact | Assessment implications |
|--------|----------|------------|-----------------------|-----------------------|-------------------------|
| A | **Single deployable; packages `platform`, `shortener`, `orchestration`; control plane uses `ApplicationPlanePort`; ArchUnit rules** | one process, simple debugging, boundaries still enforced by tests | boundaries are test-enforced rather than compile-enforced | low | clean, defensible, demonstrable |
| B | Two services (shortener and orchestrator) over HTTP | physical isolation; independent scaling | network failure modes, auth between services, two runtimes for reviewers | high | complexity without a driving requirement |
| C | Maven multi-module (`shortener`, `orchestration`, `app`) | compile-time boundaries | more build ceremony and configuration; slower iteration within the timebox | medium | slightly stronger isolation, same runtime |

## Decision

Option A. One Spring Boot application with top-level packages `com.agentic.urlshortener.common`,
`com.agentic.urlshortener.shortener`, and `com.agentic.urlshortener.orchestration`. The control plane reaches the
shortener **only** through `orchestration.port.ApplicationPlanePort`, implemented by an in-process
adapter. ArchUnit tests enforce: the shortener never imports orchestration; only the port adapter
imports shortener services; agents never import governance or policy mutation services. Inside each
plane, code follows the conventional Spring Boot layers (`controller`, `dto`, `domain`,
`repository`, `service`, `config`); the control plane adds its engine components (`engine`,
`planning`, `governance`, `policy`, `reliability`, `audit`, `metrics`, `agent`, `knowledge`, `port`).

## Rationale

Option A satisfies Principle VII's separation (verifiable by executable architecture tests) with
the lowest operational cost, and keeps restart and resume evidence deterministic because a single
process owns the database. Option C's compile-time guarantee is valuable but not worth the timebox
cost when ArchUnit provides equivalent, test-enforced rules.

## Consequences

- **Positive**: one command to build and run; shared transaction manager and database;
  straightforward end-to-end tests.
- **Negative**: both planes share one JVM, so a control-plane memory leak affects redirects.
  Mitigation: bounded worker pool and autonomy budgets.
- **Operational**: one deployable and one health endpoint; scaling the redirect path also scales
  the orchestrator.
- **Testing**: architecture tests become part of the build gate.
- **Governance**: the port is the single audited seam where the control plane changes the
  application plane.

## Risks and Mitigations

| Risk | Mitigation |
|------|------------|
| Package boundaries erode over time | ArchUnit rules fail the build |
| A future need for independent scaling | the port interface is the extraction seam (replace the in-process adapter with an HTTP client) |

## Reversibility

Medium. Extracting the control plane into its own service requires a remote port adapter and
separate persistence, but no change to domain logic.

## Traceability

- Requirements: NFR-MNT-01, NFR-MNT-02, NFR-SCA-01, FR-ORC-12, FR-CAP-01; constitution VII
- Spec sections: Overview (two planes); Constraints CON-02/04
- Plan sections: Project Structure; §1 System Context; §2; §3
- Tasks: architecture test and port tasks in `tasks.md` (assigned by `/speckit-tasks`)

## Validation

`ArchitectureTest` (ArchUnit) rules run in every build; a code review in the pre-implementation
review confirms the port is the only plane-crossing dependency.

## Revision history

- 2026-09-26: Base package renamed from `com.agentic.sdlc` to `com.agentic.urlshortener` at the
  candidate's request (naming only; plane structure, dependency rules and decision unchanged). The
  ADR remains `Proposed`.
- 2026-09-26: Shared infrastructure package renamed `platform` → `common`, and each plane organised
  into conventional Spring Boot layers (`controller`, `dto`, `domain`, `repository`, `service`,
  `config`) at the candidate's request. Plane boundaries and ArchUnit rules unchanged (the `common`
  rule replaces the `platform` rule). The ADR remains `Proposed`.
