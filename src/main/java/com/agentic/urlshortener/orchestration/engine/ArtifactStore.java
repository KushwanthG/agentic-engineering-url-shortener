package com.agentic.urlshortener.orchestration.engine;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.agentic.urlshortener.common.util.CanonicalJson;
import com.agentic.urlshortener.common.util.Fingerprints;
import com.agentic.urlshortener.orchestration.agent.ArtifactDraft;
import com.agentic.urlshortener.orchestration.agent.StageContext.ArtifactInput;
import com.agentic.urlshortener.orchestration.domain.Artifact;
import com.agentic.urlshortener.orchestration.domain.StageType;
import com.agentic.urlshortener.orchestration.repository.ArtifactRepository;

/**
 * Stores stage outputs as versioned, fingerprinted artifacts that reference their inputs
 * (FR-ORC-10), and assembles the inputs of a stage from the run's current artifacts.
 */
@Component
public class ArtifactStore {

    private final ArtifactRepository artifacts;
    private final RunAudit audit;

    public ArtifactStore(ArtifactRepository artifacts, RunAudit audit) {
        this.artifacts = artifacts;
        this.audit = audit;
    }

    /** Current (not superseded) artifact per type. */
    public Map<String, Artifact> current(UUID runId) {
        Map<String, Artifact> byType = new LinkedHashMap<>();
        for (Artifact artifact : artifacts.findByRunIdAndSupersededFalseOrderByCreatedAtAsc(runId)) {
            byType.merge(artifact.getArtifactType(), artifact, (a, b) -> a.getVersion() >= b.getVersion() ? a : b);
        }
        return byType;
    }

    /** The declared inputs of a stage that are present, as agent-facing views. */
    public Map<String, ArtifactInput> inputsFor(UUID runId, StageType stage) {
        StageInputs.Declared declared = StageInputs.of(stage);
        Map<String, Artifact> current = current(runId);
        Map<String, ArtifactInput> inputs = new LinkedHashMap<>();
        current.forEach((type, artifact) -> {
            if (declared.all() || declared.required().contains(type) || declared.optional().contains(type)) {
                inputs.put(type, view(artifact));
            }
        });
        return inputs;
    }

    /** Persists the drafts of one successful attempt; a new version supersedes the previous one of its type. */
    public List<Artifact> store(UUID runId, StageType stage, int generation, int attemptNo, String producedBy,
            List<ArtifactDraft> drafts, Map<String, ArtifactInput> inputs, Instant now) {
        String inputRefs = CanonicalJson.write(inputs.values().stream().map(ArtifactStore::ref).toList());
        List<Artifact> stored = new ArrayList<>();
        for (ArtifactDraft draft : drafts) {
            artifacts.findFirstByRunIdAndArtifactTypeAndSupersededFalseOrderByVersionDesc(runId, draft.type())
                    .ifPresent(Artifact::supersede);
            int version = artifacts.maxVersion(runId, draft.type()) + 1;
            Artifact artifact = artifacts.save(Artifact.create(runId, stage, generation, attemptNo, draft.type(), version,
                    draft.mediaType(), draft.content(), Fingerprints.sha256(draft.content()), producedBy, inputRefs, now));
            audit.system(runId, "ARTIFACT_RECORDED", draft.type(), "OK", null, Map.of("artifactId", artifact.getId().toString(),
                    "version", version, "fingerprint", artifact.getFingerprint(), "stage", stage.name()));
            stored.add(artifact);
        }
        return stored;
    }

    /** Marks the current artifacts produced by {@code stage} as superseded (the stage is re-opened by re-planning). */
    public List<String> supersedeStage(UUID runId, StageType stage) {
        List<String> superseded = new ArrayList<>();
        for (Artifact artifact : artifacts.findByRunIdAndSupersededFalseOrderByCreatedAtAsc(runId)) {
            if (artifact.getStageKey() == stage) {
                artifact.supersede();
                superseded.add(artifact.getArtifactType() + " v" + artifact.getVersion());
            }
        }
        if (!superseded.isEmpty()) {
            audit.system(runId, "ARTIFACTS_SUPERSEDED", stage.name(), "OK", "stage re-opened", Map.of("artifacts", superseded));
        }
        return superseded;
    }

    /** The artifacts one generation of a stage produced (for reuse when its inputs are unchanged). */
    public List<Artifact> producedBy(UUID runId, StageType stage, int generation) {
        return artifacts.findByRunIdOrderByCreatedAtAsc(runId).stream()
                .filter(a -> a.getStageKey() == stage && a.getGeneration() == generation).toList();
    }

    public static ArtifactInput view(Artifact artifact) {
        return new ArtifactInput(artifact.getId(), artifact.getArtifactType(), artifact.getVersion(), artifact.getFingerprint(),
                artifact.getMediaType(), artifact.getContent());
    }

    private static Map<String, Object> ref(ArtifactInput input) {
        Map<String, Object> ref = new LinkedHashMap<>();
        ref.put("artifactId", input.id().toString());
        ref.put("type", input.type());
        ref.put("version", input.version());
        ref.put("fingerprint", input.fingerprint());
        return ref;
    }
}
