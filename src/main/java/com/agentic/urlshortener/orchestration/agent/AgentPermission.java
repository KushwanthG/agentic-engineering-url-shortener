package com.agentic.urlshortener.orchestration.agent;

/** Operations on the application plane an agent may declare (plan.md §3, "Agent contract"). */
public enum AgentPermission {
    READ_LINKS,
    WRITE_SYNTHETIC_LINKS,
    READ_CAPABILITIES,
    PREVIEW_CAPABILITY,
    CHANGE_CAPABILITY_RELEASE
}
