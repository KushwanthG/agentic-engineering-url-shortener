# Pre-Implementation Principal Engineer Review (guide §14)

**Date**: 2026-09-26 · **Reviewer stance**: independent and skeptical. The artifacts were produced
by the same AI assistant, so this review deliberately argues against them. It reads the
repository as if submitted by another candidate. No files were modified during the review;
corrections were applied afterwards in a separate commit.

**Scope reviewed**: constitution, spec (clarified), plan, research, data model, contracts, 19 ADRs,
checklists, tasks (132), analysis report (runs 1–2), gate register.

## Verdict

**PROCEED WITH CONDITIONS.** Eight required corrections (RC-1..RC-8) must be applied before
implementation, plus the standing HIGH governance condition: every human gate, including this
one, is pending the candidate's ratification.

## Answers to the review questions

| # | Question | Assessment |
|---|----------|------------|
| 1 | Genuine stateful orchestration or linear chaining? | Genuine: a persisted DAG with joins, conditional nodes, gates, content-addressed reuse, and structural replanning. **Weakness**: the graph is a fixed 19-type template toggled by conditions; plans never grow from the decomposition (for example one stage per task). A reviewer could call this "a pipeline with branches". Defensible, because every scenario's structure differs (SCN-B adds stages, SCN-C changes the plan mid-run, change requests insert a gate), but it must be presented candidly. |
| 2 | Where is parallel execution demonstrated? | Planned in the engine test (T040) and the SCN-A timeline (T057). **Weakness**: deterministic agents finish in milliseconds, so wall-clock overlap of real stages can be accidental or absent; overlap alone is weak evidence → **RC-2**. |
| 3 | Where does synchronization occur? | `DESIGN` and `VALIDATION` joins; the negative case (failed predecessor) is tested. Adequate. |
| 4 | How is state persisted and resumed? | Relational state, persisted before side effects; `RecoveryService`; restart test with two application contexts. **Weakness**: closing a Spring context is a graceful stop, not a crash; the evidence must say so → **RC-8**. |
| 5 | How is decision lineage preserved? | Decisions bound to artifact fingerprints; lineage walk over input refs. Adequate. |
| 6 | Which actions require approval? | Clarification, architecture, change control, policy exception, release (plan §5). Adequate. |
| 7 | Can mandatory approval be bypassed? | Not through the API, the engine guard, or agent code (ArchUnit). **Gaps**: nothing forbids agent code from making HTTP calls back into the application's own endpoints → **RC-5**. In the demo all tokens are known to one person, so separation of duties rests on token custody (inherent to a demo; must be disclosed). |
| 8 | Are retry bounds explicit? | Yes (PVT-11, per-stage table, budget). |
| 9 | Where is timeout behavior defined? | Per stage (plan §3). **Weakness**: Java cannot kill a thread; an agent that ignores interruption keeps a worker from the bounded pool, and repeated stuck agents could starve the engine → **RC-6**. |
| 10 | Is rollback technically feasible? | Only claimed for flag state, which is feasible and idempotent. Good. |
| 11 | Where is compensation used instead? | Synthetic data, capability exposure, append-only audit. Good. |
| 12 | What triggers safe-stop? | Enumerated (FR-REL-06); each trigger has a test (T071). Good. |
| 13 | How does replanning avoid uncontrolled change? | Change-control gate, fingerprint-bound approvals, bounded clarification rounds, plan validation. **Correctness flaw**: fingerprints cover input artifacts but not the agent version or the knowledge resources an agent reads (capability catalog, lexicon, policy set). A change to those could let a stale output be reused → **RC-1**. |
| 14 | Can every major claim be validated? | Mostly yes, through tests and exported evidence. **Risk**: "agentic" can be over-read as LLM reasoning; the agents are deterministic knowledge-driven workers → **RC-7**. |
| 15 | Are accepted ADRs reflected in tasks? | No ADR is accepted; tasks follow the Proposed ADRs consistently (analysis found no ADR conflicts). Standing governance condition. |
| 16 | Are requirements independently testable? | Yes; numeric targets are labeled assumptions. |
| 17 | Are tasks small and dependency-correct? | Mostly, after the analysis split. T041 (engine core) and T049 (probes plus two agents) remain large but coherent; they should be committed in internal red/green steps. Dependencies verified mechanically (no dangling references). |
| 18 | Is the brownfield scenario credible? | Largely: real source scan, executed before the code, migration, concurrency, fail-open vs. fail-closed conflict. **Weakness**: the scan is seeded from a catalog the same author wrote, so parts of the "impact" are predetermined. Evidence must separate declared seeds from impacts derived by the import graph → **RC-3**. |
| 19 | Does the ambiguous scenario truly suspend unsafe execution? | Yes: downstream stages stay `PENDING` and tests assert no attempts start; answers in automated runs are labeled simulated; a live walkthrough lets the candidate answer. |
| 20 | Is the evidence strategy authentic? | Generated by tests, simulated inputs labeled, truthful history. **Gap**: exported evidence files carry no provenance (commit, command, time, JDK, profile), and nothing prevents hand edits of the copies in `docs/` → **RC-4**. |

