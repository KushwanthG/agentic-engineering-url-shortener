package com.agentic.urlshortener.shortener.domain;

import java.util.OptionalLong;

/**
 * Parses IPv4 literals in every form that {@code inet_aton} accepts (1 to 4 parts; each part decimal,
 * octal with a leading {@code 0}, or hexadecimal with {@code 0x}), so that notations such as
 * {@code 127.1}, {@code 2130706433}, {@code 0x7f000001}, or {@code 0177.0.0.1} cannot bypass the
 * address checks. Returns the 32-bit address, or empty when the host is not an IPv4 literal.
 */
public final class Ipv4LiteralParser {

    private Ipv4LiteralParser() {
    }

    public static OptionalLong parse(String host) {
        if (host.isEmpty() || host.endsWith(".")) {
            return OptionalLong.empty();
        }
        String[] parts = host.split("\\.", -1);
        if (parts.length > 4) {
            return OptionalLong.empty();
        }
        long[] values = new long[parts.length];
        for (int i = 0; i < parts.length; i++) {
            OptionalLong value = parsePart(parts[i]);
            if (value.isEmpty()) {
                return OptionalLong.empty();
            }
            values[i] = value.getAsLong();
        }
        // All leading parts are single bytes; the last part fills the remaining bytes.
        long address = 0;
        for (int i = 0; i < values.length - 1; i++) {
            if (values[i] > 0xFF) {
                return OptionalLong.empty();
            }
            address = (address << 8) | values[i];
        }
        int remainingBytes = 4 - (values.length - 1);
        long last = values[values.length - 1];
        if (last >= (1L << (8 * remainingBytes))) {
            return OptionalLong.empty();
        }
        return OptionalLong.of((address << (8 * remainingBytes)) | last);
    }

    private static OptionalLong parsePart(String part) {
        if (part.isEmpty()) {
            return OptionalLong.empty();
        }
        int radix = 10;
        String digits = part;
        if (part.length() > 2 && (part.startsWith("0x") || part.startsWith("0X"))) {
            radix = 16;
            digits = part.substring(2);
        } else if (part.length() > 1 && part.startsWith("0")) {
            radix = 8;
            digits = part.substring(1);
        }
        if (digits.length() > 11) {
            return OptionalLong.empty();
        }
        try {
            long value = Long.parseLong(digits, radix);
            return value <= 0xFFFFFFFFL ? OptionalLong.of(value) : OptionalLong.empty();
        } catch (NumberFormatException e) {
            return OptionalLong.empty();
        }
    }
}
