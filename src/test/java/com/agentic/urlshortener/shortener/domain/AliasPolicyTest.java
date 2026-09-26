package com.agentic.urlshortener.shortener.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.agentic.urlshortener.common.exception.ApiException;
import com.agentic.urlshortener.common.exception.ErrorCode;

/** T054: alias syntax (3-32 of letters, digits, hyphen, underscore) and reserved words (GF-001 AC-3..AC-5). */
@Tag("FR-CAP-02")
@Tag("SCN-A")
class AliasPolicyTest {

    private final AliasPolicy policy = new AliasPolicy(List.of("api", "actuator", "admin", "health", "error"));

    @Test
    void acceptsAValidAliasUnchanged() {
        assertThat(policy.validate("spring-sale")).isEqualTo("spring-sale");
        assertThat(policy.validate("Spring_Sale_2026")).isEqualTo("Spring_Sale_2026");
        assertThat(policy.validate("abc")).isEqualTo("abc");
        assertThat(policy.validate("a".repeat(32))).hasSize(32);
    }

    @ParameterizedTest
    @ValueSource(strings = {"spring sale", "spring.sale", "spring/sale", "sale!", "ümlaut", "a%20b"})
    void rejectsCharactersOutsideTheAlphabet(String alias) {
        assertThatThrownBy(() -> policy.validate(alias))
                .isInstanceOf(ApiException.class).extracting(e -> ((ApiException) e).code()).isEqualTo(ErrorCode.INVALID_ALIAS);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "ab", "abcdefghijklmnopqrstuvwxyz0123456"})
    void rejectsLengthsOutsideThreeToThirtyTwo(String alias) {
        assertThatThrownBy(() -> policy.validate(alias))
                .isInstanceOf(ApiException.class).extracting(e -> ((ApiException) e).code()).isEqualTo(ErrorCode.INVALID_ALIAS);
    }

    @ParameterizedTest
    @ValueSource(strings = {"api", "API", "actuator", "Admin"})
    void rejectsReservedAliasesIgnoringCase(String alias) {
        assertThatThrownBy(() -> policy.validate(alias))
                .isInstanceOf(ApiException.class).extracting(e -> ((ApiException) e).code()).isEqualTo(ErrorCode.RESERVED_ALIAS);
    }
}
