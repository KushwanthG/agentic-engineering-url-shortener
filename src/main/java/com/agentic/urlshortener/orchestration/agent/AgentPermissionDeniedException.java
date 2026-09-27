package com.agentic.urlshortener.orchestration.agent;

/** An agent called a port operation outside its declared permissions; the attempt fails permanently (NFR-AUT-01). */
public class AgentPermissionDeniedException extends RuntimeException {

    public AgentPermissionDeniedException(String agentId, AgentPermission permission, String operation) {
        super("Agent " + agentId + " is not permitted to call " + operation + " (requires " + permission.name() + ")");
    }
}
