package com.agentic.urlshortener.orchestration.agent;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.agentic.urlshortener.orchestration.domain.StageType;

/** Primary and fallback agents per stage type; gates have no agent. */
public class AgentRegistry {

    private final List<StageAgent> all;
    private final Map<StageType, StageAgent> primaries = new EnumMap<>(StageType.class);
    private final Map<StageType, StageAgent> fallbacks = new EnumMap<>(StageType.class);

    public AgentRegistry(List<StageAgent> agents) {
        this.all = List.copyOf(agents);
        for (StageAgent agent : agents) {
            if (agent.stageType().isGate()) {
                throw new IllegalArgumentException("gate " + agent.stageType() + " cannot have an agent: " + agent.agentId());
            }
            Map<StageType, StageAgent> target = agent.fallback() ? fallbacks : primaries;
            StageAgent previous = target.putIfAbsent(agent.stageType(), agent);
            if (previous != null) {
                throw new IllegalArgumentException("two " + (agent.fallback() ? "fallback" : "primary") + " agents for "
                        + agent.stageType() + ": " + previous.agentId() + ", " + agent.agentId());
            }
        }
    }

    public Optional<StageAgent> primary(StageType type) {
        return Optional.ofNullable(primaries.get(type));
    }

    public Optional<StageAgent> fallback(StageType type) {
        return Optional.ofNullable(fallbacks.get(type));
    }

    public List<StageAgent> all() {
        return all;
    }
}
