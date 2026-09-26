package com.agentic.sdlc.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

/**
 * T013: module boundaries of ADR-001. Rules are executable so that architecture drift fails the
 * build. Empty packages are tolerated until the shortener exists (T029 flips this switch).
 */
@Tag("NFR-MNT-01")
class ArchitectureTest {

    private static final boolean ALLOW_EMPTY = true;

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.agentic.sdlc");

    @Test
    void applicationPlaneDoesNotDependOnControlPlane() {
        noClasses().that().resideInAPackage("..shortener..")
                .should().dependOnClassesThat().resideInAPackage("..orchestration..")
                .allowEmptyShould(ALLOW_EMPTY)
                .check(CLASSES);
    }

    @Test
    void controlPlaneReachesApplicationPlaneOnlyThroughTheIntegrationAdapter() {
        noClasses().that().resideInAPackage("..orchestration..")
                .and().resideOutsideOfPackage("..orchestration.integration..")
                .should().dependOnClassesThat().resideInAPackage("..shortener..")
                .allowEmptyShould(ALLOW_EMPTY)
                .check(CLASSES);
    }

    @Test
    void platformDependsOnNeitherPlane() {
        noClasses().that().resideInAPackage("..platform..")
                .should().dependOnClassesThat().resideInAnyPackage("..shortener..", "..orchestration..")
                .allowEmptyShould(ALLOW_EMPTY)
                .check(CLASSES);
    }

    @Test
    void shortenerDomainIsFreeOfWebConcerns() {
        noClasses().that().resideInAPackage("..shortener.domain..")
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework.web..", "jakarta.servlet..")
                .allowEmptyShould(ALLOW_EMPTY)
                .check(CLASSES);
    }

    @Test
    void topLevelModulesAreFreeOfCycles() {
        slices().matching("com.agentic.sdlc.(*)..")
                .should().beFreeOfCycles()
                .allowEmptyShould(ALLOW_EMPTY)
                .check(CLASSES);
    }
}
