package com.agentic.urlshortener.orchestration.agent;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.agentic.urlshortener.common.util.CanonicalJson;
import com.agentic.urlshortener.orchestration.domain.FailureClass;
import com.agentic.urlshortener.orchestration.domain.StageType;

import tools.jackson.databind.JsonNode;

/**
 * REQUIREMENT_INGESTION: checks that the current requirement version is a well-formed requirement
 * document and records it as the {@code REQUIREMENT} artifact (canonical JSON, so its fingerprint is
 * stable). A malformed document is a permanent failure.
 */
@Component
public class RequirementIngestionAgent implements StageAgent {

    @Override
    public StageType stageType() {
        return StageType.REQUIREMENT_INGESTION;
    }

    @Override
    public String agentId() {
        return "requirement-ingestor@1.0";
    }

    @Override
    public Set<AgentPermission> permissions() {
        return Set.of();
    }

    @Override
    public StageResult execute(StageContext context) {
        JsonNode requirement;
        try {
            requirement = context.requirementJson();
        } catch (RuntimeException e) {
            return new StageResult.Failed(FailureClass.PERMANENT, "the requirement is not valid JSON");
        }
        for (String field : List.of("title", "narrative", "type", "acceptanceCriteria")) {
            if (requirement.path(field).isMissingNode() || requirement.path(field).isNull()
                    || (requirement.path(field).isString() && requirement.path(field).asString().isBlank())) {
                return new StageResult.Failed(FailureClass.PERMANENT, "the requirement document has no " + field);
            }
        }
        return new StageResult.Succeeded(List.of(ArtifactDraft.json("REQUIREMENT", CanonicalJson.canonicalize(context.requirement()))),
                "requirement version " + context.requirementVersion() + " ingested");
    }
}
