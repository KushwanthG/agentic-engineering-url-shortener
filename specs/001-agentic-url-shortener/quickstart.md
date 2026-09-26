# Quickstart and Validation Guide

**Feature**: `001-agentic-url-shortener` | **Date**: 2026-09-26 | **Phase**: 1 (plan)

This guide describes how a reviewer builds, runs, and validates the prototype end-to-end. Commands
are written for Windows PowerShell with bash equivalents. API shapes are defined in
[`contracts/openapi.yaml`](contracts/openapi.yaml); this guide does not duplicate them.

> Status: written at planning time. `/speckit-converge` re-validates every command against the
> implementation and records the actual results in `docs/assessment/`.

## 1. Prerequisites

| Tool | Version | Notes |
|------|---------|-------|
| JDK | 21 | `JAVA_HOME` must point to a JDK 21. The build's enforcer rule fails fast on older JDKs. |
| Maven | none needed | the Maven Wrapper (`mvnw` / `mvnw.cmd`) downloads the pinned Maven on first use |
| curl | any | for the manual walkthrough (PowerShell users can use `Invoke-RestMethod`) |

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21.0.12.1"   # adjust to your JDK 21 location
.\mvnw.cmd -v                                              # must report Java version 21
```

## 2. Build and run the full test suite

```powershell
.\mvnw.cmd clean verify
```

```bash
./mvnw clean verify
```

**Expected**: `BUILD SUCCESS`. The run includes unit, contract, integration, orchestration,
security, architecture, and end-to-end scenario tests. The JaCoCo report is written to
`target/site/jacoco/index.html`, and scenario evidence to `target/evidence/`.

Run only the scenario and drill end-to-end tests:

```powershell
.\mvnw.cmd verify "-Dtest=*E2ETest"
```

## 3. Run the application (demo profile)

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=demo"
```

The application always stores its state in an **H2 file database** under `./data/` (the runtime
database; runs survive restarts). The `demo` profile adds the labeled **non-production demo
credentials** and enables fault injection. The default profile keeps authenticated APIs closed and
fault injection disabled (secure defaults). Delete `./data/` to start from an empty database.

| Principal | Role | Demo token |
|-----------|------|------------|
| `demo-consumer` | `API_CONSUMER` | `demo-consumer-token` |
| `alice` | `REQUESTER` | `demo-requester-token` |
| `bob` | `APPROVER` | `demo-approver-token` |
| `carol` | `RELEASE_OWNER` | `demo-release-token` |
| `rita` | `AUDITOR` | `demo-auditor-token` |

Health check: `curl http://localhost:8080/actuator/health` → `{"status":"UP"}`.

## 4. Exercise the URL shortener (US1)

```bash
# create
curl -s -X POST http://localhost:8080/api/v1/links \
  -H "Authorization: Bearer demo-consumer-token" -H "Content-Type: application/json" \
  -d '{"url":"https://www.example.com/articles/2026?ref=quickstart"}'
# → 201 with "code", "shortUrl", "status":"ACTIVE"

# follow (public, no token)
curl -si http://localhost:8080/<code>
# → HTTP/1.1 302, Location: https://www.example.com/articles/2026?ref=quickstart, Cache-Control: no-store

# analytics
curl -s http://localhost:8080/api/v1/links/<code>/stats -H "Authorization: Bearer demo-consumer-token"
# → "totalClicks":1

# negative: unsupported scheme
curl -s -X POST http://localhost:8080/api/v1/links -H "Authorization: Bearer demo-consumer-token" \
  -H "Content-Type: application/json" -d '{"url":"javascript:alert(1)"}'
# → 400, "code":"URL_SCHEME_NOT_ALLOWED"

# negative: capability not yet released
curl -s -X POST http://localhost:8080/api/v1/links -H "Authorization: Bearer demo-consumer-token" \
  -H "Content-Type: application/json" -d '{"url":"https://example.com","alias":"spring-sale"}'
# → 422, "code":"CAPABILITY_NOT_AVAILABLE" (until SCN-A releases custom-alias)
```

## 5. Drive a governed workflow run by hand (SCN-A)

1. Submit the fixed SCN-A requirement (the file lives in `src/main/resources/scenarios/`):

   ```bash
   curl -s -X POST http://localhost:8080/api/v1/workflows \
     -H "Authorization: Bearer demo-requester-token" -H "Content-Type: application/json" \
     --data @src/main/resources/scenarios/scn-a-greenfield.json
   # → 201 with "runId"
   ```

