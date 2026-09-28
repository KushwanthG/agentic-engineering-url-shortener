package com.agentic.urlshortener.traceability;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * T108 (SC-003, constitution X): executable traceability between the specification, the tests, and
 * the tasks.
 * <ul>
 * <li>Every functional requirement and scenario of spec.md is verified by at least one test class
 *   (tag). The exceptions are the explicitly deferred requirements below; each names its deferred
 *   task, and an entry fails once it becomes stale (the requirement gains a test).</li>
 * <li>Every test class carries at least one tag that is a known requirement, scenario, drill, or
 *   success-criterion id.</li>
 * <li>No tag is an unknown id (typo guard).</li>
 * <li>Every task in tasks.md has a {@code Req} field.</li>
 * </ul>
 * Writes {@code target/traceability/requirements-to-tests.md}.
 */
@Tag("SC-003")
class TraceabilityMatrixTest {

    private static final Path FEATURE = Path.of("specs", "001-agentic-url-shortener");
    private static final Path TESTS = Path.of("src", "test", "java");
    private static final Path MATRIX = Path.of("target", "traceability", "requirements-to-tests.md");

    /** Requirements deferred by scope decision SD-1 (docs/assessment/timebox-and-scope.md); reported, never hidden. */
    private static final Map<String, String> DEFERRED = Map.of(
            "FR-RPL-06", "T096 late ambiguity during execution (SD-1)");

    private static final Pattern FR_DEFINITION = Pattern.compile("(?m)^- \\*\\*(FR-[A-Z]+-\\d+)\\*\\*");
    private static final Pattern ANY_ID = Pattern.compile("\\b(N?FR-[A-Z]+-\\d+|SCN-[A-Z]|RDR-\\d{2}|SC-\\d{3})\\b");
    private static final Pattern SCENARIO = Pattern.compile("\\bSCN-[A-Z]\\b");
    private static final Pattern TAG = Pattern.compile("@Tag\\(\"([^\"]+)\"\\)");
    private static final Pattern TEST_METHOD = Pattern.compile("(?m)^\\s*@(Test|ParameterizedTest|RepeatedTest|TestFactory)\\b");
    private static final Pattern TASK = Pattern.compile("(?m)^- \\[[ Xx]\\] (T\\d{3})\\b");

    private static Set<String> knownIds;
    private static Set<String> functionalRequirements;
    private static Set<String> scenarios;
    private static Map<String, Set<String>> tagsByTestClass;

