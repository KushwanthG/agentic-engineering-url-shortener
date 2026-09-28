# Final engineering summary

The summary follows the guide's mandatory 21-section schema, in order. It was generated on
2026-09-27 at commit `2a2a0bf`, after convergence (T121).

**How claims are labeled.** Each material claim cites a path or an identifier, and a command with
its observed result. Claims are labeled by kind:
- **[Confirmed]**: a requirement verified by an executed test;
- **[Assumption]**: an approved or provisional assumption;
- **[Measurement]**: demonstration data;
- **[Recommendation]**: a production recommendation;
- **[Deferred]**: scope deferred under SD-1;
- **[Limitation]**: an accepted or proposed limitation;
- **[Blocker]**: unresolved.

**About the agents.** They are **deterministic, knowledge-driven workers**; none uses an LLM at
runtime (ADR-017). All human decisions in the automated evidence are **simulated input** from
labeled demo principals.

## 1. Executive engineering outcome and release-readiness status

- **The system works end to end.** A governed, dependency-aware control plane takes requirements
  through 19 stage types, with human gates, policy compliance, and evidence, to capability releases
  in a working URL shortener.
- **Final build.** `mvnw -B -ntp clean verify` at `2a2a0bf` ran **530 tests, 0 failures, BUILD
  SUCCESS** [Confirmed].
- **Scenarios and drills.** All three scenarios complete with readiness `READY`, and all seven
  reliability drills behave as specified [Confirmed].
