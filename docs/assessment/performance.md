# Performance measurement (T113, NFR-PRF-01)

> **DEMONSTRATION MEASUREMENT on one local development machine; not a production benchmark.**

## Result in one line

**The PVT-19 and PVT-20 targets are not reliably met in this measurement.** Redirect p95 missed its
50 ms target in both recorded runs. Creation p95 met its 150 ms target in one run and missed it in
the other. NFR-PRF-01 is therefore **not demonstrated**, and it is listed as an open finding for the
release-readiness decision (G6).

## How to regenerate

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21.0.12.1"
.\mvnw.cmd -B -ntp test "-Dtest=PerformanceMeasurementTest"
# -> target/evidence/performance/performance-measurement.json (provenance: commit, JDK, time)
```

## Load profile

| Item | Value |
|---|---|
| Clients | 20 concurrent threads (`java.net.http.HttpClient`, HTTP/1.1 over loopback) |
| Creation | 20 × 20 = 400 `POST /api/v1/links` (distinct URLs, one API consumer) |
| Redirect | 20 × 50 = 1,000 `GET /{code}` over 50 pre-created links; each redirect records a click |
| Warm-up | 50 creations and 50 redirects before measuring |
| Server | the application in the `test` profile: embedded Tomcat, in-memory H2, rate limits raised |
| Machine | Windows 11 (10.0), 8 logical processors, JDK 21.0.12.1, max heap 4,034 MB; clients and server share the machine |
| Asserted | only a regression bound: p95 < 10× the target (500 ms redirect, 1,500 ms creation), so the test is not flaky |

## Recorded runs (2026-09-27, commit `a94b800` plus the Phase 10 working tree)

| Run (UTC) | Redirect p50 / p95 / p99 / max (ms) | Creation p50 / p95 / p99 / max (ms) | PVT-19 (≤ 50 ms) | PVT-20 (≤ 150 ms) |
|---|---|---|---|---|
| 07:55:55 | 37 / **119** / 186 / 367 | 40 / **142** / 176 / 313 | not met | met |
| 07:56:58 | 46 / **220** / 351 / 552 | 53 / **295** / 544 / 886 | not met | not met |

The medians are moderate, but the tails are long and change a lot between runs.

## Likely causes (not yet profiled)

These are hypotheses and have not been verified by profiling:
- **Shared machine.** The 20 client threads, the Tomcat workers, and the JIT all share 8 logical
  processors, and background load on the desktop was not controlled.
- **A write on every redirect.** Every redirect writes a click event and increments the link's
  counter in one transaction. With 1,000 redirects over only 50 links, those updates contend for the
  same rows.
- **Tuning.** The H2 in-memory database is not tuned for concurrent writes, and the Tomcat thread
  pool uses its default size.

## What would be done next

- Profile a run and measure with a separate client machine.
- Record clicks asynchronously for links without a click limit. This is an ADR-level change, because
  FR-RED-03 couples counting to the redirect.
- Re-run the measurement on a quiet machine.

None of this was done within the timebox.