    @BeforeAll
    static void load() throws IOException {
        String spec = Files.readString(FEATURE.resolve("spec.md"), StandardCharsets.UTF_8);
        functionalRequirements = matches(FR_DEFINITION, spec);
        knownIds = matches(ANY_ID, spec);
        scenarios = matches(SCENARIO, spec);
        tagsByTestClass = new TreeMap<>();
        try (Stream<Path> files = Files.walk(TESTS)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file, StandardCharsets.UTF_8);
                if (TEST_METHOD.matcher(source).find()) {
                    tagsByTestClass.put(TESTS.relativize(file).toString().replace('\\', '/'), matches(TAG, source));
                }
            }
        }
    }

    private static Set<String> matches(Pattern pattern, String text) {
        Set<String> found = new TreeSet<>();
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            found.add(matcher.groupCount() == 0 ? matcher.group() : matcher.group(1));
        }
        return found;
    }

    private static Map<String, Set<String>> testsById() {
        Map<String, Set<String>> byId = new TreeMap<>();
        tagsByTestClass.forEach((test, tags) -> tags.forEach(tag -> byId.computeIfAbsent(tag, t -> new TreeSet<>()).add(test)));
        return byId;
    }

    @Test
    void theSpecificationDefinesRequirementsAndScenarios() {
        assertThat(functionalRequirements).hasSizeGreaterThan(80).contains("FR-LNK-01", "FR-RPL-05");
        assertThat(scenarios).containsExactly("SCN-A", "SCN-B", "SCN-C");
        assertThat(knownIds).contains("NFR-OBS-02", "RDR-07", "SC-009");
    }

    @Test
    void everyFunctionalRequirementAndScenarioIsVerifiedByATestUnlessExplicitlyDeferred() {
        Map<String, Set<String>> byId = testsById();
        List<String> orphans = new ArrayList<>();
        for (String id : new TreeSet<>(Set.copyOf(concat(functionalRequirements, scenarios)))) {
            if (!byId.containsKey(id) && !DEFERRED.containsKey(id)) {
                orphans.add(id);
            }
        }
        assertThat(orphans).as("requirements or scenarios without any test").isEmpty();
        assertThat(DEFERRED.keySet()).as("deferred entries that now have tests (remove them)").noneMatch(byId::containsKey);
        assertThat(functionalRequirements).as("deferred entries must be real requirements").containsAll(DEFERRED.keySet());
    }

    @Test
    void everyTestClassTracesToARequirementScenarioOrDrillAndNoTagIsUnknown() {
        List<String> untraced = new ArrayList<>();
        List<String> unknown = new ArrayList<>();
        tagsByTestClass.forEach((test, tags) -> {
            if (tags.stream().noneMatch(knownIds::contains)) {
                untraced.add(test);
            }
            tags.stream().filter(t -> !knownIds.contains(t)).forEach(t -> unknown.add(test + ": " + t));
        });
        assertThat(untraced).as("test classes without a requirement, scenario, drill, or success-criterion tag").isEmpty();
        assertThat(unknown).as("tags that are not ids defined in spec.md").isEmpty();
    }

    @Test
    void everyTaskHasARequirementField() throws IOException {
        String tasks = Files.readString(FEATURE.resolve("tasks.md"), StandardCharsets.UTF_8);
        List<String> withoutReq = new ArrayList<>();
        Matcher matcher = TASK.matcher(tasks);
        List<int[]> spans = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        while (matcher.find()) {
            ids.add(matcher.group(1));
            spans.add(new int[] {matcher.start(), 0});
        }
        for (int i = 0; i < ids.size(); i++) {
            int end = i + 1 < spans.size() ? spans.get(i + 1)[0] : tasks.length();
            String block = tasks.substring(spans.get(i)[0], end);
            if (!block.contains("Req:")) {
                withoutReq.add(ids.get(i));
            }
        }
        assertThat(ids).as("tasks parsed").hasSizeGreaterThan(100);
        assertThat(withoutReq).as("tasks without a Req field").isEmpty();
    }

    @Test
    void writesTheRequirementsToTestsMatrix() throws IOException {
        Map<String, Set<String>> byId = testsById();
        StringBuilder md = new StringBuilder("# Requirements to tests\n\n")
                .append("Generated by `TraceabilityMatrixTest` from `spec.md` and the `@Tag` values in `src/test/java`. ")
                .append("Do not edit by hand; regenerate with `mvnw test -Dtest=TraceabilityMatrixTest`.\n\n")
                .append("| Id | Status | Test classes |\n|---|---|---|\n");
        int covered = 0;
        for (String id : knownIds) {
            Set<String> tests = byId.getOrDefault(id, Set.of());
            String status = !tests.isEmpty() ? "VERIFIED" : DEFERRED.containsKey(id) ? "DEFERRED: " + DEFERRED.get(id) : "NO TEST";
            covered += tests.isEmpty() ? 0 : 1;
            md.append("| ").append(id).append(" | ").append(status).append(" | ")
                    .append(tests.stream().map(t -> "`" + t.substring(t.lastIndexOf('/') + 1).replace(".java", "") + "`")
                            .reduce((a, b) -> a + ", " + b).orElse(""))
                    .append(" |\n");
        }
        md.append("\n").append(covered).append(" of ").append(knownIds.size()).append(" ids have at least one test class; ")
                .append(tagsByTestClass.size()).append(" test classes scanned. Non-functional requirements and success criteria ")
                .append("without a test tag are verified by measurement or review; see docs/traceability/.\n");
        Files.createDirectories(MATRIX.getParent());
        Files.writeString(MATRIX, md.toString(), StandardCharsets.UTF_8);
        assertThat(MATRIX).exists();
    }

    private static List<String> concat(Set<String> a, Set<String> b) {
        List<String> all = new ArrayList<>(a);
        all.addAll(b);
        return all;
    }
}