- **Proposed release readiness: NOT READY**
  ([release-readiness.md](release-readiness.md)). The reasons are:
  - G1–G7 are unratified and all ADRs are `Proposed` [Blocker: the candidate's action];
  - FR-ORC-18 and FR-RPL-06 are not implemented [Deferred];
  - the performance targets were missed [Measurement].

## 2. Project objective, scope, and 2–3-day timebox outcome

**Objective:** build an agentic software-engineering system that governs a URL shortener, using
SpecKit spec-driven development ([spec.md](../../specs/001-agentic-url-shortener/spec.md)).

**Timebox:** the work ran under scope decision **SD-1** ([timebox-and-scope.md](timebox-and-scope.md)),
which follows the candidate's instruction to implement what submission needs. At checkpoints
CP1–CP3 all must-have scope was complete.
- **Delivered:** Phases 1–10: US1–US7, the three scenarios, the seven drills, evidence, and quality
  gates.
- **Deferred [Deferred]:** T096, T107, T109, T112, T114, T115, T119. (T074 was deferred too and has
  since been implemented at the candidate's instruction.)

## 3. Confirmed requirements, assumptions, exclusions, and deferred scope

**Requirements.** The specification has 92 FRs, 26 NFRs, 9 SCs, 3 scenarios, and 7 drills.
`mvnw test -Dtest=TraceabilityMatrixTest` gives 126 of 136 ids with tests; every FR and scenario is
tested except FR-ORC-18 and FR-RPL-06 [Confirmed / Deferred].

**Assumptions** are recorded in spec.md (ASM-*). Examples:
- bearer tokens stand in for an identity provider (ASM-02);
- deterministic agents (G3 Q1);
- H2, Java 21, and Spring Boot were the candidate's directives.

These are provisional until the candidate ratifies G2 and G3 [Assumption].

**Exclusions:** URL reputation scanning (EXC-06), among the other EXC-* entries in spec.md
[Limitation].

## 4. Architecture overview and accepted ADRs

**Design.** The system is a modular monolith with two planes and an ArchUnit-enforced port
([overview.md](../architecture/overview.md)), built on a custom persisted DAG engine (ADR-005).
`ArchitectureTest` is green [Confirmed].

**ADRs.** There are 19 ([docs/adr/README.md](../adr/README.md)). **None is accepted: all are
`Proposed` and await G4** [Blocker].

## 5. API contracts and schema deliverables

- **Contracts.**
  [openapi.yaml](../../specs/001-agentic-url-shortener/contracts/openapi.yaml) defines 29
  operations, and there are 13 artifact JSON schemas. Contract history is in
  [CHANGELOG.md](../../specs/001-agentic-url-shortener/contracts/CHANGELOG.md).
- **Checks** [Confirmed]:
  - `ContractDriftTest`: no drift in either direction;
  - `LinkApiContractTest`, `WorkflowApiContractTest`, `EvidenceControllerTest`: responses
    validated;
  - `ArtifactSchemaTest` and per-run validation in the scenario e2e tests: every artifact of SCN-A,
    SCN-B, and SCN-C validates.
- **Migrations.** V1–V4 run through Flyway; V4 is the click-limit migration (`MigrationTest`).
- **Not done.** The compatibility review and the change-control approval of contract versions
  (T112) [Deferred].

## 6. Orchestration model, dependency graph, and state persistence

**Graph.** Each run is a versioned DAG plan (`PlanFactory`, `PlanValidator`) persisted as stage
nodes, attempts, artifacts, decisions, and a hash-chained audit trail (ADR-006, ADR-007).

**Scheduling.** A per-run lock serializes changes, dispatch happens after commit, and stale
results are discarded (`RunCoordinatorTest`).

**Parallelism.** Parallel stages are shown by the SCN-A timeline, where TESTING and
SECURITY_VERIFICATION overlap. It was also observed by hand in
[quickstart-validation.md](quickstart-validation.md) [Confirmed].

**Recovery.** State survives restarts (`RestartResumeTest`, RDR-05) [Confirmed].

## 7. Human approvals, governance gates, and decision lineage

- **Gate controls.** The gates are CLARIFICATION, ARCHITECTURE_APPROVAL, CHANGE_APPROVAL, and
  RELEASE_APPROVAL. They enforce roles, separation of duties, fingerprint-bound approvals, deadlines,
  and concurrent-decision detection. Tests: `SeparationOfDutiesTest`, `ApprovalBindingTest`,
  `GateDeadlineTest`, `ConcurrentGateDecisionTest` [Confirmed].
- **Lineage.** Decision lineage is served per artifact (`LineageServiceTest`) [Confirmed].
- **Invariants.** The governance invariants hold on all 10 end-to-end runs. The checker
  (`GovernanceInvariants`) was proven first against violating fixtures in
  `GovernanceInvariantsTest` [Confirmed].
- **Real gates.** The project's own gates G1–G7 are unratified [Blocker].

## 8. Compliance and change-control policy results

- **Policy set.** It is versioned ([policy-set.md](../governance/policy-set.md)) and pinned per run
  (ADR-019).
- **Scenario results.** SCN-A, SCN-B, and SCN-C reach `READY` with no mandatory failure. In the
  T120 manual SCN-A run, the 14 evaluations were 12 `PASS` and 2 `NOT_APPLICABLE` [Confirmed].
- **Change control.**
  - The SCN-B impact analysis was committed before the click-limit code (`53d8fc4` before
    `86e4320`).
  - A material change after an approval inserts `CHANGE_APPROVAL` (`ChangeRequestFlowTest`)
    [Confirmed].

## 9. Policy exceptions, compensating controls, expiry, and approvals

RDR-06 proves the exception path (`ReliabilityDrillsE2ETest`, `PolicyExceptionFlowTest`)
[Confirmed]:
- a failed mandatory policy blocks the release;
- an exception needs a compensating control, an expiry, and an approver other than the requester;
- an approved exception leads to `READY_WITH_ACCEPTED_LIMITATIONS`, and a rejected one to
  `SAFE_STOPPED`;
- the invariant checker verifies that no release uses an unapproved or expired exception.

No exception exists for this project's own release.

## 10. Security controls, findings, and residual risks

**Controls.** See [SECURITY.md](../../SECURITY.md): the URL safety catalog, hashed tokens, role
matrices, agent permissions, and audit tamper detection [Confirmed].

**Findings in this phase:**
- Convergence found **Spring Boot's generated password logged at startup** (a constitution V
  violation). It was fixed by T133 and verified by `StartupLogSecurityTest` (red, then green)
  [Confirmed].
- The secret scan passes with 0 unexplained findings.
- The **dependency scan** (T128, OSV-Scanner v2.6.0) found 3 CRITICAL advisories in Tomcat 11.0.24. They were fixed by upgrading to Tomcat 11.0.26 (`pom.xml` override; 530 tests green), and the re-scan reports no issues [Confirmed].

**Residual risks** are in the [risk register](risk-register.md): no URL reputation scanning,
failed authentication not throttled (L7), no autonomy budget [Limitation].

## 11. Reliability, retry, fallback, rollback/compensation, and safe-stop

Drills RDR-01 to RDR-07 are green (`ReliabilityDrillsE2ETest`, `RestartResumeTest`;
[drills.md](../scenarios/drills.md)) [Confirmed]:

| Drill | What it shows |
|---|---|
| RDR-01 | bounded retry (3 attempts) |
| RDR-02 | template fallback that lowers readiness |
| RDR-03 | post-release verification failure: release rolled back and run safe-stopped |
| RDR-04 | gate deadline leads to a safe-stop |
| RDR-05 | resume after restart |
| RDR-06 | policy exception |
| RDR-07 | pause, resume, and safe-stop |

**Limitation:** a capability released *before* a run cannot be restored by compensation
[Limitation].

## 12. MTTR definition, measurement population, calculation, exclusions, and unrecovered failures

**Definition** (plan.md §7): MTTR = Σ(`recovery_completed_at` − `detected_at`) over RECOVERED
events ÷ the number of RECOVERED events. Unrecovered events are listed and excluded; OPEN and
superseded events are excluded with counts.

**Drill-suite measurement** [Measurement] ([mttr-validation.md](mttr-validation.md)):
- population: 7 runs, 76 attempts;
- recovered: 236 ms (RETRY) and 89 ms (FALLBACK), so MTTR = 162.5 ms;
- unrecovered: 1 (RDR-03 RELEASE, VERIFICATION_FAILURE);
- exclusions: 0 and 0.

The calculation reproduces by hand.

**Limitations:** synthetic faults on a single machine, and RDR-05 is outside this population.

## 13. Observability, auditability, and evidence integrity

- **Metrics and logs.** Micrometer meters and an `sdlc.stage` observation per attempt; no run id as
  a tag; MDC fields `runId`, `stage`, and `attempt` (`MetricsTest`) [Confirmed].
- **Audit.** A SHA-256 audit chain per run; direct SQL tampering is reported with the first broken
  sequence (`EvidenceControllerTest`, `AuditTamperDetectionTest`) [Confirmed].
- **Evidence exports.** They carry provenance and a `simulatedInput` flag.
- **Not built:** committed evidence snapshots (T109) and run reconstruction (T107) [Deferred].

## 14. Greenfield well-defined requirement scenario outcome

**SCN-A**, custom aliases ([scn-a-greenfield.md](../scenarios/scn-a-greenfield.md),
`ScenarioAGreenfieldE2ETest`) [Confirmed]:
- clarification was **skipped with recorded evidence**: five quality checks passed and no blocking
  ambiguity was found;
- the architecture and release gates were decided (simulated);
- parallel verification ran;
- the run completed `READY` and `custom-alias` was released.

Driven by hand in T120, it gave the same outcome.

**Not built:** the SCN-A escalation path for ambiguity found mid-run (T096) [Deferred].

## 15. Brownfield scenario outcome

**SCN-B**, click-limited links ([scn-b-brownfield.md](../scenarios/scn-b-brownfield.md),
`ScenarioBBrownfieldE2ETest`, `BrownfieldImpactGateIT`) [Confirmed]:
- the impact analysis (a codebase import graph with seed and derived components) was gated and
  committed before the code;
- the red run was refused before delivery;
- the V4 migration and exactly-N atomic limits were delivered (`ClickLimitConcurrencyTest`);
- regression probes passed;
- the run completed `READY`.

## 16. Ambiguous-requirement scenario outcome

**SCN-C**, "better link expiry" ([scn-c-ambiguous.md](../scenarios/scn-c-ambiguous.md),
`ScenarioCAmbiguousE2ETest`) [Confirmed]:
- 6 ambiguities were detected (5 blocking) and the run stopped at clarification before
  decomposition;
- 7 questions were answered (simulated D1–D4);
- the requirement moved to version 2 with derived acceptance criteria;
- the run re-planned to plan version 2 (impact analysis and regression testing were added) and
  resumed;
- the run completed `READY`, and default expiry was released with 30 days.

A live walkthrough for the candidate is documented in §7 of the scenario document.

## 17. Test strategy, executed validation, and actual results

**Strategy:** layered and contract-first ([testing-limitations-tradeoffs.md](testing-limitations-tradeoffs.md)).
There are 103 test classes.

**Executed validation** [Confirmed]:
- **Final build:** **530 tests, 0 failures, BUILD SUCCESS, 163 s** (`clean verify` at `2a2a0bf`).
- **Random test order:** 528/528 green.
- **Clean clone:** 528/528 green, and quickstart §1–§6 pass.
- **Line coverage:** **95.0%** (named packages 88.9–96.9%), measured but not enforced (T115)
  [Deferred].

**TDD.** The red and green runs are in [tdd-evidence.md](tdd-evidence.md), including tests written
after their code, which are labeled *verification*.

**Performance [Measurement]:** redirect p95 was 119 and 220 ms against a 50 ms target, so
**not met** ([performance.md](performance.md)).

## 18. Requirement-to-design-to-task-to-code-to-test traceability

The chain is: spec FR/NFR/SC ids → plan and ADR references → every task's `Req` field
(enforced) → the code named in the task → the test `@Tag`s (enforced).
`mvnw test -Dtest=TraceabilityMatrixTest` generates `target/traceability/requirements-to-tests.md`
and fails on untested FRs and scenarios, untagged tests, unknown ids, and tasks without `Req`.
A planted typo was detected [Confirmed].

**Not done:** the committed matrix and the ADR-evidence map (T119) [Deferred].

## 19. Known limitations, technical debt, and deferred enhancements

**Deferred [Deferred]:**
- FR-RPL-06 (T096);
- T107, T109, T112, T114, T115, T119.

**Limitations [Limitation]:**
- performance targets not met, and T134 is open;
- single process: locks, scheduler, and H2 (see
  [overview §5](../architecture/overview.md) for the scaling path);
- compensation cannot restore a capability released before the run;
- a second material change after a rejected change gate is refused;
- deterministic agents are bounded by their knowledge base, and IMPLEMENTATION verifies delivery
  rather than generating code.

Full list: [testing-limitations-tradeoffs.md](testing-limitations-tradeoffs.md),
[risk-register.md](risk-register.md).

## 20. Repository paths, reproducible commands, and reviewer verification points

See [reviewer-guide.md](reviewer-guide.md), which covers the guide's 25 navigation items. The core
commands are:
- `mvnw -B -ntp clean verify`;
- `mvnw spring-boot:run -Dspring-boot.run.profiles=demo`;
- `mvnw test -Dtest=Scenario*E2ETest`;
- `mvnw test -Dtest=TraceabilityMatrixTest`.

## 21. Final engineering judgment, unresolved blockers, and recommended next actions

**Judgment.** The engineering core is sound and evidenced: governed orchestration, three scenarios,
seven drills, independent invariant and MTTR checks, and a tested security baseline. The proposal
stays **NOT READY** because of the unresolved blockers below. They are governance and scope
decisions, not defects hidden in the code.

**Unresolved blockers [Blocker]:**
1. Ratify G1–G5 and decide each ADR: the candidate.
2. Decide on FR-RPL-06: implement T096 (about 2 h), or accept it at G6. (FR-ORC-18 is implemented:
   T074.)
3. Accept the performance miss (T134), or have it profiled and fixed.
4. Review the deviations in the gate register.

**Recommended next actions [Recommendation]:**
- Before submission: G6/T125 and G7/T126 (tag `assessment-submission-v1.0`).
- For production:
  - PostgreSQL with database locks and leader-elected scheduling;
  - an identity provider;
  - asynchronous click counting for links without a click limit;
  - URL reputation scanning;
  - external audit anchoring.

**Pre-implementation review findings RC-1 to RC-8
([pre-implementation-review.md](pre-implementation-review.md)) and their disposition:**

| Finding | Disposition |
|---|---|
| RC-1 | Fingerprints include the agent id and the knowledge version (`InputFingerprinterTest`): **done** |
| RC-2 | Scheduling cycles are recorded in the evidence, and SCN-A exports them: **done** |
| RC-3 | The impact analysis labels components as seed or derived: **done** |
| RC-4 | Provenance is on the evidence (**done**); the copy script is **deferred** (T109) |
| RC-5 | ArchUnit keeps agents away from security and network code: **done** |
| RC-6 | The risk is recorded, the executor gauge exists, and the query timeout is configured: **done**, with the timeout untested |
| RC-7 | The documents describe the agents as deterministic: **done** |
| RC-8 | The runbook documents the graceful stop and the manual kill variant: **done** |
