package com.agentic.urlshortener.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * T127 (NFR-SEC-03, constitution V): a secret scan of every git-tracked text file. It looks for
 * private-key blocks, cloud access keys, bearer tokens, and {@code password=} / {@code secret=}
 * values. The only allowed matches are the labeled demo tokens: their SHA-256 hashes are what the
 * demo profile configures, and the plain tokens appear in docs and tests as documented demo
 * credentials. Detection is proven with one planted fixture per pattern under
 * {@code src/test/resources/secret-scan-fixtures/}, which the repository scan excludes.
 */
@Tag("NFR-SEC-03")
class RepositorySecretScanTest {

    private static final String FIXTURES = "src/test/resources/secret-scan-fixtures/";

    static final Map<String, Pattern> PATTERNS = new TreeMap<>(Map.of(
            "private-key", Pattern.compile("-----BEGIN (?:RSA |EC |DSA |OPENSSH |ENCRYPTED )?PRIVATE KEY-----"),
            "cloud-access-key", Pattern.compile("\\b(?:AKIA|ASIA)[0-9A-Z]{16}\\b"),
            "bearer-token", Pattern.compile("Bearer\\s+([A-Za-z0-9._~+/=-]{16,})"),
            "password-assignment", Pattern.compile("(?i)\\b[a-z0-9_.-]*(?:password|passwd)\\s*[=:]\\s*[\"']?([^\\s\"',;)]{4,})"),
            "secret-assignment", Pattern.compile("(?i)\\b[a-z0-9_.-]*(?:secret|api[_-]?key)\\s*[=:]\\s*[\"']?([^\\s\"',;)]{4,})")));

    /** Labeled demo credentials (non-production; see SECURITY.md). */
    private static final Set<String> ALLOWED_VALUES = Set.of("demo-consumer-token", "demo-requester-token", "demo-approver-token",
            "demo-release-token", "demo-auditor-token", "test-dual-role-token");

    /**
     * Reviewed non-secret matches, keyed by {@code file|pattern}, with the reason. An entry that no longer
     * matches fails the scan, so the list cannot go stale.
     */
    static final Map<String, String> ALLOWED_FINDINGS = Map.of(
            "mvnw|password-assignment", "Maven Wrapper (vendored): a shell parameter expansion that tests whether MVNW_PASSWORD is set",
            "src/main/java/com/agentic/urlshortener/orchestration/policy/rules/NoSecretsRule.java|secret-assignment",
            "Java for-each over the SECRETS patterns of the product's own secret policy rule",
            "src/test/java/com/agentic/urlshortener/common/security/SecurityMatrixTest.java|bearer-token",
            "deliberately invalid token (not-a-known-token) for the 401 test",
            "src/test/java/com/agentic/urlshortener/orchestration/policy/PolicyRulesTest.java|private-key", "fake key fixture for NoSecretsRule",
            "src/test/java/com/agentic/urlshortener/orchestration/policy/PolicyRulesTest.java|password-assignment",
            "fake password fixture (hunter2) for NoSecretsRule",
            "src/test/java/com/agentic/urlshortener/orchestration/policy/PolicyRulesTest.java|cloud-access-key",
            "fake access-key fixture for NoSecretsRule",
            "src/test/java/com/agentic/urlshortener/security/RepositorySecretScanTest.java|bearer-token",
            "this test's own negative example (demo-requester-tokenX)");

    /** Values that are placeholders, not secrets. */
    private static boolean placeholder(String value) {
        return value.startsWith("${") || value.startsWith("<") || value.startsWith("$") || value.startsWith("{")
                || value.equalsIgnoreCase("null") || value.equalsIgnoreCase("none") || value.matches("\\*+|x+|\\.+")
                || value.startsWith("Tokens.") || value.startsWith("bearer(");
    }

    record Finding(String file, int line, String pattern, String excerpt) {
    }

    static List<Finding> scan(String file, String text) {
        List<Finding> findings = new ArrayList<>();
        String[] lines = text.split("\\R", -1);
        for (int i = 0; i < lines.length; i++) {
            for (Map.Entry<String, Pattern> entry : PATTERNS.entrySet()) {
                Matcher matcher = entry.getValue().matcher(lines[i]);
                while (matcher.find()) {
                    String value = matcher.groupCount() > 0 ? matcher.group(1) : matcher.group();
                    if (ALLOWED_VALUES.contains(value) || placeholder(value)) {
                        continue;
                    }
                    findings.add(new Finding(file, i + 1, entry.getKey(), redact(lines[i].trim())));
                }
            }
        }
        return findings;
    }

    private static String redact(String line) {
        return line.length() <= 12 ? "***" : line.substring(0, 12) + "…";
    }

    private static List<String> trackedFiles() throws IOException, InterruptedException {
        Process git = new ProcessBuilder("git", "ls-files", "-z").redirectErrorStream(true).start();
        String out = new String(git.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(git.waitFor()).as("git ls-files: " + out).isZero();
        List<String> files = new ArrayList<>();
        for (String file : out.split("\0")) {
            if (!file.isBlank()) {
                files.add(file);
            }
        }
        return files;
    }

    private static String readText(Path path) throws IOException {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (MalformedInputException binary) {
            return null;
        }
    }

    @Test
    void everyPatternIsDetectedInItsPlantedFixture() throws IOException {
        for (String pattern : PATTERNS.keySet()) {
            Path fixture;
            try (var files = Files.list(Path.of(FIXTURES))) {
                fixture = files.filter(f -> f.getFileName().toString().startsWith(pattern + ".")).findFirst().orElseThrow(
                        () -> new AssertionError("no planted fixture for " + pattern));
            }
            assertThat(scan(fixture.toString(), Files.readString(fixture))).as(pattern)
                    .anyMatch(f -> f.pattern().equals(pattern));
        }
    }

    @Test
    void theLabeledDemoTokensAndPlaceholdersAreAllowed() {
        assertThat(scan("x", "Authorization: Bearer demo-requester-token")).isEmpty();
        assertThat(scan("x", "password: ${DB_PASSWORD}")).isEmpty();
        assertThat(scan("x", "Authorization: Bearer demo-requester-tokenX")).hasSize(1);
    }

    @Test
    void noGitTrackedFileContainsASecret() throws Exception {
        List<String> files = trackedFiles();
        assertThat(files).hasSizeGreaterThan(100);
        List<Finding> findings = new ArrayList<>();
        int scanned = 0;
        for (String file : files) {
            if (file.startsWith(FIXTURES)) {
                continue;
            }
            Path path = Path.of(file);
            if (!Files.isRegularFile(path) || Files.size(path) > 2_000_000) {
                continue;
            }
            String text = readText(path);
            if (text != null) {
                scanned++;
                findings.addAll(scan(file, text));
            }
        }
        assertThat(scanned).isGreaterThan(100);
        Set<String> matchedAllowances = new java.util.TreeSet<>();
        List<Finding> unexplained = new ArrayList<>();
        for (Finding finding : findings) {
            String key = finding.file() + "|" + finding.pattern();
            if (ALLOWED_FINDINGS.containsKey(key)) {
                matchedAllowances.add(key);
            } else {
                unexplained.add(finding);
            }
        }
        assertThat(unexplained).as("possible secrets in git-tracked files (values redacted)").isEmpty();
        assertThat(matchedAllowances).as("stale entries in ALLOWED_FINDINGS")
                .isEqualTo(new java.util.TreeSet<>(ALLOWED_FINDINGS.keySet()));
    }
}