2. Inspect the run: `GET /api/v1/workflows/{runId}` with any control-plane token. Expect
   `CLARIFICATION` as `SKIPPED` with a reason, and the run waiting at `ARCHITECTURE_APPROVAL`
   (`pendingActions[0].requiredRole = APPROVER`).
3. **Make the decision yourself** as the approver (you are the human in the loop):

   ```bash
   curl -s -X POST http://localhost:8080/api/v1/workflows/<runId>/gates/ARCHITECTURE_APPROVAL/decision \
     -H "Authorization: Bearer demo-approver-token" -H "Content-Type: application/json" \
     -d '{"decision":"APPROVE","rationale":"Reviewed design: additive API change, reserved words covered."}'
   ```

   Negative check: the same call with `demo-requester-token` returns `403` (role), and a requester
   who also held the approver role would get `403 SEPARATION_OF_DUTIES`.
4. Watch the parallel branches: `GET /api/v1/workflows/<runId>/timeline` shows `TESTING`,
   `SECURITY_VERIFICATION`, and `DOCUMENTATION` with overlapping intervals and `VALIDATION`
   starting after all of them finish.
5. Approve the release as `carol` (`demo-release-token`) at `RELEASE_APPROVAL`.
6. Verify the outcome: run `status = COMPLETED`, `readiness = READY`; `GET /api/v1/capabilities`
   shows `custom-alias` released; the alias request from step 4 of §4 now returns `201`.
7. Evidence: `GET .../summary` (Markdown), `GET .../audit`, `GET .../audit/verification`
   (`"valid": true`), and `GET .../policy-evaluations`.

SCN-B (`scn-b-brownfield.json`) and SCN-C (`scn-c-ambiguous.json`) follow the same pattern. For
SCN-C, answer the clarification with your own decisions through
`POST /api/v1/workflows/<runId>/clarifications` (see the questions in `pendingActions` and in the
`CLARIFICATION_REQUEST` artifact).

## 6. Reliability drills (simulated faults, demo profile)

Add a `simulation` block to a submission (schema `SimulationOptions`), for example:

```json
"simulation": { "faults": [ { "stage": "TESTING", "type": "TRANSIENT_ERROR", "occurrences": 2 } ] }
```

| Drill | Simulation | Expected observable result |
|-------|-----------|----------------------------|
| RDR-01 retry | `TRANSIENT_ERROR` ×2 on `TESTING` | `TESTING` attempts = 3; run completes; reliability report has a recovered event |
| RDR-02 fallback | `PERMANENT_ERROR` on `IMPACT_ANALYSIS` | fallback `CATALOG_ONLY` analysis, `degraded = true`, readiness `READY_WITH_ACCEPTED_LIMITATIONS` |
| RDR-03 compensation | `VERIFICATION_FAILURE` on `RELEASE` | capability withdrawn, run `SAFE_STOPPED` |
| RDR-04 gate deadline | `gateDeadlineSeconds: 5`, then do not decide | run `SAFE_STOPPED` after the deadline |
| RDR-05 resume | `DELAY` 20000 ms on `DESIGN`; stop the app mid-run; start it again | run resumes and completes; `REQUIREMENT_INGESTION` attempts stay 1 |
| RDR-06 policy exception | `POLICY_FAILURE` with `policyId: DOC-001` | compliance waits for an exception; approve → `READY_WITH_ACCEPTED_LIMITATIONS`; reject → `SAFE_STOPPED` |
| RDR-07 operator controls | none | `POST .../pause`, `.../resume`, `.../safe-stop` as `carol` |

Reliability metrics: `GET /api/v1/reliability/report` with `demo-auditor-token`. MTTR is computed
over recovered failure events only, unrecovered failures are listed separately, and the report is
labeled as demonstration data.

## 7. Where the evidence lives

| Evidence | Location |
|----------|----------|
| Scenario and drill evidence exported by the end-to-end tests | `target/evidence/` (regenerated), committed snapshot in `docs/scenarios/` |
| Traceability matrix | `docs/traceability/` |
| Architecture and ADRs | `docs/architecture/`, `docs/adr/` |
| Human gates | `docs/governance/human-gate-register.md` |
| Final engineering summary and reviewer guide | `docs/assessment/` |
