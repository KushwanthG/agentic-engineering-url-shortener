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

## 2. Dependency vulnerability scan (T128, NFR-SEC-05): NOT executed — release limitation

**Planned command.** Run OSV-Scanner, at a pinned release, against the CycloneDX SBOM that the build
produces:

```powershell
.\mvnw.cmd -B -ntp verify                      # online: writes target/classes/META-INF/sbom/application.cdx.json
osv-scanner scan --sbom target/classes/META-INF/sbom/application.cdx.json
```

**Status: not run.** OSV-Scanner is not installed in this environment. Installing a third-party
binary was left for the candidate's approval, so there are **no findings to report, and the absence
of findings is not evidence of absence**.

**SBOM available.** The SBOM is CycloneDX 1.6 with 104 components. The main runtime components are:
- Spring Boot 4.1.1 (web MVC, embedded Tomcat, actuator, data JPA, security);
- Jackson 3.1.5;
- Logback 1.5.38;
- SnakeYAML 2.6;
- H2.

**Disposition.** The dependency scan is a release limitation. Under constitution V it needs the
candidate's exception at G6, or the scan must be run and its findings dispositioned first. Any
HIGH or CRITICAL finding blocks the readiness proposal (T122) until it is dispositioned.
