package com.agentic.urlshortener.orchestration.domain;

/** Text helpers for bounded persisted columns. */
public final class Texts {

    private Texts() {
    }

    /** Returns {@code text} cut to at most {@code max} characters (marked with "..."), or null for null. */
    public static String truncate(String text, int max) {
        if (text == null || text.length() <= max) {
            return text;
        }
        return text.substring(0, max - 3) + "...";
    }
}
