# Reliability drills RDR-01 .. RDR-07

The drills prove that the reliability controls work end to end ([reliability.md](../architecture/reliability.md)).

> **Simulated input.** Every drill uses faults injected on purpose (demo and test profiles only).
> The gate and exception decisions by `bob` and `carol`, and the operator actions by `carol`, are
> **simulated human input** given by automated tests acting as labeled demo principals. They are
> not decisions of the candidate.

## Regenerating the evidence

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21.0.12.1"
.\mvnw.cmd -B -ntp test "-Dtest=ReliabilityDrillsE2ETest,RestartResumeTest"
```

Output goes to `target/evidence/drills/`. Each file starts with provenance: git commit, command,
timestamp, JDK, profile, and `simulatedInput: true`.

## Drills

| Drill | Input (SCN-A requirement GF-001 plus) | Expected | Recorded outcome (latest run) | Evidence |
|---|---|---|---|---|
| RDR-01 retry | `TRANSIENT_ERROR` ×2 on `TESTING` | bounded retry recovers; run completes | `TESTING` attempts `FAILED_TRANSIENT`, `FAILED_TRANSIENT`, `SUCCEEDED`; run `COMPLETED`, `READY` | `RDR-01-retry.json` |
| RDR-02 fallback | `PERMANENT_ERROR` on `DOCUMENTATION` | template fallback, degraded, accepted limitation | `DOCUMENTATION` primary failed, fallback succeeded; `FALLBACK_USED` decision; run `COMPLETED`, `READY_WITH_ACCEPTED_LIMITATIONS` | `RDR-02-fallback.json` |
| RDR-03 rollback | `VERIFICATION_FAILURE` on `RELEASE` | release rolled back; safe-stop | "post-release verification failed …; release state of custom-alias rolled back to released=false"; `SAFE_STOPPED`; alias creation then refused with `CAPABILITY_NOT_AVAILABLE` | `RDR-03-rollback.json` |
| RDR-04 gate deadline | gate deadline 2 s, no decision | escalation; safe-stop; never approved | "decision deadline … passed for ARCHITECTURE_APPROVAL … escalated to APPROVER, not approved by default"; late decision `409` | `RDR-04-gate-deadline.json` |
| RDR-05 restart | `DELAY` 20 s on `DESIGN`, application context closed mid-attempt | interrupted attempt resumed after restart; completed stages not repeated | `DESIGN` attempts `INTERRUPTED`, `SUCCEEDED`; resumed about 1.9 s after restart (PVT-22 ≤ 30 s); failure event `PROCESS_INTERRUPTION`, mechanism `RESUME`, `RECOVERED`; run `COMPLETED` | `RDR-05-restart-resume.json` |
| RDR-06 policy exception | `POLICY_FAILURE` of DOC-001 | blocked until an approved exception with a compensating control; a rejected exception stops the run | run 1: DOC-001 `FAIL` then `EXCEPTION_REQUESTED`, `COMPLETED`, `READY_WITH_ACCEPTED_LIMITATIONS`; run 2: exception rejected, `SAFE_STOPPED` | `RDR-06-policy-exception.json` |
| RDR-07 operator controls | `DELAY` 1.5 s on `DESIGN` | pause lets the in-flight stage finish; nothing new starts; resume; safe-stop | `PAUSED` while `DESIGN` finished and `IMPLEMENTATION` did not start; resumed; "operator safe-stop by carol: change freeze (drill)" | `RDR-07-operator-controls.json` |

RDR-05 interrupts the run by stopping the application context cleanly (evidence label: *graceful
context stop*). The manual process-kill variant is in the
[runbook](../operations/runbook.md#recovery-after-a-restart).

The component-level tests behind each drill are:

| Drill(s) | Tests |
|---|---|
| RDR-01 | `StageRetryTimeoutTest` |
| RDR-02 | `FallbackTest` |
| RDR-03 | `CompensationCoordinatorTest`, `ReleaseRollbackTest` |
| RDR-04 | `GateDeadlineTest`, `SafeStopServiceTest` |
| RDR-06 | `PolicyExceptionFlowTest` |
| RDR-07 | `OperationsControllerTest` |
| RDR-01..07 (fault injection) | `FaultInjectionTest` |
