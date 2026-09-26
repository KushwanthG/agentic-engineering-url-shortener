package com.agentic.urlshortener.orchestration.agent;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Splits an acceptance criterion into Given / When / Then. A criterion is testable when it names at
 * least a precondition (Given) and an observable outcome (Then); When is optional.
 */
public final class AcceptanceCriterionParser {

    private static final Pattern GIVEN_WHEN_THEN = Pattern.compile(
            "^\\s*given\\s+(.+?),\\s*(?:when\\s+(.+?),\\s*)?then\\s+(.+?)\\.?\\s*$", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /** A parsed criterion; the parts are null when the text does not follow the pattern. */
    public record Parsed(String id, String text, String given, String when, String then, boolean testable) {
    }

    private AcceptanceCriterionParser() {
    }

    public static Parsed parse(String id, String text) {
        Matcher matcher = GIVEN_WHEN_THEN.matcher(text);
        if (!matcher.matches()) {
            return new Parsed(id, text, null, null, null, false);
        }
        return new Parsed(id, text, matcher.group(1).strip(), matcher.group(2) == null ? null : matcher.group(2).strip(),
                matcher.group(3).strip(), true);
    }
}
