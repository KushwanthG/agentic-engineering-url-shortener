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

## 2. Dependency vulnerability scan (T128, NFR-SEC-05): executed; all findings fixed by upgrade

**Tool.** OSV-Scanner **v2.6.0** (commit `e840a6e`), downloaded from the official GitHub release.
Its SHA256 matched the published `osv-scanner_SHA256SUMS`.

**Command** (run after an online `mvnw clean verify`, which writes the SBOM):

```powershell
osv-scanner scan source -L target/classes/META-INF/sbom/application.cdx.json --format table
```

### First scan (2026-09-27, commit `2ba5823`, CycloneDX 1.6 SBOM, 104 components)

The scan found **3 known vulnerabilities, all CRITICAL, all in one package**:

| Advisory | CVSS | Component | Issue | Reachability | Disposition |
|---|---|---|---|---|---|
| GHSA-9xv2-5v5q-p794 / CVE-2026-65905 | 9.8 | `tomcat-embed-core` 11.0.24 (managed by Spring Boot 4.1.1) | Replay attack in Tomcat's DIGEST authenticator | Not reachable by analysis: authentication is the Spring Security bearer-token filter, with no Tomcat authenticator | **Upgrade** |
| GHSA-gcx9-497g-6cp6 / CVE-2026-65182 | 9.1 | same | Bypass of servlet `<security-constraint>` ordering | Not reachable by analysis: there are no servlet security constraints | **Upgrade** |
| GHSA-h3x4-894j-xpx5 / CVE-2026-68525 | 9.1 | same | Bypass in Tomcat FORM authentication | Not reachable by analysis: FORM login is disabled | **Upgrade** |

- **Fix available.** The advisories name Tomcat **11.0.25** as the fix. The scanner itself printed
  `--` for the fixed version.
- **Why upgrade anyway.** Reachability rests on reading the configuration, not on a test. So even
  though none of the three looked reachable, all three were fixed by upgrading instead of being
  accepted.

### Remediation

- **Change.** `pom.xml` sets `<tomcat.version>11.0.26</tomcat.version>`, the latest patch of the
  11.0 line on Maven Central. This overrides the Tomcat version managed by Spring Boot 4.1.1.
- **Build check.** `mvnw -B -ntp clean verify` passed: **530 tests, 0 failures**. The SBOM lists
  `tomcat-embed-core`, `tomcat-embed-el`, and `tomcat-embed-websocket` at 11.0.26.
- **Maintenance note.** The override must be removed or raised when Spring Boot is upgraded to a
  release that manages Tomcat ≥ 11.0.25.

### Re-scan after the upgrade (2026-09-27)

The same command over the new SBOM (104 packages) printed **"No issues found"**. There are no
remaining findings, so no exception is needed at G6.

**Limits.**
- The scan reflects the OSV database on 2026-09-27. New advisories may appear later, so re-run it
  before any release.
- It covers the dependencies listed in the SBOM (104 components), not the Maven build plugins.
