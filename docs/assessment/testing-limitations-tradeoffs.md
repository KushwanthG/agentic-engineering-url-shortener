# Testing approach, limitations, and trade-offs (T117)

## 1. Testing approach (ADR-013)

The suite is **layered and contract-first**. Every test class is tagged with the requirement,
scenario, or drill it verifies, and `TraceabilityMatrixTest` enforces the tagging.

| Layer | What it proves | Examples (test classes) |
|---|---|---|
| Unit | Pure rules: URL policy, short codes, retry backoff, clarification derivation, reliability calculator | `UrlPolicyTest`, `RetryPolicyTest`, `ReliabilityReportServiceTest` |
| Integration (Spring, in-memory H2) | Services and persistence; engine behavior with **scripted agents** (deterministic stand-ins) | `RunCoordinatorTest`, `GateDeadlineTest`, `ReplanningServiceTest`, `FailureEventRecorderTest` |
| Contract | HTTP responses match `openapi.yaml`; no drift between the controllers and the contract; artifacts match the JSON schemas | `LinkApiContractTest`, `WorkflowApiContractTest`, `ContractDriftTest`, `ArtifactSchemaTest` |
| Security | Role matrix; malicious-URL catalog; token never logged; secret scan | `SecurityMatrixTest`, `GovernanceSecurityMatrixTest`, `UrlSecurityCatalogIT`, `RepositorySecretScanTest` |
| Architecture | Plane boundaries, agent isolation, no package cycles | `ArchitectureTest` |
| Concurrency | Exactly-N click limits; redirect counting; concurrent gate decisions | `ClickLimitConcurrencyTest`, `RedirectConcurrencyTest`, `ConcurrentGateDecisionTest` |
| End-to-end over real HTTP | The three scenarios and seven drills with **real agents**, a governance-invariant check, and artifact-schema validation of every artifact | `Scenario{A,B,C}*E2ETest`, `ReliabilityDrillsE2ETest`, `RestartResumeTest` |
| Measurement | Latency under 20 concurrent clients (a regression bound only) | `PerformanceMeasurementTest` |
| Traceability | spec ↔ tests ↔ tasks | `TraceabilityMatrixTest` |

The suite contains 103 test classes: 60 in orchestration, 18 in the shortener, 6 end-to-end,
5 contract, 5 common, 4 security, 1 architecture, 1 persistence, 1 traceability, 1 performance, and the
walking skeleton.
[convergence-report.md](convergence-report.md) records the executed totals and results.

**TDD.** Red and green runs are recorded per phase in [tdd-evidence.md](tdd-evidence.md).
- Tests written after the code are labeled *verification*. Examples: `FailureEventRecorderTest`,
  and the Phase 10 quality gates, which tasks.md defines as verification tasks.
- Red runs that stopped differently than planned are recorded as such. For example, SCN-C stopped
  at TESTING rather than IMPLEMENTATION.

**Independence of the evidence.**
- The governance-invariant checker, the MTTR hand calculation, and the audit verification all
  recompute results from recorded data, not from the engine's own view.
- Negative fixtures prove that the checkers can fail: the invariant checker, the traceability
  test (a planted typo tag), the secret scan (planted fixtures), and every artifact schema (it
  rejects `{}`).

## 2. Limitations

**Not implemented (spec MUSTs, deferred under scope decision SD-1):**
- FR-RPL-06: suspending only the affected path when ambiguity is found mid-run (T096). Ambiguity is
  handled at intake (SCN-C).

**Not demonstrated or not run:**
- **NFR-PRF-01.** The latency targets were not reliably met ([performance.md](performance.md)).
- **Deferred tasks (SD-1):**
  - JaCoCo coverage gate (T115). The report is produced; the ≥ 80% threshold is not enforced.
  - Extensibility test (T114).
  - Contract compatibility review (T112).
  - Run reconstruction test (T107).
  - Evidence export scripts and index (T109).
  - Committed traceability matrices (T119). The matrix is generated in `target/`.

**By design for the prototype:**
- **Single process.** Locks, scheduler, probe mutex, and the H2 file database are in-process
  ([overview §5](../architecture/overview.md)).
- **Deterministic agents.** There is no LLM at runtime (ADR-017). Analysis quality is bounded by
  the capability catalog and the ambiguity lexicon: a requirement outside the catalog's
  capabilities cannot be implemented. It is refused, and the refusal is shown by the scenario red
  runs.
- **Implementation is verification, not code generation.** For the three scenario capabilities, the
  code was written under TDD by the AI coding assistant. The runtime IMPLEMENTATION stage verifies
  that a capability is delivered (provider, migration, contract) and refuses undelivered ones; it
  does not write code.
- **Compensation.** A capability that was already released before the run cannot be restored,
  because the release record holds no previous parameters. This is reported as needing manual
  intervention.
- **Refused requests in the audit trail.** A request refused by the URL role check (for example, a
  requester calling a gate decision, which returns `403`) is rejected before it reaches the workflow,
  so it leaves **no entry in the run's audit trail**. Only separation-of-duties refusals are audited
  (`DECISION_REFUSED`). Found during a manual walkthrough on 2026-09-27.
- **Change control.** After a rejected material change, a further material change request on the
  same run is refused (409).
- **Demonstration data.** Metrics and MTTR come from synthetic workloads with injected faults. The
  drill report's population does not include RDR-05, because resume runs in a separate context.
- **Evidence snapshots.** They are regenerated into `target/evidence/`. No committed copy exists
  (T109).

## 3. Trade-offs

| Choice | Benefit | Cost |
|---|---|---|
| A custom persisted DAG engine instead of Temporal or Camunda (ADR-005) | Gates, fingerprints, re-planning, and evidence are first-class and fully testable in-process | More engine code to own; no built-in UI, visibility store, or horizontal scaling |
| Deterministic agents (ADR-017) | Reproducible runs and tests; no model cost or variance; explainable decisions | Limited to the knowledge base; no open-ended reasoning |
| Modular monolith with an enforced port (ADR-001) | One deployable, fast tests, a clear seam to extract later | Both planes scale together until extraction |
| H2 file database (ADR-003, the candidate's directive) | Zero setup; state survives restarts | Single instance; not a production database |
| Exact synchronous click counting (ADR-016) | Exact analytics and exact click limits | A write on every redirect; probable cause of the missed redirect latency target |
| Approvals bound to artifact fingerprints (ADR-008) | No approval survives a change to what was approved | Re-planning can require re-approval (mitigated by carrying approvals over for unchanged artifacts) |
| Scope decision SD-1 (timebox) | The three scenarios, the drills, and the evidence are complete | Two FR MUSTs and several quality tasks are deferred |
