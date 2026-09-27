package com.agentic.urlshortener.orchestration.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.agentic.urlshortener.common.util.CanonicalJson;
import com.agentic.urlshortener.orchestration.domain.ActorType;
import com.agentic.urlshortener.orchestration.domain.Artifact;
import com.agentic.urlshortener.orchestration.domain.Classification;
import com.agentic.urlshortener.orchestration.domain.Decision;
import com.agentic.urlshortener.orchestration.domain.DecisionType;
import com.agentic.urlshortener.orchestration.domain.RequirementVersion;
import com.agentic.urlshortener.orchestration.domain.StageType;
import com.agentic.urlshortener.orchestration.domain.WorkflowRun;
import com.agentic.urlshortener.orchestration.repository.ArtifactRepository;
import com.agentic.urlshortener.orchestration.repository.DecisionRepository;
import com.agentic.urlshortener.orchestration.repository.RequirementVersionRepository;
import com.agentic.urlshortener.orchestration.repository.WorkflowRunRepository;
import com.agentic.urlshortener.support.IntegrationTest;

/**
 * T102 (FR-AUD-03, FR-ORC-10, SCN-C): an artifact's lineage is every valid decision that influenced
 * it: gate decisions bound to an artifact in its transitive input closure, and the decision that
 * produced a requirement version the artifact was derived from. Decisions bound only to the artifact
 * itself, to unrelated artifacts, or invalidated decisions are not lineage.
 */
@IntegrationTest
@Tag("FR-AUD-03")
@Tag("FR-ORC-10")
@Tag("SCN-C")
class LineageServiceTest {

    private static final Instant T0 = Instant.parse("2026-09-27T10:00:00Z");

    @Autowired private LineageService lineage;
    @Autowired private WorkflowRunRepository runs;
    @Autowired private ArtifactRepository artifacts;
    @Autowired private DecisionRepository decisions;
    @Autowired private RequirementVersionRepository requirements;

    private UUID runId;
    private int clock;

    @BeforeEach
    void setUp() {
        runId = runs.saveAndFlush(WorkflowRun.create(UUID.randomUUID(), "AMB-001", "Lineage", "alice", Classification.CHANGE_TO_EXISTING,
                "1.0.0", T0)).getId();
    }

    private Instant next() {
        return T0.plusSeconds(++clock);
    }

    private Artifact artifact(StageType stage, String type, String fingerprint, Artifact... inputs) {
        return artifact(stage, type, 1, fingerprint, inputs);
    }

    private Artifact artifact(StageType stage, String type, int version, String fingerprint, Artifact... inputs) {
        List<Map<String, Object>> refs = java.util.Arrays.stream(inputs).map(i -> Map.<String, Object>of("artifactId", i.getId().toString(),
                "type", i.getArtifactType(), "version", i.getVersion(), "fingerprint", i.getFingerprint())).toList();
        return artifacts.saveAndFlush(Artifact.create(runId, stage, 1, 1, type, version, Artifact.JSON, "{}", fingerprint, "test@1.0",
                CanonicalJson.write(refs), next()));
    }

    private Decision decision(StageType stage, DecisionType type, Map<String, String> bound) {
        return decisions.saveAndFlush(Decision.create(runId, stage, type, "APPROVED", ActorType.HUMAN, "bob", "APPROVER",
                "reviewed (simulated human input)", null, bound == null ? null : CanonicalJson.write(bound), next()));
    }

    private static String fp(char c) {
        return String.valueOf(c).repeat(64);
    }

    @Test
    void lineageFollowsTheTransitiveInputClosureAndRequirementVersionDecisions() {
        Artifact requirementV1 = artifact(StageType.REQUIREMENT_INGESTION, "REQUIREMENT", fp('1'));
        requirements.saveAndFlush(RequirementVersion.create(runId, 1, "SUBMITTED", "{}", fp('1'), "alice", next(), null));
        Decision clarification = decision(StageType.CLARIFICATION, DecisionType.CLARIFICATION_ANSWER, null);
        Artifact requirementV2 = artifact(StageType.CLARIFICATION, "REQUIREMENT", 2, fp('2'));
        requirements.saveAndFlush(RequirementVersion.create(runId, 2, "CLARIFIED", "{}", fp('2'), "bob", next(), clarification.getId()));

        Artifact normalized = artifact(StageType.REQUIREMENT_ANALYSIS, "NORMALIZED_REQUIREMENT", fp('n'), requirementV2);
        Artifact design = artifact(StageType.DESIGN, "DESIGN", fp('d'), normalized);
        Decision architecture = decision(StageType.ARCHITECTURE_APPROVAL, DecisionType.GATE, Map.of("DESIGN", fp('d')));
        Decision invalidated = decision(StageType.ARCHITECTURE_APPROVAL, DecisionType.GATE, Map.of("DESIGN", fp('d')));
        invalidated.invalidate("bound artifact changed");
        decisions.saveAndFlush(invalidated);
        decision(StageType.RELEASE_APPROVAL, DecisionType.GATE, Map.of("OTHER", fp('z')));
        Artifact changeSet = artifact(StageType.IMPLEMENTATION, "CHANGE_SET", fp('c'), design);

        assertThat(lineage.of(runId, changeSet)).extracting(Decision::getId)
                .containsExactly(clarification.getId(), architecture.getId());
        // The gate approved the design itself: it did not influence the design through its inputs.
        assertThat(lineage.of(runId, design)).extracting(Decision::getId).containsExactly(clarification.getId());
        // The clarified requirement was produced by the clarification decision.
        assertThat(lineage.of(runId, requirementV2)).extracting(Decision::getId).containsExactly(clarification.getId());
        // The original requirement predates and does not derive from the clarification.
        assertThat(lineage.of(runId, requirementV1)).isEmpty();
    }

    @Test
    void anArtifactWithoutInputsOrDecisionsHasAnEmptyLineage() {
        Artifact lone = artifact(StageType.THREAT_ASSESSMENT, "THREAT_MODEL", fp('t'));
        decision(StageType.RELEASE_APPROVAL, DecisionType.GATE, Map.of("THREAT_MODEL", fp('t')));
        assertThat(lineage.of(runId, lone)).isEmpty();
    }
}
