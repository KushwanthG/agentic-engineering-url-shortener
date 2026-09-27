package com.agentic.urlshortener.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.agentic.urlshortener.orchestration.agent.ArtifactDraft;
import com.agentic.urlshortener.orchestration.agent.RequirementAnalysisAgent;
import com.agentic.urlshortener.orchestration.agent.RequirementIngestionAgent;
import com.agentic.urlshortener.orchestration.agent.StageAgent;
import com.agentic.urlshortener.orchestration.agent.StageResult;
import com.agentic.urlshortener.orchestration.knowledge.AmbiguityLexicon;
import com.agentic.urlshortener.orchestration.knowledge.CapabilityCatalog;
import com.agentic.urlshortener.orchestration.port.ApplicationPlanePort;

/**
 * Runs real agents one after another outside the engine, accumulating their artifacts, so that each
 * agent test works on genuine upstream output rather than hand-written fixtures.
 */
public final class AgentChain {

    public static final CapabilityCatalog CATALOG = CapabilityCatalog.load();
    public static final AmbiguityLexicon LEXICON = AmbiguityLexicon.load();

    private final String requirement;
    private final UUID runId = UUID.randomUUID();
    private final Map<String, String> artifacts = new LinkedHashMap<>();
    private ApplicationPlanePort port;

    private AgentChain(String requirement) {
        this.requirement = requirement;
    }

    /** Starts a chain with ingestion and analysis already applied. */
    public static AgentChain analyzed(String requirement) {
        return new AgentChain(requirement)
                .then(new RequirementIngestionAgent())
                .then(new RequirementAnalysisAgent(CATALOG, LEXICON));
    }

    public AgentChain withPort(ApplicationPlanePort port) {
        this.port = port;
        return this;
    }

    /** Runs {@code agent}; it must succeed, and its artifacts are added. */
    public AgentChain then(StageAgent agent) {
        StageResult result = run(agent);
        assertThat(result).as(agent.agentId()).isInstanceOf(StageResult.Succeeded.class);
        for (ArtifactDraft draft : ((StageResult.Succeeded) result).artifacts()) {
            artifacts.put(draft.type(), draft.content());
        }
        return this;
    }

    /** Runs {@code agent} and returns its raw result without adding artifacts. */
    public StageResult run(StageAgent agent) {
        return agent.execute(RequirementFixtures.context(runId, agent.stageType(), requirement, artifacts, port, () -> false));
    }

    public String artifact(String type) {
        return artifacts.get(type);
    }

    public Map<String, String> artifacts() {
        return artifacts;
    }

    public UUID runId() {
        return runId;
    }

    public AgentChain put(String type, String content) {
        artifacts.put(type, content);
        return this;
    }
}
