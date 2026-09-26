package com.agentic.urlshortener.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

/**
 * T013: module boundaries of ADR-001. Rules are executable so that architecture drift fails the
 * build. Empty packages fail the rules (switched on by T029 once the shortener exists).
 */
@Tag("NFR-MNT-01")
class ArchitectureTest {

    private static final boolean ALLOW_EMPTY = false;

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.agentic.urlshortener");

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
    void commonDependsOnNeitherPlane() {
        noClasses().that().resideInAPackage("..common..")
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
        slices().matching("com.agentic.urlshortener.(*)..")
                .should().beFreeOfCycles()
                .allowEmptyShould(ALLOW_EMPTY)
                .check(CLASSES);
    }
}
