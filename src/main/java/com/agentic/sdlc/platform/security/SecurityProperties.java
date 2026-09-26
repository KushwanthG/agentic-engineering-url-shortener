package com.agentic.sdlc.platform.security;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configured principals ({@code app.security.principals}). Only SHA-256 hashes of tokens are
 * configured; the default profile defines none, so every authenticated API is closed (secure default).
 */
@ConfigurationProperties("app.security")
public record SecurityProperties(List<PrincipalConfig> principals) {

    public SecurityProperties {
        principals = principals == null ? List.of() : List.copyOf(principals);
    }

    public record PrincipalConfig(String id, String displayName, String tokenSha256, List<Role> roles) {
    }
}
