package com.agentic.urlshortener.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** SHA-256 fingerprints (lower-case hex) of text, canonical JSON, and values. */
public final class Fingerprints {

    private Fingerprints() {
    }

    public static String sha256(String text) {
        return HexFormat.of().formatHex(digest(text.getBytes(StandardCharsets.UTF_8)));
    }

    public static byte[] digest(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", e);
        }
    }

    /** Fingerprint of a JSON document's canonical form. */
    public static String ofJson(String json) {
        return sha256(CanonicalJson.canonicalize(json));
    }

    /** Fingerprint of a value's canonical JSON form. */
    public static String ofValue(Object value) {
        return sha256(CanonicalJson.write(value));
    }
}
