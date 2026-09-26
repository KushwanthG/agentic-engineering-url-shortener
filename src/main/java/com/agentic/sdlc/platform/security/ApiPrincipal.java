package com.agentic.sdlc.platform.security;

import java.security.Principal;
import java.util.Set;

/** An authenticated caller: a human principal or an API consumer (never an agent). */
public record ApiPrincipal(String id, String displayName, Set<Role> roles) implements Principal {

    public ApiPrincipal {
        roles = Set.copyOf(roles);
    }

    public boolean hasRole(Role role) {
        return roles.contains(role);
    }

    @Override
    public String getName() {
        return id;
    }
}
