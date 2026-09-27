package com.agentic.urlshortener.shortener.domain;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.agentic.urlshortener.common.exception.ApiException;
import com.agentic.urlshortener.common.exception.ErrorCode;

/**
 * Custom alias rules (GF-001 AC-3..AC-5): 3 to 32 letters, digits, hyphens, or underscores, and not a
 * reserved word. Aliases are case-sensitive like generated codes, but reserved words are matched
 * ignoring case so that no alias can shadow a system route such as {@code /API} or {@code /Actuator}.
 */
public class AliasPolicy {

    private static final Pattern ALPHABET = Pattern.compile("^[A-Za-z0-9_-]*$");
    private static final int MIN_LENGTH = 3;
    private static final int MAX_LENGTH = 32;

    private final Set<String> reserved;

    public AliasPolicy(List<String> reserved) {
        this.reserved = reserved.stream().map(r -> r.toLowerCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
    }

    /** Returns the alias unchanged when valid; otherwise throws with the contract error code. */
    public String validate(String alias) {
        if (alias == null || !ALPHABET.matcher(alias).matches()) {
            throw new ApiException(ErrorCode.INVALID_ALIAS, "An alias may contain only letters, digits, hyphens, and underscores.");
        }
        if (alias.length() < MIN_LENGTH || alias.length() > MAX_LENGTH) {
            throw new ApiException(ErrorCode.INVALID_ALIAS, "An alias must be " + MIN_LENGTH + " to " + MAX_LENGTH + " characters long.");
        }
        if (reserved.contains(alias.toLowerCase(Locale.ROOT))) {
            throw new ApiException(ErrorCode.RESERVED_ALIAS, "The alias '" + alias + "' is reserved.");
        }
        return alias;
    }
}
