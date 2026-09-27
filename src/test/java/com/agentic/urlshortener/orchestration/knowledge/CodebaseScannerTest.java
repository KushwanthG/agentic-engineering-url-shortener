package com.agentic.urlshortener.orchestration.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * T083 (FR-ORC-14): the scanner builds an import graph of a source tree (explicit imports and
 * same-package references, ignoring comments and strings), computes the reverse-dependency closure
 * of seed classes with the dependency chain of every derived class, finds tests that reference
 * impacted classes and documents that mention given terms, and never reads outside its root.
 */
@Tag("FR-ORC-14")
@Tag("SCN-B")
class CodebaseScannerTest {

    private static final Path FIXTURE = Path.of("src", "test", "resources", "codebase-fixture");

    private final CodebaseScanner scanner = new CodebaseScanner(FIXTURE);

    @Test
    void indexesTheMainSourceClassesWithTheirDependencies() {
        CodebaseScanner.Index index = scanner.scan();

        assertThat(index.classes()).containsOnlyKeys("Store", "Counter", "Notes", "Controller", "Unrelated");
        assertThat(index.classes().get("Counter").dependencies()).as("same-package reference").containsExactly("Store");
        assertThat(index.classes().get("Controller").dependencies()).as("import").containsExactly("Counter");
        assertThat(index.classes().get("Notes").dependencies()).as("comments and strings are not references").isEmpty();
        assertThat(index.classes().get("Store").path()).isEqualTo("src/main/java/demo/store/Store.java");
    }

    @Test
    void theReverseClosureListsEveryDerivedClassWithItsDependencyChain() {
        Map<String, List<String>> closure = scanner.scan().reverseClosure(Set.of("Store"));

        assertThat(closure).containsOnlyKeys("Store", "Counter", "Controller");
        assertThat(closure.get("Store")).containsExactly("Store");
        assertThat(closure.get("Counter")).containsExactly("Counter", "Store");
        assertThat(closure.get("Controller")).containsExactly("Controller", "Counter", "Store");
    }

    @Test
    void findsTestsReferencingImpactedClassesAndDocsMentioningTerms() {
        CodebaseScanner.Index index = scanner.scan();

        assertThat(index.testsReferencing(Set.of("Counter", "Store"))).containsExactly("src/test/java/demo/CounterTest.java");
        assertThat(index.docsMentioning(List.of("GET /{code}"))).containsExactly("docs/api/links.md");
    }

    @Test
    void anUnavailableRootIsReportedAndNothingOutsideTheRootIsRead() {
        CodebaseScanner missing = new CodebaseScanner(Path.of("does", "not", "exist"));
        assertThat(missing.available()).isFalse();
        assertThat(missing.scan().classes()).isEmpty();

        assertThat(scanner.available()).isTrue();
        assertThat(scanner.scan().classes().values()).allSatisfy(c -> assertThat(c.path()).doesNotStartWith("..").doesNotContain(":"));
        assertThat(scanner.scan().docsMentioning(List.of("../"))).isEmpty();
    }
}
