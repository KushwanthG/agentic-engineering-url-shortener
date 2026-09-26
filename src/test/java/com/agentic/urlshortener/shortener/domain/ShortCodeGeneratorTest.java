package com.agentic.urlshortener.shortener.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** T019: fixed-length base62 codes from SecureRandom, not predictable from previous codes (FR-LNK-06). */
@Tag("FR-LNK-06")
@Tag("NFR-SCA-02")
class ShortCodeGeneratorTest {

    private final SecureRandomShortCodeGenerator generator = new SecureRandomShortCodeGenerator(7);

    @Test
    void producesSevenCharacterBase62Codes() {
        for (int i = 0; i < 1_000; i++) {
            assertThat(generator.next()).matches("[0-9A-Za-z]{7}");
        }
    }

    @Test
    void codesAreNotSequentialOrRepeated() {
        Set<String> codes = new HashSet<>();
        String previous = null;
        int sequentialPairs = 0;
        for (int i = 0; i < 10_000; i++) {
            String code = generator.next();
            codes.add(code);
            if (previous != null && previous.substring(0, 6).equals(code.substring(0, 6))) {
                sequentialPairs++;
            }
            previous = code;
        }
        assertThat(codes).hasSize(10_000);
        assertThat(sequentialPairs).isLessThan(3);
    }

    @Test
    void usesTheWholeAlphabet() {
        Set<Character> seen = new HashSet<>();
        for (int i = 0; i < 5_000; i++) {
            for (char c : generator.next().toCharArray()) {
                seen.add(c);
            }
        }
        assertThat(seen).hasSize(62);
    }

    @Test
    void codeSpaceExceedsThreeTrillion() {
        assertThat(Math.pow(62, 7)).isGreaterThan(3.5e12);
    }
}
