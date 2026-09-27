package com.agentic.urlshortener.common.security;

/** Roles of ADR-015. Control-plane roles are held by humans; agents never hold a role. */
public enum Role {
    API_CONSUMER,
    REQUESTER,
    APPROVER,
    RELEASE_OWNER,
    AUDITOR;

    public String authority() {
        return "ROLE_" + name();
    }
}
