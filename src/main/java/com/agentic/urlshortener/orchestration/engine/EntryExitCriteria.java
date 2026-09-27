package com.agentic.urlshortener.orchestration.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.agentic.urlshortener.common.util.CanonicalJson;
import com.agentic.urlshortener.orchestration.agent.ArtifactDraft;
import com.agentic.urlshortener.orchestration.domain.Artifact;
import com.agentic.urlshortener.orchestration.domain.StageType;

import tools.jackson.databind.JsonNode;

/**
 * Entry criteria are checked before a stage becomes ready; exit criteria when an attempt succeeds.
 * A failed exit criterion fails the stage, so its dependents never start (FR-ORC-07).
 */
@Component
public class EntryExitCriteria {

    /** Empty when the stage may start; otherwise the unmet criterion. */
    public Optional<String> entry(StageType stage, Map<String, Artifact> current) {
        for (String required : StageInputs.of(stage).required()) {
            if (!current.containsKey(required)) {
                return Optional.of("entry criterion not met: required input " + required + " is missing");
            }
        }
        if (stage == StageType.DECOMPOSITION || stage == StageType.THREAT_ASSESSMENT || stage == StageType.IMPACT_ANALYSIS) {
            Artifact normalized = current.get("NORMALIZED_REQUIREMENT");
            if (CanonicalJson.parse(normalized.getContent()).path("clarificationRequired").asBoolean(false)) {
                return Optional.of("entry criterion not met: a blocking ambiguity is still open");
            }
        }
        return Optional.empty();
    }

    /** Empty when the produced artifacts satisfy the stage's exit criteria; otherwise the violations. */
    public Optional<String> exit(StageType stage, List<ArtifactDraft> drafts) {
        List<String> violations = new ArrayList<>();
        for (String type : stage.outputArtifactTypes()) {
            if (drafts.stream().noneMatch(d -> d.type().equals(type))) {
                violations.add("missing output " + type);
            }
        }
        for (ArtifactDraft draft : drafts) {
            if (draft.content() == null || draft.content().isBlank()) {
                violations.add("empty output " + draft.type());
            } else if (Artifact.JSON.equals(draft.mediaType())) {
                try {
                    JsonNode json = CanonicalJson.parse(draft.content());
                    if (draft.type().equals("VALIDATION_REPORT") && !json.path("passed").asBoolean(false)) {
                        violations.add("validation did not pass");
                    }
                } catch (RuntimeException e) {
                    violations.add("output " + draft.type() + " is not valid JSON");
                }
            }
        }
        return violations.isEmpty() ? Optional.empty() : Optional.of("exit criteria not met: " + String.join("; ", violations));
    }
}
