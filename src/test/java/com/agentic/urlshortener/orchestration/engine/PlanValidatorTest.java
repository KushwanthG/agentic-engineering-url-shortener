package com.agentic.urlshortener.orchestration.engine;

import static com.agentic.urlshortener.orchestration.domain.StageType.ARCHITECTURE_APPROVAL;
import static com.agentic.urlshortener.orchestration.domain.StageType.DESIGN;
import static com.agentic.urlshortener.orchestration.domain.StageType.FINAL_SUMMARY;
import static com.agentic.urlshortener.orchestration.domain.StageType.RELEASE;
import static com.agentic.urlshortener.orchestration.domain.StageType.RELEASE_APPROVAL;
import static com.agentic.urlshortener.orchestration.domain.StageType.REQUIREMENT_ANALYSIS;
import static com.agentic.urlshortener.orchestration.domain.StageType.REQUIREMENT_INGESTION;
import static com.agentic.urlshortener.orchestration.domain.StageType.TESTING;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.agentic.urlshortener.orchestration.domain.StageType;
import com.agentic.urlshortener.orchestration.planning.PlanGraph;
import com.agentic.urlshortener.orchestration.planning.StageSpec;

/** T037: an invalid plan must never execute (FR-ORC-02); structural invariants of plan.md §3. */
@Tag("FR-ORC-02")
@Tag("FR-ORC-06")
class PlanValidatorTest {

    private final PlanValidator validator = new PlanValidator();

    private static StageSpec s(StageType key, StageType... deps) {
        return StageSpec.of(key, List.of(deps));
    }

    private static StageSpec gate(StageType key, StageType... deps) {
        return new StageSpec(key, List.of(deps), true, null);
    }

    @Test
    void acceptsAMinimalValidPlan() {
        PlanGraph plan = new PlanGraph(List.of(s(REQUIREMENT_INGESTION), s(REQUIREMENT_ANALYSIS, REQUIREMENT_INGESTION),
                gate(RELEASE_APPROVAL, REQUIREMENT_ANALYSIS), s(RELEASE, RELEASE_APPROVAL), s(FINAL_SUMMARY, RELEASE)));
        assertThatCode(() -> validator.validate(plan)).doesNotThrowAnyException();
    }

    @Test
    void rejectsCycles() {
        PlanGraph plan = new PlanGraph(List.of(s(REQUIREMENT_INGESTION), s(REQUIREMENT_ANALYSIS, REQUIREMENT_INGESTION, DESIGN),
                s(DESIGN, REQUIREMENT_ANALYSIS)));
        assertThatThrownBy(() -> validator.validate(plan)).isInstanceOf(InvalidPlanException.class).hasMessageContaining("cycle");
    }

    @Test
    void rejectsUnknownDependencies() {
        PlanGraph plan = new PlanGraph(List.of(s(REQUIREMENT_INGESTION), s(REQUIREMENT_ANALYSIS, DESIGN)));
        assertThatThrownBy(() -> validator.validate(plan)).isInstanceOf(InvalidPlanException.class).hasMessageContaining("unknown dependency");
    }

    @Test
    void rejectsMultipleRootsOrAWrongRoot() {
        PlanGraph twoRoots = new PlanGraph(List.of(s(REQUIREMENT_INGESTION), s(DESIGN)));
        assertThatThrownBy(() -> validator.validate(twoRoots)).isInstanceOf(InvalidPlanException.class).hasMessageContaining("root");
        PlanGraph wrongRoot = new PlanGraph(List.of(s(DESIGN)));
        assertThatThrownBy(() -> validator.validate(wrongRoot)).isInstanceOf(InvalidPlanException.class).hasMessageContaining("root");
    }

    @Test
    void rejectsReleaseWithoutReleaseApproval() {
        PlanGraph plan = new PlanGraph(List.of(s(REQUIREMENT_INGESTION), s(REQUIREMENT_ANALYSIS, REQUIREMENT_INGESTION),
                gate(ARCHITECTURE_APPROVAL, REQUIREMENT_ANALYSIS), s(RELEASE, ARCHITECTURE_APPROVAL)));
        assertThatThrownBy(() -> validator.validate(plan)).isInstanceOf(InvalidPlanException.class).hasMessageContaining("RELEASE_APPROVAL");
    }

    @Test
    void rejectsSideEffectingStagesNotDownstreamOfAGate() {
        PlanGraph plan = new PlanGraph(List.of(s(REQUIREMENT_INGESTION), s(REQUIREMENT_ANALYSIS, REQUIREMENT_INGESTION),
                s(TESTING, REQUIREMENT_ANALYSIS)));
        assertThatThrownBy(() -> validator.validate(plan)).isInstanceOf(InvalidPlanException.class).hasMessageContaining("TESTING");
    }

    @Test
    void rejectsDuplicateStages() {
        PlanGraph plan = new PlanGraph(List.of(s(REQUIREMENT_INGESTION), s(REQUIREMENT_INGESTION)));
        assertThatThrownBy(() -> validator.validate(plan)).isInstanceOf(InvalidPlanException.class).hasMessageContaining("duplicate");
    }
}
