package com.agentic.urlshortener.common.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.agentic.urlshortener.common.util.Fingerprints;

/** Resolves bearer tokens to principals by comparing SHA-256 hashes in constant time. */
@Component
public class PrincipalRegistry {

    private static final Pattern SHA256_HEX = Pattern.compile("[0-9a-f]{64}");

    private final List<Entry> entries;

    public PrincipalRegistry(SecurityProperties properties) {
        this.entries = properties.principals().stream().map(PrincipalRegistry::toEntry).toList();
    }

    private static Entry toEntry(SecurityProperties.PrincipalConfig config) {
        if (config.id() == null || config.id().isBlank()) {
            throw new IllegalStateException("app.security.principals[].id must not be blank");
        }
        if (config.tokenSha256() == null || !SHA256_HEX.matcher(config.tokenSha256()).matches()) {
            throw new IllegalStateException("app.security.principals[" + config.id() + "].token-sha256 must be 64 lower-case hex characters");
        }
        Set<Role> roles = config.roles() == null ? Set.of() : Set.copyOf(config.roles());
        return new Entry(new ApiPrincipal(config.id(), config.displayName(), roles), HexFormat.of().parseHex(config.tokenSha256()));
    }

    /** The principal owning {@code rawToken}, if any. Every configured hash is compared (no early exit). */
    public Optional<ApiPrincipal> authenticate(String rawToken) {
        byte[] presented = Fingerprints.digest(rawToken.getBytes(StandardCharsets.UTF_8));
        ApiPrincipal match = null;
        for (Entry entry : entries) {
            if (MessageDigest.isEqual(presented, entry.tokenHash())) {
                match = entry.principal();
            }
        }
        return Optional.ofNullable(match);
    }

    private record Entry(ApiPrincipal principal, byte[] tokenHash) {
    }
}
