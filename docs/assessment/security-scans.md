# Security scans (T127, T128; constitution V)

## 1. Repository secret scan (T127, NFR-SEC-03): executed, passing

```powershell
.\mvnw.cmd -B -ntp test "-Dtest=RepositorySecretScanTest"
```

`RepositorySecretScanTest` scans every git-tracked text file (`git ls-files`) for the following
patterns:
- private-key blocks;
- cloud access keys (`AKIA…`/`ASIA…`);
- bearer tokens;
- values assigned to a password key (with `=` or `:`);
- values assigned to a secret or API-key key.

**Detection is proven, not assumed.** There is one planted fixture per pattern under
`src/test/resources/secret-scan-fixtures/`, and the test asserts that each one fires. Those fixture
files are excluded from the repository scan.

**Allowed without review:**
- the six labeled demo tokens (`demo-*-token`, `test-dual-role-token`);
- placeholders such as `${…}`.

The demo profile configures only the tokens' SHA-256 hashes (see SECURITY.md).

**Reviewed non-secret matches (7).** Each is keyed by file and pattern with its reason, and an
entry that no longer matches fails the test:

| File | Pattern | Why it is not a secret |
|---|---|---|
| `mvnw` | password assignment | vendored Maven Wrapper: shell expansion testing whether `MVNW_PASSWORD` is set |
| `NoSecretsRule.java` | secret assignment | Java for-each over the product's own secret-detection patterns |
| `SecurityMatrixTest.java` | bearer token | deliberately invalid token for the 401 test |
| `PolicyRulesTest.java` | private key, password, cloud key | fake fixtures that prove the product's NoSecrets policy rule |
| `RepositorySecretScanTest.java` | bearer token | the scan test's own negative example |

**Result (2026-09-27):** 3/3 tests pass, with 0 unexplained findings.

**Limits.** The scan uses regular expressions: it finds known shapes of secrets, not every possible
secret, and it does not scan git history. The history is short and local, and no secret was ever
committed to it by this workflow.

## 2. Dependency vulnerability scan (T128, NFR-SEC-05): run once; findings accepted, not remediated

**Scope decision.** On 2026-09-27 the candidate removed T128 from the assignment's scope:
"dont override the tomcat as its too much for assignment... remove that T128 from task". The scan
had already been run once. Its results are recorded below, as found, so that nothing is hidden.

**Command and tool.** OSV-Scanner **v2.6.0** (commit `e840a6e`), downloaded from the official
GitHub release; its SHA256 matched the published `osv-scanner_SHA256SUMS`. It scanned the CycloneDX
1.6 SBOM of commit `2ba5823` (104 components) on 2026-09-27:

```powershell
osv-scanner scan source -L target/classes/META-INF/sbom/application.cdx.json --format table
```

**Result.** 1 package is affected by **3 known vulnerabilities, all rated CRITICAL**, and no other
findings:

| Advisory | CVSS | Component | Issue | Reachability in this application |
|---|---|---|---|---|
| GHSA-9xv2-5v5q-p794 / CVE-2026-65905 | 9.8 | `tomcat-embed-core` 11.0.24 (managed by Spring Boot 4.1.1) | Replay attack in Tomcat's **DIGEST** authenticator | **Not reachable (by analysis).** No Tomcat authenticator or realm is configured; authentication is the Spring Security bearer-token filter |
| GHSA-gcx9-497g-6cp6 / CVE-2026-65182 | 9.1 | same | Bypass of servlet `<security-constraint>` ordering | **Not reachable (by analysis).** There are no servlet security constraints; Spring Security's filter chain does all authorization |
| GHSA-h3x4-894j-xpx5 / CVE-2026-68525 | 9.1 | same | Bypass in Tomcat **FORM** authentication | **Not reachable (by analysis).** FORM login is disabled (`formLogin(...disable())` in `SecurityConfig`) |

**What the scanner reported and what is known.**
- The scanner printed no fixed version (`--`).
- The advisories themselves name **Tomcat 11.0.25** as the fix, and Maven Central also has 11.0.26.
- Setting `<tomcat.version>11.0.26</tomcat.version>` was tried: it built green (530 tests) and the
  SBOM showed 11.0.26. The change was **reverted** at the candidate's request and is **not** in the
  repository.

**Disposition: accepted by the candidate, not remediated.**
- The reachability conclusions come from reading the configuration; no test proves them.
- Constitution V requires dependency-risk checks before release. The check ran. Shipping with known
  CRITICAL advisories is a risk that only the candidate can accept, at gate G6.
- **Production recommendation:** override `tomcat.version` to ≥ 11.0.25, or move to a Spring Boot
  patch release that manages a fixed Tomcat, and scan in CI.