## Critical findings

None that block implementation outright once RC-1..RC-8 are applied. RC-1 is a correctness defect
in the replanning design and must be fixed upstream before the replanning tasks run.

## Architectural weaknesses

- Shared JVM and database for both planes: control-plane load can affect redirect latency
  (ADR-001 accepted trade-off; bounded pool and budgets mitigate).
- The capability catalog couples the control plane to application-plane component names; drift
  is detected only at the `IMPLEMENTATION` stage.
- Single-instance coordination (in-JVM locks) — documented, BL-05.

## Orchestration weaknesses

- Fixed stage template (question 1).
- Parallelism evidence quality (RC-2).
- Stuck-agent pool starvation (RC-6).
- Fingerprint scope (RC-1).

## Governance weaknesses

- All gates pending; checklists unreviewed (0/167); ADRs `Proposed` (standing condition).
- Demo separation of duties depends on token custody (disclose).

## Testing weaknesses

- Asynchronous end-to-end tests risk flakiness; Awaitility with generous bounds is planned.
- The restart test is a graceful stop (RC-8).
- MTTR data comes only from simulated faults with sub-second recoveries; it demonstrates the
  mechanism, not a meaningful number (already labeled; keep it that way).

## Evidence weaknesses

- Missing provenance metadata on exported evidence (RC-4).
- Seed vs. derived impact not distinguished (RC-3).

## Required corrections before implementation

| ID | Correction | Upstream artifact |
|----|------------|-------------------|
| RC-1 | Stage input fingerprints MUST include the agent id and version and the versions of every knowledge resource the agent reads (capability catalog, ambiguity lexicon, policy set) in addition to input artifacts and relevant decisions | ADR-011; tasks T093/T094 |
| RC-2 | Parallelism evidence: audit dispatch events carry a scheduling-cycle number, and the scenario evidence shows the parallel stages dispatched in the same cycle. The engine test proves real concurrency with sleeping scripted agents (elapsed time well below the sum of sleeps) | tasks T040, T041, T057 |
| RC-3 | Impact-analysis output labels each component as a catalog seed or as derived through the reverse-dependency closure (with the dependency chain); the test requires at least one derived component that is not a seed | tasks T084 |
| RC-4 | Exported evidence carries provenance (git commit, command, timestamp, JDK, active profile, "simulated" flags) and is copied into `docs/` only by a script, never edited by hand | tasks T015, T109 |
| RC-5 | ArchUnit rule: agents must not depend on HTTP client classes or on the security package | tasks T065 |
| RC-6 | Record stuck-agent pool starvation as a risk with mitigations (cooperative interruption checks in agents, JDBC query timeout, an active-worker gauge) | ADR-009; tasks T068, T106 |
| RC-7 | Documentation describes agents as deterministic, knowledge-driven workers and never implies LLM reasoning | tasks T116, T117, T123 guardrails |
| RC-8 | Restart evidence labeled "graceful context stop"; the runbook documents a manual process-kill variant of RDR-05 | tasks T073, T081 |

## Conclusion

The design is defensible and substantially above linear chaining. With RC-1..RC-8 applied,
implementation may start **provisionally**, under the recorded delegation, with G1–G5 pending
the candidate.
