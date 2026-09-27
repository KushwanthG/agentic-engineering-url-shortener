# Quickstart validation from a clean clone (T120, SC-001)

## Setup

| Item | Value |
|---|---|
| Date | 2026-09-27; the clone was made at 08:13:56 UTC |
| Source | `git clone` of the local repository at commit `90c2bfe`, into an empty scratch directory (no `./data/`, no `target/`) |
| Machine | Windows 11, JDK 21.0.12.1 (Oracle), Git Bash `curl` |
| Performed by | the AI assistant. Every gate decision below is **simulated human input** by the assistant (each rationale is labeled), **not the candidate's decision** |

## Results by quickstart section

### §1 Prerequisites

`mvnw -v` reports Apache Maven 3.9.16 and Java 21.0.12.1. **Pass.**

### §2 Build and full suite

`mvnw clean verify` from the clean clone:
- **528 tests, 0 failures, 0 errors, BUILD SUCCESS, 202 s** (including the first download of the
  Maven distribution into the wrapper cache);
- `target/site/jacoco/index.html` and `target/classes/META-INF/sbom/application.cdx.json` were
  produced.

**Pass.**

### §3 Run with the demo profile

`mvnw spring-boot:run -Dspring-boot.run.profiles=demo`, then `GET /actuator/health` returns
`{"groups":["liveness","readiness"],"status":"UP"}`. **Pass**, with a wording note (discrepancy D-1).

### §4 URL shortener

| Step | Result | Verdict |
|---|---|---|
| Create | `201` with code `FafQu4j`, `status: ACTIVE` | Pass |
| Follow | `302`, the expected `Location`, `Cache-Control: no-store` | Pass |
| Stats | `totalClicks: 1` | Pass |
| `javascript:` URL | `URL_SCHEME_NOT_ALLOWED` | Pass |
| Alias before release | `422 CAPABILITY_NOT_AVAILABLE` | Pass |

### §5 SCN-A driven by hand (run `3eedd71f-…`)

| Step | Result | Verdict |
|---|---|---|
| Submit, then inspect | `CLARIFICATION` `SKIPPED` with the recorded reason ("All five quality checks passed…"); run `AWAITING_HUMAN`; the pending action is `ARCHITECTURE_APPROVAL` for role `APPROVER` | Pass |
| Requester tries to approve | `403` | Pass |
| Approver approves (simulated) | `200` | Pass |
| Timeline | `TESTING` and `SECURITY_VERIFICATION` started together and overlapped; `VALIDATION` started after both finished | Pass, with discrepancy D-2 |
| Release owner approves (simulated) | `200`; run `COMPLETED`, readiness `READY` | Pass |
| After the release | `custom-alias` released; the alias request now returns `201` | Pass |
| Evidence | `summary` `200 text/markdown`; `audit/verification` `valid: true`, 110 events; 14 policy evaluations (12 `PASS`, 2 `NOT_APPLICABLE`) | Pass |

### §6 Drills

**RDR-01** (run `d8141313-…`, `TRANSIENT_ERROR` ×2 on `TESTING`):
- the TESTING attempts were `FAILED_TRANSIENT`, `FAILED_TRANSIENT`, `SUCCEEDED`, and the run
  `COMPLETED`;
- the reliability report was labeled `DEMONSTRATION DATA - not production statistics`, with
  `recoveredEvents: 1`, `byMechanism: {RETRY: 1}`, and `mttrMillis: 647.0` over 2 runs.

**Pass.**

**RDR-07:** pause, resume, and safe-stop as the release owner each returned `200`, and the run
ended `SAFE_STOPPED`. **Pass.**

**Not run by hand:** RDR-02 to RDR-06. They are covered by `ReliabilityDrillsE2ETest` and
`RestartResumeTest`, which passed in §2.

## Discrepancies (the quickstart is a SpecKit artifact; corrections are proposed, not made silently)

- **D-1.** §3 says the health check returns `{"status":"UP"}`. The actual response also lists the
  probe groups. *Proposed wording:* "the response contains `"status":"UP"`".
- **D-2.** §5 step 4 says `TESTING`, `SECURITY_VERIFICATION`, and `DOCUMENTATION` overlap. In the
  plan, `DOCUMENTATION` depends on `ARCHITECTURE_APPROVAL`, so it runs alongside `IMPLEMENTATION`;
  in this run it had finished before `TESTING` started. *Proposed wording:* "`DOCUMENTATION` runs
  in parallel with `IMPLEMENTATION`, and `TESTING` and `SECURITY_VERIFICATION` run in parallel;
  `VALIDATION` starts after all of them."
- **D-3.** §7 names committed evidence snapshots in `docs/scenarios/` and matrices in
  `docs/traceability/`. Neither exists, because T109 and T119 are deferred under SD-1. The
  evidence is regenerated under `target/`.

## Housekeeping

- A first scripted attempt at RDR-01 sent malformed authorization headers (an error in the
  validation script, which returned `401`). It was corrected, and the same run was then completed.
- The demo application was stopped after validation, and port 8080 was freed.
