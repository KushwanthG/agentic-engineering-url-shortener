package com.agentic.urlshortener.orchestration.planning;

import static com.agentic.urlshortener.orchestration.domain.StageType.ARCHITECTURE_APPROVAL;
import static com.agentic.urlshortener.orchestration.domain.StageType.CLARIFICATION;
import static com.agentic.urlshortener.orchestration.domain.StageType.COMPLIANCE_EVALUATION;
import static com.agentic.urlshortener.orchestration.domain.StageType.DECOMPOSITION;
import static com.agentic.urlshortener.orchestration.domain.StageType.DESIGN;
import static com.agentic.urlshortener.orchestration.domain.StageType.DOCUMENTATION;
import static com.agentic.urlshortener.orchestration.domain.StageType.FINAL_SUMMARY;
import static com.agentic.urlshortener.orchestration.domain.StageType.IMPACT_ANALYSIS;
import static com.agentic.urlshortener.orchestration.domain.StageType.IMPLEMENTATION;
import static com.agentic.urlshortener.orchestration.domain.StageType.REGRESSION_TESTING;
import static com.agentic.urlshortener.orchestration.domain.StageType.RELEASE;
import static com.agentic.urlshortener.orchestration.domain.StageType.RELEASE_APPROVAL;
import static com.agentic.urlshortener.orchestration.domain.StageType.REQUIREMENT_ANALYSIS;
import static com.agentic.urlshortener.orchestration.domain.StageType.REQUIREMENT_INGESTION;
import static com.agentic.urlshortener.orchestration.domain.StageType.SECURITY_VERIFICATION;
import static com.agentic.urlshortener.orchestration.domain.StageType.TESTING;
import static com.agentic.urlshortener.orchestration.domain.StageType.THREAT_ASSESSMENT;
import static com.agentic.urlshortener.orchestration.domain.StageType.VALIDATION;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.agentic.urlshortener.orchestration.domain.Classification;
import com.agentic.urlshortener.orchestration.engine.PlanValidator;

/** T037: plan templates of plan.md §3 (sequential paths, fan-out, joins, conditional stages). */
@Tag("FR-ORC-02")
@Tag("FR-ORC-03")
@Tag("FR-ORC-06")
@Tag("SCN-A")
@Tag("SCN-B")
class PlanFactoryTest {

    private final PlanFactory factory = new PlanFactory();

    @Test
    void newCapabilityTemplateHasTheDocumentedStagesAndEdges() {
        PlanGraph plan = factory.create(Classification.NEW_CAPABILITY);

        assertThat(plan.keys()).containsExactly(REQUIREMENT_INGESTION, REQUIREMENT_ANALYSIS, CLARIFICATION, DECOMPOSITION,
                THREAT_ASSESSMENT, DESIGN, ARCHITECTURE_APPROVAL, IMPLEMENTATION, DOCUMENTATION, TESTING, SECURITY_VERIFICATION,
                VALIDATION, COMPLIANCE_EVALUATION, RELEASE_APPROVAL, RELEASE, FINAL_SUMMARY);
        assertThat(plan.stage(REQUIREMENT_ANALYSIS).dependsOn()).containsExactly(REQUIREMENT_INGESTION);
        assertThat(plan.stage(DECOMPOSITION).dependsOn()).containsExactly(CLARIFICATION);
        assertThat(plan.stage(THREAT_ASSESSMENT).dependsOn()).containsExactly(CLARIFICATION);
        assertThat(plan.stage(DESIGN).dependsOn()).containsExactly(DECOMPOSITION, THREAT_ASSESSMENT);
        assertThat(plan.stage(IMPLEMENTATION).dependsOn()).containsExactly(ARCHITECTURE_APPROVAL);
        assertThat(plan.stage(DOCUMENTATION).dependsOn()).containsExactly(ARCHITECTURE_APPROVAL);
        assertThat(plan.stage(VALIDATION).dependsOn()).containsExactlyInAnyOrder(TESTING, SECURITY_VERIFICATION, DOCUMENTATION);
        assertThat(plan.stage(RELEASE).dependsOn()).containsExactly(RELEASE_APPROVAL);
        assertThat(plan.stage(FINAL_SUMMARY).dependsOn()).containsExactly(RELEASE);
    }

    @Test
    void gatesAndConditionsAreDeclared() {
        PlanGraph plan = factory.create(Classification.NEW_CAPABILITY);
        assertThat(plan.stages()).filteredOn(StageSpec::gate).extracting(StageSpec::key)
                .containsExactly(CLARIFICATION, ARCHITECTURE_APPROVAL, RELEASE_APPROVAL);
        assertThat(plan.stage(CLARIFICATION).condition()).isEqualTo(StageSpec.CONDITION_BLOCKING_AMBIGUITY);
        assertThat(plan.stage(ARCHITECTURE_APPROVAL).condition()).isEqualTo(StageSpec.CONDITION_MATERIAL_CHANGE);
        assertThat(plan.stage(DESIGN).condition()).isNull();
    }

    @Test
    void changeToExistingAddsImpactAnalysisAndRegressionTesting() {
        PlanGraph plan = factory.create(Classification.CHANGE_TO_EXISTING);
        assertThat(plan.keys()).contains(IMPACT_ANALYSIS, REGRESSION_TESTING);
        assertThat(plan.stage(IMPACT_ANALYSIS).dependsOn()).containsExactly(CLARIFICATION);
        assertThat(plan.stage(DESIGN).dependsOn()).containsExactlyInAnyOrder(DECOMPOSITION, THREAT_ASSESSMENT, IMPACT_ANALYSIS);
        assertThat(plan.stage(REGRESSION_TESTING).dependsOn()).containsExactly(IMPLEMENTATION);
        assertThat(plan.stage(VALIDATION).dependsOn()).contains(REGRESSION_TESTING);
    }

    @Test
    void undeterminedUsesTheNewCapabilityTemplateUntilReplanned() {
        assertThat(factory.create(Classification.UNDETERMINED).keys()).doesNotContain(IMPACT_ANALYSIS, REGRESSION_TESTING);
    }

    @Test
    void everyTemplateIsValid() {
        for (Classification c : Classification.values()) {
            new PlanValidator().validate(factory.create(c));
        }
    }

    @Test
    void diffReportsAddedStagesAndChangedDependencies() {
        PlanDiff diff = PlanDiff.between(factory.create(Classification.NEW_CAPABILITY), factory.create(Classification.CHANGE_TO_EXISTING));
        assertThat(diff.added()).containsExactlyInAnyOrder("IMPACT_ANALYSIS", "REGRESSION_TESTING");
        assertThat(diff.removed()).isEmpty();
        assertThat(diff.dependencyChanges()).anyMatch(c -> c.startsWith("DESIGN"));
        assertThat(diff.dependencyChanges()).anyMatch(c -> c.startsWith("VALIDATION"));
    }
}
