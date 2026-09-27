# SCN-C — Ambiguous: vague and conflicting request (link expiry)

Scenario definition: [spec.md § SCN-C](../../specs/001-agentic-url-shortener/spec.md). Executable
proof: `ScenarioCAmbiguousE2ETest` (real agents, HTTP only). Component tests:
`ClarificationFlowTest`, `ReplanningServiceTest`, `InputFingerprinterTest`, `ChangeRequestFlowTest`,
and `DefaultExpiryTest`.

> **Simulated versus candidate decisions.** In the automated test, the approver `bob` answers the
> clarification with the spec's reference decisions D1–D4, and `bob`/`carol` approve the gates.
> These are **simulated human input** from labeled demo principals, not the candidate's decisions.
> Section 7 describes a live walkthrough in which the candidate answers personally.

## 1. Input

Fixed requirement `AMB-001`
([`scenarios/scn-c-ambiguous.json`](../../src/main/resources/scenarios/scn-c-ambiguous.json)):
"Better link expiry". The type is not specified, and there are no acceptance criteria. The
narrative: *"Links should expire after a while so old links don't pile up, but premium users' links
should never expire. Also make the analytics better."*

## 2. Detected ambiguities (evidence C-02)

The `ambiguity-lexicon.yaml` knowledge detected six ambiguities deterministically:

| Id | Type | Text | Severity | Blocking |
|---|---|---|---|---|
| AMB-1 | VAGUE_TERM | "after a while" | HIGH | yes |
| AMB-2 | UNDEFINED_CONCEPT | "premium users" (the system has no user tiers) | HIGH | yes |
| AMB-3 | CONFLICT | "expire / never expire" | HIGH | yes |
| AMB-4 | UNBOUNDED_SCOPE | "make the analytics better" | MEDIUM | yes |
| AMB-5 | MISSING_ACCEPTANCE_CRITERIA | — | HIGH | yes |
| AMB-6 | UNSPECIFIED_TYPE | — | LOW | no |

The analysis matched catalog capability `default-expiry`, which changes existing link-creation
behavior. So the clarification request also asks whether existing links are affected (Q-7).

## 3. Suspension (evidence C-04 .. C-07)

1. After analysis, the `CLARIFICATION` gate opens (`AWAITING_DECISION`, awaiting `CLARIFICATION`),
   and the run is `AWAITING_HUMAN`.
2. The timeline before the answers contains only `REQUIREMENT_INGESTION` and
   `REQUIREMENT_ANALYSIS`. Decomposition, impact analysis, threat assessment, design, and
   implementation did not start (asserted).
3. The reason recorded in the clarification request: *"5 blocking ambiguities must be resolved by
   an APPROVER before decomposition: [...]"*.
4. It asks 7 questions (Q-1..Q-7). Each quotes the ambiguous phrase and offers options, and every
   option states its impact.

## 4. Decisions and the updated requirement (evidence C-08, C-09)

| Question | Reference decision (simulated) | Effect on requirement version 2 |
|---|---|---|
| Q-1 "after a while" | B: 30 days (**D1**) | release parameter `defaultExpiryDays = 30` |
| Q-2 "premium users" | A: out of scope, no link is exempt (**D2**) | scope exclusion |
| Q-3 conflict | A: the general rule applies without exceptions | constraint |
| Q-4 analytics | A: defer to the backlog (**D3**) | scope exclusion |
| Q-5 no criteria | A: derive them from the decisions | 3 acceptance criteria from the catalog templates |
| Q-6 type | B: change to existing behavior | `type = CHANGE_TO_EXISTING` |
| Q-7 existing links | A: existing links are unchanged (**D4**) | constraint |

Each answer is a `CLARIFICATION_ANSWER` decision by `bob` (role APPROVER). Requirement version 2
(source `CLARIFIED`) derives these acceptance criteria:

- AC-1: *Given the capability is released with a default expiry of 30 days, when a link is created
  without an explicit expiry, then the link expires 30 days after creation.*
- AC-2: *…created with an explicit expiry, then the explicit expiry is kept.*
- AC-3: *Given a link created before the capability was released, when it is resolved, then it keeps
  its original expiry.*

Only answers that resolve an ambiguity are recorded as clarifications. On re-analysis, a finding
whose quoted phrase has a recorded clarification is treated as resolved. Answers that resolve
nothing lead to another round, and more than 3 rounds safe-stop the run (`ClarificationFlowTest`).

## 5. Re-planning and resumption (evidence C-10 .. C-13)

