package com.agentic.urlshortener.orchestration.agent;

import java.util.Set;

import com.agentic.urlshortener.orchestration.domain.StageType;

/**
 * A stage worker (ADR-017). Agents are deterministic and knowledge-driven; they read their inputs
 * from the {@link StageContext}, reach the application plane only through its permission-scoped
 * port, and can neither decide gates nor change policies. An AI-backed agent can be registered for a
 * stage later, with the deterministic agent kept as its fallback (FR-ORC-12).
 */
public interface StageAgent {

    StageType stageType();

    /** Agent identifier with version, for example {@code requirement-analyst@1.0}. */
    String agentId();

    /** Port operations this agent may use; enforced by the port proxy. */
    Set<AgentPermission> permissions();

    StageResult execute(StageContext context);

    /** A fallback agent runs after a permanent failure or retry exhaustion; its output is marked degraded. */
    default boolean fallback() {
        return false;
    }
}
