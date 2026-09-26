package com.agentic.urlshortener.shortener.domain;

import java.security.SecureRandom;

/**
 * Fixed-length base62 codes drawn from {@link SecureRandom}, so codes cannot be predicted from
 * previously issued ones (FR-LNK-06). Seven characters give 62^7 (about 3.5 * 10^12) codes (ADR-004).
 */
public class SecureRandomShortCodeGenerator implements ShortCodeGenerator {

    private static final char[] ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();

    private final SecureRandom random = new SecureRandom();
    private final int length;

    public SecureRandomShortCodeGenerator(int length) {
        if (length < 4 || length > 32) {
            throw new IllegalArgumentException("code length must be between 4 and 32");
        }
        this.length = length;
    }

    @Override
    public String next() {
        char[] code = new char[length];
        for (int i = 0; i < length; i++) {
            code[i] = ALPHABET[random.nextInt(ALPHABET.length)];
        }
        return new String(code);
    }
}
