package com.agentic.urlshortener.orchestration.agent.probes;

import static com.agentic.urlshortener.orchestration.agent.probes.AcceptanceProbe.pattern;

import java.util.List;

import com.agentic.urlshortener.orchestration.port.ProbeResponse;

/** Acceptance probes CA-P1..CA-P6 of the custom-alias capability, mapped to GF-001 AC-1..AC-6 by pattern. */
final class CustomAliasProbes {

    private static final String CAPABILITY = "custom-alias";

    private CustomAliasProbes() {
    }

    static List<AcceptanceProbe> all() {
        return List.of(
                new AcceptanceProbe("CA-P1", CAPABILITY, "a valid alias becomes the short code",
                        pattern("alias `?spring-sale`?.*then the link is created"), CustomAliasProbes::aliasBecomesCode),
                new AcceptanceProbe("CA-P2", CAPABILITY, "an alias in use is rejected with ALIAS_CONFLICT",
                        pattern("is in use|ALIAS_CONFLICT"), CustomAliasProbes::aliasInUseConflicts),
                new AcceptanceProbe("CA-P3", CAPABILITY, "an alias with other characters is rejected with INVALID_ALIAS",
                        pattern("characters other than"), c -> rejected(c, "bad alias!", "INVALID_ALIAS")),
                new AcceptanceProbe("CA-P4", CAPABILITY, "aliases shorter than 3 or longer than 32 are rejected with INVALID_ALIAS",
                        pattern("shorter than 3|longer than 32"), CustomAliasProbes::lengthBounds),
                new AcceptanceProbe("CA-P5", CAPABILITY, "a reserved alias is rejected with RESERVED_ALIAS",
                        pattern("reserved alias|RESERVED_ALIAS"), c -> rejected(c, "api", "RESERVED_ALIAS")),
                new AcceptanceProbe("CA-P6", CAPABILITY, "an aliased link redirects to its target",
                        pattern("alias is resolved|redirected to the target"), CustomAliasProbes::aliasResolves));
    }

    private static ProbeOutcome aliasBecomesCode(ProbeContext context) {
        String alias = context.alias("spring-sale");
        ProbeResponse response = context.create("https://www.example.com/spring", alias);
        return ProbeOutcome.check(response.outcome() == ProbeResponse.Outcome.CREATED && alias.equals(response.code()),
                "alias " + alias + " -> " + response.outcome() + " with code " + response.code());
    }

    private static ProbeOutcome aliasInUseConflicts(ProbeContext context) {
        String alias = context.alias("in-use");
        ProbeResponse first = context.create("https://www.example.com/first", alias);
        ProbeResponse second = context.create("https://www.example.com/second", alias);
        return ProbeOutcome.check(first.outcome() == ProbeResponse.Outcome.CREATED && "ALIAS_CONFLICT".equals(second.errorCode()),
                "first " + first.outcome() + ", second " + second.outcome() + " " + second.errorCode());
    }

    private static ProbeOutcome lengthBounds(ProbeContext context) {
        ProbeResponse tooShort = context.create("https://www.example.com", "ab");
        ProbeResponse tooLong = context.create("https://www.example.com", "a".repeat(33));
        return ProbeOutcome.check("INVALID_ALIAS".equals(tooShort.errorCode()) && "INVALID_ALIAS".equals(tooLong.errorCode()),
                "2 characters -> " + tooShort.errorCode() + ", 33 characters -> " + tooLong.errorCode());
    }

    private static ProbeOutcome aliasResolves(ProbeContext context) {
        String alias = context.alias("go");
        String target = "https://www.example.com/landing";
        ProbeResponse created = context.create(target, alias);
        ProbeResponse resolved = context.resolve(alias);
        return ProbeOutcome.check(created.outcome() == ProbeResponse.Outcome.CREATED
                        && resolved.outcome() == ProbeResponse.Outcome.REDIRECT && target.equals(resolved.targetUrl()),
                "resolve " + alias + " -> " + resolved.outcome() + " " + resolved.targetUrl());
    }

    static ProbeOutcome rejected(ProbeContext context, String alias, String expectedCode) {
        ProbeResponse response = context.create("https://www.example.com", alias);
        return ProbeOutcome.check(expectedCode.equals(response.errorCode()),
                "alias '" + alias + "' -> " + response.outcome() + " " + response.errorCode() + " (expected " + expectedCode + ")");
    }
}
