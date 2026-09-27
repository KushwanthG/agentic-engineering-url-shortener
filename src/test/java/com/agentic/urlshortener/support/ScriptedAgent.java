package com.agentic.urlshortener.support;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.agentic.urlshortener.orchestration.agent.AgentPermission;
import com.agentic.urlshortener.orchestration.agent.AgentRegistry;
import com.agentic.urlshortener.orchestration.agent.ArtifactDraft;
import com.agentic.urlshortener.orchestration.agent.StageAgent;
import com.agentic.urlshortener.orchestration.agent.StageContext;
import com.agentic.urlshortener.orchestration.agent.StageResult;
import com.agentic.urlshortener.orchestration.domain.StageType;

/**
 * A deterministic test agent for one stage type. Its behavior is looked up in {@link Scripts}, so a test
 * can make a stage sleep, fail, or inspect persisted state while it executes. By default it succeeds
 * with minimal artifacts of the stage's output types (no ambiguity, no material change, validation
 * passed, readiness READY).
 */
public final class ScriptedAgent implements StageAgent {

    private final StageType type;
    private final Scripts scripts;

    public ScriptedAgent(StageType type, Scripts scripts) {
        this.type = type;
        this.scripts = scripts;
    }

    @Override
    public StageType stageType() {
        return type;
    }

    @Override
    public String agentId() {
        return "scripted-" + type.name().toLowerCase().replace('_', '-') + "@test";
    }

    @Override
    public Set<AgentPermission> permissions() {
        return Set.of();
    }

    @Override
    public StageResult execute(StageContext context) {
        return scripts.behavior(type).apply(context);
    }

    /** Default successful result for a stage type. */
    public static StageResult defaultSuccess(StageType type) {
        List<ArtifactDraft> drafts = new ArrayList<>();
        for (String artifactType : type.outputArtifactTypes()) {
            drafts.add(defaultDraft(artifactType));
        }
        return new StageResult.Succeeded(drafts, "scripted success");
    }

    private static ArtifactDraft defaultDraft(String artifactType) {
        return switch (artifactType) {
            case "NORMALIZED_REQUIREMENT" -> ArtifactDraft.json(artifactType,
                    "{\"clarificationRequired\":false,\"clarificationRationale\":\"scripted: no blocking ambiguity\"}");
            case "DESIGN" -> ArtifactDraft.json(artifactType, "{\"materialChange\":false,\"materialReasons\":[]}");
            case "VALIDATION_REPORT" -> ArtifactDraft.json(artifactType, "{\"passed\":true}");
            case "READINESS_REPORT" -> ArtifactDraft.json(artifactType, "{\"outcome\":\"READY\",\"reasons\":[],\"limitations\":[]}");
            case "DOCUMENTATION", "FINAL_SUMMARY" -> ArtifactDraft.markdown(artifactType, "# scripted " + artifactType);
            default -> ArtifactDraft.json(artifactType, "{\"scripted\":\"" + artifactType + "\"}");
        };
    }

    /** Mutable per-test behaviors; reset between tests. */
    public static final class Scripts {

        private final Map<StageType, Function<StageContext, StageResult>> behaviors = new EnumMap<>(StageType.class);

        public synchronized void reset() {
            behaviors.clear();
        }

        public synchronized void set(StageType type, Function<StageContext, StageResult> behavior) {
            behaviors.put(type, behavior);
        }

        /** Sleeps, then succeeds with the default artifacts. */
        public void sleepThenSucceed(StageType type, long millis) {
            set(type, context -> {
                try {
                    Thread.sleep(millis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return defaultSuccess(type);
            });
        }

        synchronized Function<StageContext, StageResult> behavior(StageType type) {
            return behaviors.getOrDefault(type, context -> defaultSuccess(type));
        }
    }

    /** Replaces the production agents with scripted agents for every non-gate stage type. */
    @TestConfiguration
    public static class Config {

        @Bean
        public Scripts scripts() {
            return new Scripts();
        }

        /** Scripted primaries for every non-gate stage, plus the production fallback agents (FR-REL-03). */
        @Bean
        @Primary
        public AgentRegistry scriptedAgentRegistry(Scripts scripts, List<StageAgent> productionAgents) {
            List<StageAgent> agents = new ArrayList<>();
            for (StageType type : StageType.values()) {
                if (!type.isGate()) {
                    agents.add(new ScriptedAgent(type, scripts));
                }
            }
            productionAgents.stream().filter(StageAgent::fallback).forEach(agents::add);
            return new AgentRegistry(agents);
        }
    }
}
