package com.agentic.urlshortener.shortener.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.agentic.urlshortener.common.exception.ApiException;
import com.agentic.urlshortener.common.exception.ErrorCode;
import com.agentic.urlshortener.support.MaliciousUrlCatalog;

/**
 * T017: the security URL catalog (PVT-15: at least 25 malicious or invalid patterns) and the
 * normalization rules of FR-LNK-02..04. No DNS resolution is involved (research R-07).
 */
@Tag("FR-LNK-02")
@Tag("FR-LNK-03")
@Tag("FR-LNK-04")
@Tag("NFR-SEC-01")
class UrlPolicyTest {

    private final UrlPolicy policy = new UrlPolicy(2048, Set.of("sho.rt"));

    static Stream<Arguments> maliciousOrInvalid() {
        return MaliciousUrlCatalog.entries().stream().map(e -> Arguments.of(e.url(), e.code()));
    }

    @ParameterizedTest(name = "[{index}] {0} -> {1}")
    @MethodSource("maliciousOrInvalid")
    void rejectsMaliciousAndInvalidUrls(String url, ErrorCode expected) {
        assertThatThrownBy(() -> policy.validate(url))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).code())
                .isEqualTo(expected);
    }

    @Test
    void catalogCoversAtLeastTwentyFivePatterns() {
        assertThat(maliciousOrInvalid().count()).isGreaterThanOrEqualTo(25);
    }

    @Test
    void acceptsAndNormalizesSchemeHostAndDefaultPort() {
        assertThat(policy.validate("HTTPS://WWW.Example.COM:443/Path?q=A#Frag").value())
                .isEqualTo("https://www.example.com/Path?q=A#Frag");
        assertThat(policy.validate("http://example.com:80/x").value()).isEqualTo("http://example.com/x");
        assertThat(policy.validate("http://example.com:8080/x").value()).isEqualTo("http://example.com:8080/x");
    }

    @Test
    void preservesPathQueryAndFragmentExactly() {
        String url = "https://example.com/a%20b/c?x=1&y=%2F#section-2";
        assertThat(policy.validate(url).value()).isEqualTo(url);
    }

    @Test
    void convertsInternationalizedHostsToAscii() {
        assertThat(policy.validate("https://bücher.example/katalog").value()).isEqualTo("https://xn--bcher-kva.example/katalog");
    }

    @Test
    void acceptsAUrlExactlyAtTheMaximumLength() {
        String base = "https://example.com/";
        String url = base + "a".repeat(2048 - base.length());
        assertThat(url).hasSize(2048);
        assertThat(policy.validate(url).value()).isEqualTo(url);
        assertThatThrownBy(() -> policy.validate(url + "b"))
                .extracting(e -> ((ApiException) e).code()).isEqualTo(ErrorCode.URL_TOO_LONG);
    }

    @Test
    void acceptsPublicAddressesAndHostnames() {
        for (String url : List.of("https://example.com", "http://93.184.216.34/", "https://sub.domain.example.org/x",
                "http://[2001:db8::1]/" /* documentation range is not blocked */)) {
            assertThat(policy.validate(url).value()).startsWith(url.substring(0, url.indexOf(':')));
        }
    }

    @Test
    void trimsSurroundingWhitespace() {
        assertThat(policy.validate("  https://example.com/x  ").value()).isEqualTo("https://example.com/x");
    }
}
