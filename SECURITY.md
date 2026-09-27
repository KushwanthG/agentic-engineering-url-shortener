# Security

This is an **assessment prototype**, not a production service. This page summarizes the threat
model, the controls, and how each is verified, plus the residual risks. The full model is in
[plan.md §8](specs/001-agentic-url-shortener/plan.md) and
[ADR-015](docs/adr/ADR-015-authentication-and-authorization.md).

## Demo credentials notice

The `demo` and `test` profiles define **labeled, non-production** principals, such as
`demo-requester-token`. The configuration stores **only the SHA-256 hash** of each token, and
tokens are compared in constant time. These tokens are published in the documentation on purpose:
never reuse them anywhere.

The default profile has no principals, so the authenticated APIs are closed. Fault injection is
disabled by default. Actuator exposes only `health` and `info` without authentication; the other
actuator endpoints require the auditor role.

## Threat model summary (STRIDE)

| Threat | Control | Verified by |
|---|---|---|
| Open redirect to internal targets (localhost, private, link-local, metadata IPs, obfuscated IP notations) | `UrlPolicy` rejects them in every notation | `UrlSecurityCatalogIT` (malicious-URL catalog), `UrlPolicyTest` |
| `javascript:`, `data:`, `file:` injection | scheme allow-list (`http`, `https`) | catalog, `UrlPolicyTest` |
| Short-code enumeration | 62⁷ random codes; per-client throttling of not-found results | `ShortCodeGeneratorTest`, `RateLimitHttpTest` |
| Abuse of link creation | authenticated creation, per-consumer rate limit | `RateLimitHttpTest` |
| Approval bypass or self-approval | role checks, separation of duties, transition guards, ArchUnit rules | `SeparationOfDutiesTest`, `GovernanceSecurityMatrixTest`, `GovernanceInvariants` on every e2e run |
| An agent exceeding its mandate | declared permissions and a permission-filtered port; agents cannot reach governance or security code | `AgentPermissionTest`, `ArchitectureTest` |
| Audit tampering | SHA-256 hash chain; verification endpoint | `AuditTamperDetectionTest`, `EvidenceControllerTest` |
| Secrets in logs, artifacts, or the repository | hashed tokens; the `Authorization` header is never logged; no default password user (so no generated password is logged); policy SEC-002 scans artifacts; repository secret scan | `TokenNotLoggedTest`, `StartupLogSecurityTest`, `PolicyRulesTest`, `RepositorySecretScanTest` |
| SQL injection | parameterized JPA and JDBC queries only | code review; hostile-input tests |
| Fault injection or preview abused in production | disabled by default; preview runs in-process only (no HTTP path) | `SecurityMatrixTest`, `PreviewIsolationTest` |

## Scan results

See [security-scans.md](docs/assessment/security-scans.md).

- **Repository secret scan:** passing, with 0 unexplained findings.
- **Dependency vulnerability scan:** **not run** yet (T128). This is a release limitation.

## Residual risks

These are tracked in the [risk register](docs/assessment/risk-register.md).

- **No reputation or malware scanning of target URLs** (EXC-06). A syntactically safe URL can still
  lead to a phishing site.
- **Failed authentication attempts are not throttled** (analysis finding L7).
- **No autonomy budget per run** (FR-ORC-18, T074 deferred). Runaway runs are bounded only by the
  per-stage retry and timeout limits and by the bounded agent pool.
- **Bearer tokens stand in for an identity provider** (ASM-02). There is no token expiry or
  rotation.
- **Dependencies are not yet scanned for known vulnerabilities** (T128).

## Reporting a vulnerability

This repository is an interview assessment. Report a security concern privately to the repository
owner. Do not open a public issue.
