package com.agentic.urlshortener.orchestration.engine;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.agentic.urlshortener.common.util.CanonicalJson;
import com.agentic.urlshortener.orchestration.domain.Artifact;
import com.agentic.urlshortener.orchestration.domain.Decision;
import com.agentic.urlshortener.orchestration.domain.RequirementVersion;
import com.agentic.urlshortener.orchestration.repository.ArtifactRepository;
import com.agentic.urlshortener.orchestration.repository.DecisionRepository;
import com.agentic.urlshortener.orchestration.repository.RequirementVersionRepository;

import tools.jackson.databind.JsonNode;

/**
 * Decision lineage of an artifact (FR-AUD-03, FR-ORC-10, ADR-012): the valid decisions that
 * influenced it, in recording order. A decision counts when it is bound to an artifact in the
 * transitive input closure (a gate approving an input), or when it produced a requirement version
 * whose document the artifact is, or derives from (matched by fingerprint). A decision bound only to
 * the artifact itself approved it; it did not shape it.
 */
@Service
@Transactional(readOnly = true)
public class LineageService {

    private static final String REQUIREMENT = "REQUIREMENT";

    private final ArtifactRepository artifacts;
    private final DecisionRepository decisions;
    private final RequirementVersionRepository requirements;

    public LineageService(ArtifactRepository artifacts, DecisionRepository decisions, RequirementVersionRepository requirements) {
        this.artifacts = artifacts;
        this.decisions = decisions;
        this.requirements = requirements;
    }

    public List<Decision> of(UUID runId, Artifact artifact) {
        Map<UUID, Artifact> byId = new HashMap<>();
        artifacts.findByRunIdOrderByCreatedAtAsc(runId).forEach(a -> byId.put(a.getId(), a));
        Set<String> inputFingerprints = new HashSet<>();
        Set<String> requirementFingerprints = new HashSet<>();
        if (REQUIREMENT.equals(artifact.getArtifactType())) {
            requirementFingerprints.add(artifact.getFingerprint());
        }
        Set<UUID> seen = new HashSet<>();
        Deque<UUID> todo = new ArrayDeque<>(inputIds(artifact));
        while (!todo.isEmpty()) {
            Artifact input = byId.get(todo.poll());
            if (input != null && seen.add(input.getId())) {
                inputFingerprints.add(input.getFingerprint());
                if (REQUIREMENT.equals(input.getArtifactType())) {
                    requirementFingerprints.add(input.getFingerprint());
                }
                todo.addAll(inputIds(input));
            }
        }
        Set<UUID> requirementDecisions = new HashSet<>();
        for (RequirementVersion version : requirements.findByRunIdOrderByVersionAsc(runId)) {
            if (version.getDecisionId() != null && requirementFingerprints.contains(version.getFingerprint())) {
                requirementDecisions.add(version.getDecisionId());
            }
        }
        List<Decision> lineage = new ArrayList<>();
        for (Decision decision : decisions.findByRunIdOrderByCreatedAtAsc(runId)) {
            if (decision.isValid() && (requirementDecisions.contains(decision.getId()) || boundTo(decision, inputFingerprints))) {
                lineage.add(decision);
            }
        }
        return lineage;
    }

    @SuppressWarnings("unchecked")
    private static boolean boundTo(Decision decision, Set<String> fingerprints) {
        return decision.getBoundFingerprints() != null
                && CanonicalJson.read(decision.getBoundFingerprints(), Map.class).values().stream().anyMatch(fingerprints::contains);
    }

    private static List<UUID> inputIds(Artifact artifact) {
        List<UUID> ids = new ArrayList<>();
        for (JsonNode ref : CanonicalJson.parse(artifact.getInputRefs())) {
            ids.add(UUID.fromString(ref.path("artifactId").asString()));
        }
        return ids;
    }
}
