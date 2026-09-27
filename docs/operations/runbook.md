# Operations runbook

Procedures for running the application and operating workflow runs. Background:
[reliability.md](../architecture/reliability.md) and [governance.md](../architecture/governance.md).
The demo tokens below are **non-production** labeled demo principals; their hashes are in
`application-demo.yml`.

## Start and stop

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21.0.12.1"
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=demo"   # http://localhost:8080
```

- **Data:** the H2 database file lives under `./data/`. Runs survive restarts.
- **Health:** `GET /actuator/health` is public. Metrics are at `/actuator/metrics` and
  `/actuator/prometheus`, with the auditor token.
- **Stop:** press Ctrl+C. In-flight attempts are left open and are resumed at the next start.
- **Reset all state:** stop the application, then delete `./data/`. This removes every run, link,
  and audit event.

## Operate a run (release owner)

```powershell
$h = @{ Authorization = "Bearer demo-release-token"; "Content-Type" = "application/json" }
$body = '{"reason":"maintenance window"}'
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/v1/workflows/<runId>/pause     -Headers $h -Body $body
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/v1/workflows/<runId>/resume    -Headers $h -Body $body
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/v1/workflows/<runId>/safe-stop -Headers $h -Body $body
```

| Action | Allowed from | Effect |
|---|---|---|
| pause | `RUNNING`, `AWAITING_HUMAN` | no new stage starts; in-flight attempts finish |
| resume | `PAUSED` | scheduling continues |
| safe-stop | any non-terminal status | the safe-stop procedure; the run ends `SAFE_STOPPED` with the operator's reason |

Each action is recorded as an `OPERATOR_ACTION` decision. A terminal run answers `409 RUN_TERMINAL`.

## Inspect a run

```powershell
$a = @{ Authorization = "Bearer demo-auditor-token" }
Invoke-RestMethod -Uri http://localhost:8080/api/v1/workflows/<runId>          -Headers $a   # stages, pending actions
Invoke-RestMethod -Uri http://localhost:8080/api/v1/workflows/<runId>/timeline -Headers $a   # attempts, retries, faults
Invoke-RestMethod -Uri http://localhost:8080/api/v1/workflows/<runId>/decisions -Headers $a  # gates, fallbacks, safe-stop
```

Things to look for:
- A run with `manualInterventionRequired: true` stopped after a compensation that did not succeed.
- Look up its `COMPENSATION_ACTION` audit events and the `SAFE_STOP` decision payload, then complete
  the action by hand. For example, withdraw a capability that was already released before the run.

## Recovery after a restart

No action is needed. At start, `RecoveryService` handles every unfinished run:
- Interrupted attempts become `INTERRUPTED` and are retried (failure cause `PROCESS_INTERRUPTION`,
  mechanism `RESUME`).
- Due retries run.
- Waiting gates keep their deadlines.
- Paused runs stay paused.

**Manual process-kill variant of drill RDR-05.** The automated test stops the context gracefully.
To check a hard kill:

1. Start the demo profile. Submit the SCN-A requirement with
   `"simulation":{"faults":[{"stage":"DESIGN","type":"DELAY","delayMillis":60000}]}`.
2. When the timeline shows a `DESIGN` attempt without `finishedAt`, kill the Java process:
   `Stop-Process -Id <pid> -Force`.
3. Start the application again. Within 30 s (PVT-22), the timeline shows attempt 1 `INTERRUPTED`
   and attempt 2 running or finished. `REQUIREMENT_INGESTION` still has exactly one attempt.

## Drills

Run the automated drills with
`.\mvnw.cmd -B -ntp test "-Dtest=ReliabilityDrillsE2ETest,RestartResumeTest"`.
The evidence index is [drills.md](../scenarios/drills.md).

Faults are accepted only when fault injection is enabled (demo and test profiles). The default
profile refuses them with `400 FAULT_INJECTION_DISABLED`.