- **Plan version 2** (trigger `CLARIFICATION`) adds `IMPACT_ANALYSIS` and `REGRESSION_TESTING`,
  and rewires `DESIGN` and `VALIDATION` to them. The reason: the clarified change modifies existing
  link-creation behavior.
- **Resumed at requirement analysis:** `REQUIREMENT_INGESTION` ran once, and `REQUIREMENT_ANALYSIS`
  twice. The clarified requirement enters as a new `REQUIREMENT` artifact version.
- **Invalidated and regenerated:** `REQUIREMENT` v1, `NORMALIZED_REQUIREMENT` v1, and
  `CLARIFICATION_REQUEST` v1 were superseded. The re-analysis found no blocking ambiguity, so
  `CLARIFICATION` was skipped with its rationale.
- **Reuse:** re-planning is content-addressed. A re-opened stage whose input fingerprint
  (requirement, inputs, agent version, knowledge version) is unchanged reuses its earlier result
  (`ReplanningServiceTest`).

The run then follows the brownfield path, as in SCN-B:
- The impact analysis (C-10) scans the code from `LinkCreationService`.
- Architecture approval: `bob`.
- Acceptance probes DE-P1..DE-P3 verify AC-1..AC-3, and regression probes R-P1..R-P3 check
  existing behavior.
- Compliance: readiness `READY`, which means no policy failed.
- Release approval: `carol`.
- Release with `defaultExpiryDays = 30`.

## 6. Outcome (evidence C-14 .. C-17)

- **Terminal:** `COMPLETED`, readiness `READY`, capability `default-expiry` released with
  `defaultExpiryDays = 30`. The audit chain is verified.
- **Over HTTP after the release:**
  - A new link without an expiry expires 30 days after creation.
  - A link created before the run still has no expiry (D4).

**Recorded red run.** The test was written before the default-expiry code. Its first run passed
clarification, re-planning, and architecture approval, then safe-stopped at `TESTING`: *"no
acceptance probes exist for capability default-expiry"*. T098 expected the stop at
`IMPLEMENTATION`. But default expiry changes no schema or contract, so the delivery check there
passes; verification is the gate that refuses the undelivered behavior. A second run, after the
code, stopped at compliance: DOC-001 failed because `docs/api/links.md` did not yet document default
expiry. The documentation was added, and the run completed.

## 7. Live walkthrough (the candidate answers personally)

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=demo"
$req = @{ Authorization = "Bearer demo-requester-token"; "Content-Type" = "application/json" }
$run = Invoke-RestMethod -Method Post http://localhost:8080/api/v1/workflows -Headers $req `
       -Body (Get-Content src/main/resources/scenarios/scn-c-ambiguous.json -Raw)
# Read the questions: the CLARIFICATION_REQUEST artifact in
#   GET /api/v1/workflows/$($run.runId)/artifacts
# then answer with your own choices as the approver (demo-approver-token):
#   POST /api/v1/workflows/$($run.runId)/clarifications
#   {"answers":[{"questionId":"Q-1","optionId":"B"}, ...],"rationale":"my decision"}
```

A different duration changes `defaultExpiryDays` and the derived AC-1. Declining a default (Q-1
option D) leaves AC-1 underived. Answers that resolve nothing trigger another round.

## 8. Evidence index

Regenerate with `.\mvnw.cmd -B -ntp test "-Dtest=ScenarioCAmbiguousE2ETest"`. Output goes to
`target/evidence/scn-c/`, with provenance and `simulatedInput: true`.

| Spec evidence item | File |
|---|---|
| original input | `C-01-original-input.json` |
| each detected ambiguity with classification and severity | `C-02-detected-ambiguities.json` |
| affected requirements or components | `C-03-affected-requirements-and-components.json` |
| workflow state before detection | `C-04-state-before-detection.json` |
| transition to the waiting state | `C-05-transition-to-waiting.json` |
| reason implementation cannot continue | `C-06-why-implementation-cannot-continue.json` |
| clarification request | `C-07-clarification-request.json` |
| recorded human decisions (simulated) | `C-08-human-decisions.json` |
| updated requirement | `C-09-updated-requirement.json` |
| downstream impact analysis | `C-10-downstream-impact-analysis.json` |
| replanning event | `C-11-replanning-event.json` |
| invalidated and regenerated artifacts | `C-12-invalidated-and-regenerated-artifacts.json` |
| resumed state | `C-13-resumed-state.json` |
| final validation | `C-14-final-validation.json` |
| audit trail | `C-15-audit-trail.json` |
| terminal outcome | `C-16-terminal-outcome.json` |
| final engineering summary | `C-17-final-summary.md` |
