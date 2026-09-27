# Risk register (T118, constitution XI)

- **Owner.** The human candidate owns every risk. The assistant proposes the levels.
- **Likelihood and impact:** L = low, M = medium, H = high, judged for the prototype as delivered.
- **Status:**
  - **Mitigated**: the control exists and a test or record is cited.
  - **Partial**: part of the mitigation exists.
  - **Open**: the planned mitigation is not in place.
  - **Accepted**: a documented prototype limitation.

## 1. Delivery and scope risks

| # | Risk | L | I | Mitigation | Status | Residual |
|---|---|---|---|---|---|---|
| R-01 | Unimplemented FR MUSTs: FR-ORC-18 (autonomy budget, T074) and FR-RPL-06 (ambiguity found mid-run, T096) | H | M | Deferred under SD-1 and shown as DEFERRED by `TraceabilityMatrixTest`; the candidate decides at G6 | **Open** | M |
| R-02 | Performance targets not met (PVT-19, PVT-20) | H | M | Measured and documented ([performance.md](performance.md)); likely cause is R-18 | **Open** | M |
| R-03 | Known vulnerabilities in dependencies | L | H | OSV-Scanner scan (T128): 3 CRITICAL advisories in Tomcat 11.0.24, fixed by the 11.0.26 override; re-scan clean ([security-scans.md §2](security-scans.md)). The override must be revisited on Spring Boot upgrades | Mitigated | M |
| R-04 | Human gates G1–G7 and all ADRs are unratified; implementation proceeded provisionally | H | H | Delegated provisional progression is recorded in the gate register; nothing is marked approved | **Open** (the candidate's action) | H until ratified |
| R-05 | Overclaiming in the documents (for example, describing unbuilt behavior) | M | H | Claims cite evidence. One overclaim was found and corrected (SCN-A mid-run ambiguity, Phase 10) | Partial | L |
| R-06 | Simulated human input mistaken for the candidate's decisions | M | H | Labeled "simulated human input" in tests, evidence provenance (`simulatedInput: true`), and scenario docs; live walkthroughs documented | Mitigated | L |

## 2. ADR risks

| # | Source | Risk | L | I | Mitigation | Status |
|---|---|---|---|---|---|---|
| R-07 | ADR-001 | Package boundaries erode | L | M | ArchUnit rules fail the build (`ArchitectureTest`) | Mitigated |
| R-08 | ADR-001 | Independent scaling needed later | M | L | The port interface is the extraction seam ([overview §5](../architecture/overview.md)) | Accepted |
| R-09 | ADR-002 | A reviewer has only JDK 17 or 11 | M | M | The enforcer fails fast; the prerequisites are in the README | Mitigated |
| R-10 | ADR-003/006 | Two processes on one H2 file database | L | H | The H2 file lock makes the second start fail fast; single instance documented | Accepted |
| R-11 | ADR-003 | Dialect drift against PostgreSQL | M | M | PostgreSQL mode, standard SQL in migrations (BL-02) | Partial |
| R-12 | ADR-004 | Offensive generated words | L | L | Accepted for a prototype; a production blocklist is possible | Accepted |
| R-13 | ADR-005 | Races between attempt completion and decisions | M | H | Per-run lock, staleness checks, `CONCURRENT_DECISION` (`ConcurrentGateDecisionTest`, `RunCoordinatorTest`) | Mitigated |
| R-14 | ADR-009 | An agent ignores interruption after a timeout and starves the pool (RC-6) | L | M | Timeouts and late-result discard (`StageRetryTimeoutTest`); `sdlc.executor.active` gauge; a global JPA query timeout of 15 s (`jakarta.persistence.query.timeout` in `application.yml`) is configured but **not tested** | Partial |
| R-15 | ADR-010 | Rolling back a flag another run changed | L | H | Rollback only while the flag holds this run's value; otherwise a conflict is recorded (`CompensationCoordinatorTest`, `ReleaseRollbackTest`) | Mitigated |
| R-16 | ADR-011 | Hidden inputs defeat content-addressed reuse | L | H | Agents see only declared inputs (`StageContext`); `InputFingerprinterTest` | Mitigated |
| R-17 | ADR-011 | Re-plan storms | L | M | Clarification rounds bounded (`ClarificationFlowTest`). The autonomy budget named in the ADR is **not implemented** (R-01) | Partial |
| R-18 | ADR-016 | Hot-row contention on popular links (a counter update on every redirect) | M | M | Accepted at prototype scale; the production path is sharded counters or an event stream. Probably contributes to R-02 | Accepted |
| R-19 | ADR-012 | A database administrator rewrites the whole audit chain | L | H | Documented residual risk; external anchoring is backlog BL-06 | Accepted |
| R-20 | ADR-013 | Flaky asynchronous tests | M | M | Awaitility, scripted agents, a separate context per suite; the suite is repeatedly green | Mitigated |
| R-21 | ADR-014 | A reviewer runs without the demo profile and gets 401 | M | L | README and quickstart explain the profiles | Mitigated |
| R-22 | ADR-015 | Demo tokens reused in a real deployment | L | H | Demo principals only in the demo and test profiles; hashes only; a `DEMO PROFILE ACTIVE` startup warning (`StartupLogSecurityTest`, T135); [SECURITY.md](../../SECURITY.md) notice | Mitigated |
| R-23 | ADR-015 | Token brute force; failed authentication attempts are not throttled (analysis finding **L7**) | L | M | Long random tokens are recommended for production; creation is rate-limited. **No throttling of failed authentication** | Open (residual) |
| R-24 | ADR-017 | Reviewers expect LLM agents | M | M | ADR-017 rationale; README states that the agents are deterministic | Mitigated |
| R-25 | ADR-017 | The capability catalog drifts from the code | L | M | Delivery checks at IMPLEMENTATION and acceptance probes against the live system | Mitigated |
| R-26 | ADR-018 | A capability preview leaks to consumer threads | L | H | Thread-scoped override cleared in `finally` (`PreviewIsolationTest`) | Mitigated |
| R-27 | ADR-018 | Concurrent releases by two runs | L | M | Optimistic version; a conflict is recorded | Mitigated |
| R-28 | ADR-019 | SBOM absent when running from an IDE | M | L | LIC-001 fails with clear evidence; run the build online | Mitigated |
| R-29 | ADR-019 | Gaps in the policy rules | M | M | `PolicySetCoverageTest`; review in checklists | Partial |

## 3. Plan threats (plan.md §8) not already covered above

| # | Threat | L | I | Mitigation | Status |
|---|---|---|---|---|---|
| R-30 | T1: short links to phishing or malware | M | M | Authenticated creation, scheme allow-list, rate limits. **No reputation scanning** (EXC-06) | Accepted (residual) |
| R-31 | T2/T3/T5: internal targets, dangerous schemes, self-redirect | L | H | `UrlPolicy` (`UrlSecurityCatalogIT`) | Mitigated |
| R-32 | T4: code enumeration | L | M | 62⁷ codes and not-found throttling (`RateLimitHttpTest`) | Mitigated |
| R-33 | T6/T7: approval bypass, an agent exceeding its mandate | L | H | Roles, separation of duties, permissions, ArchUnit, invariant checker on every e2e run | Mitigated |
| R-34 | T8: audit tampering | L | H | Hash chain and verification (`AuditTamperDetectionTest`) | Mitigated (see R-19) |
| R-35 | T9: secrets in logs, artifacts, or the repository | L | H | Hashed tokens, SEC-002, `TokenNotLoggedTest`, `RepositorySecretScanTest` | Mitigated |
| R-36 | T10: resource exhaustion | M | M | Bounded pool, rate limits, request limits. **No autonomy budget** (R-01) | Partial |
| R-37 | T11: vulnerable dependencies | M | H | SBOM, LIC-001, and the OSV scan with remediation (R-03). No scanning in CI yet (BL-03) | Partial |
| R-38 | T12: fault injection or preview in production | L | H | Disabled by default; in-process only (`SecurityMatrixTest`) | Mitigated |
| R-39 | T13: SQL injection | L | H | Parameterized queries only | Mitigated (review) |

## 4. Summary of residual risk

The **open** items (R-01, R-02, R-04, R-23) and the partial R-37 are the input to the release-readiness
proposal ([release-readiness.md](release-readiness.md)). They keep the proposal at **NOT READY**
until the candidate either accepts each one as a limitation at G6 or resolves it.
