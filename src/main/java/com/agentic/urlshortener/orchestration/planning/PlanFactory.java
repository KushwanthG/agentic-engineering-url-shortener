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

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.agentic.urlshortener.orchestration.domain.Classification;
import com.agentic.urlshortener.orchestration.domain.StageType;

/**
 * Builds the stage graph template of plan.md §3. A change to existing behavior adds impact analysis
 * (a third analysis branch joined by design) and regression testing (a third verification branch
 * joined by validation). An undetermined requirement starts with the new-capability template and is
 * replanned once clarification determines its type.
 */
@Component
public class PlanFactory {

    public PlanGraph create(Classification classification) {
        boolean brownfield = classification == Classification.CHANGE_TO_EXISTING;
        List<StageSpec> stages = new ArrayList<>();
        stages.add(StageSpec.of(REQUIREMENT_INGESTION, List.of()));
        stages.add(StageSpec.of(REQUIREMENT_ANALYSIS, List.of(REQUIREMENT_INGESTION)));
        stages.add(StageSpec.conditional(CLARIFICATION, List.of(REQUIREMENT_ANALYSIS), StageSpec.CONDITION_BLOCKING_AMBIGUITY));
        stages.add(StageSpec.of(DECOMPOSITION, List.of(CLARIFICATION)));
        stages.add(StageSpec.of(THREAT_ASSESSMENT, List.of(CLARIFICATION)));
        if (brownfield) {
            stages.add(StageSpec.of(IMPACT_ANALYSIS, List.of(CLARIFICATION)));
        }
        stages.add(StageSpec.of(DESIGN, brownfield
                ? List.of(DECOMPOSITION, THREAT_ASSESSMENT, IMPACT_ANALYSIS)
                : List.of(DECOMPOSITION, THREAT_ASSESSMENT)));
        stages.add(StageSpec.conditional(ARCHITECTURE_APPROVAL, List.of(DESIGN), StageSpec.CONDITION_MATERIAL_CHANGE));
        stages.add(StageSpec.of(IMPLEMENTATION, List.of(ARCHITECTURE_APPROVAL)));
        stages.add(StageSpec.of(DOCUMENTATION, List.of(ARCHITECTURE_APPROVAL)));
        stages.add(StageSpec.of(TESTING, List.of(IMPLEMENTATION)));
        if (brownfield) {
            stages.add(StageSpec.of(REGRESSION_TESTING, List.of(IMPLEMENTATION)));
        }
        stages.add(StageSpec.of(SECURITY_VERIFICATION, List.of(IMPLEMENTATION)));
        List<StageType> verification = new ArrayList<>(List.of(TESTING, SECURITY_VERIFICATION, DOCUMENTATION));
        if (brownfield) {
            verification.add(REGRESSION_TESTING);
        }
        stages.add(StageSpec.of(VALIDATION, verification));
        stages.add(StageSpec.of(COMPLIANCE_EVALUATION, List.of(VALIDATION)));
        stages.add(StageSpec.of(RELEASE_APPROVAL, List.of(COMPLIANCE_EVALUATION)));
        stages.add(StageSpec.of(RELEASE, List.of(RELEASE_APPROVAL)));
        stages.add(StageSpec.of(FINAL_SUMMARY, List.of(RELEASE)));
        return new PlanGraph(stages);
    }
}
