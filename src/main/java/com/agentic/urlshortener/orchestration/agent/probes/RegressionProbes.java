package com.agentic.urlshortener.orchestration.agent.probes;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import com.agentic.urlshortener.orchestration.port.LinkSnapshot;
import com.agentic.urlshortener.orchestration.port.ProbeResponse;
import com.agentic.urlshortener.orchestration.port.SyntheticLinkSpec;

/**
 * Baseline regression probes (FR-ORC-14, NFR-CHG-02): behavior that must not change when a capability
 * changes existing behavior. They use no capability input themselves, so with the new capability
 * enabled they prove that consumers who do not use it are unaffected.
 */
final class RegressionProbes {

    static final String BASELINE = "baseline";
    private static final String TARGET = "https://www.example.com/regression";

    private RegressionProbes() {
    }

    static List<AcceptanceProbe> all() {
        return List.of(
                new AcceptanceProbe("R-P1", BASELINE, "a link is created with a generated 7-character code", null,
                        RegressionProbes::creation),
                new AcceptanceProbe("R-P2", BASELINE, "a link redirects to its target", null, RegressionProbes::redirect),
                new AcceptanceProbe("R-P3", BASELINE, "a future expiry is accepted and active; a past expiry is rejected", null,
                        RegressionProbes::expiry),
                new AcceptanceProbe("R-P4", BASELINE, "a link created without a limit stays unlimited", null, RegressionProbes::unlimited),
                new AcceptanceProbe("R-P5", BASELINE, "every redirect is counted in the link's statistics", null, RegressionProbes::stats),
                new AcceptanceProbe("R-P6", BASELINE, "a replay with the same idempotency key returns the same link", null,
                        RegressionProbes::idempotentReplay));
    }

    private static ProbeOutcome creation(ProbeContext context) {
        ProbeResponse created = context.create(TARGET + "/create", null);
        return ProbeOutcome.check(created.outcome() == ProbeResponse.Outcome.CREATED && created.code() != null
                && created.code().matches("[A-Za-z0-9]{7}"), "create -> " + created.outcome() + " code " + created.code());
    }

    private static ProbeOutcome redirect(ProbeContext context) {
        String target = TARGET + "/redirect";
        ProbeResponse created = context.create(target, null);
        ProbeResponse resolved = context.resolve(created.code());
        return ProbeOutcome.check(resolved.outcome() == ProbeResponse.Outcome.REDIRECT && target.equals(resolved.targetUrl()),
                "resolve " + created.code() + " -> " + resolved.outcome() + " " + resolved.targetUrl());
    }

    private static ProbeOutcome expiry(ProbeContext context) {
        ProbeResponse future = context.create(new SyntheticLinkSpec(TARGET + "/expiry", null, null,
                Instant.now().plus(1, ChronoUnit.HOURS)));
        Optional<LinkSnapshot> snapshot = future.code() == null ? Optional.empty() : context.find(future.code());
        ProbeResponse past = context.create(new SyntheticLinkSpec(TARGET + "/expired", null, null,
                Instant.now().minus(1, ChronoUnit.HOURS)));
        boolean passed = future.outcome() == ProbeResponse.Outcome.CREATED
                && snapshot.map(s -> "ACTIVE".equals(s.status())).orElse(false)
                && "INVALID_EXPIRY".equals(past.errorCode());
        return ProbeOutcome.check(passed, "future expiry -> " + future.outcome() + " " + snapshot.map(LinkSnapshot::status).orElse("?")
                + "; past expiry -> " + past.errorCode());
    }

    private static ProbeOutcome unlimited(ProbeContext context) {
        ProbeResponse created = context.create(TARGET + "/unlimited", null);
        int redirects = 0;
        for (int i = 0; i < 3; i++) {
            if (context.resolve(created.code()).outcome() == ProbeResponse.Outcome.REDIRECT) {
                redirects++;
            }
        }
        Optional<LinkSnapshot> snapshot = context.find(created.code());
        return ProbeOutcome.check(redirects == 3 && snapshot.map(s -> "ACTIVE".equals(s.status())).orElse(false),
                redirects + " of 3 resolutions redirected; status " + snapshot.map(LinkSnapshot::status).orElse("?"));
    }

    private static ProbeOutcome stats(ProbeContext context) {
        ProbeResponse created = context.create(TARGET + "/stats", null);
        context.resolve(created.code());
        context.resolve(created.code());
        long clicks = context.find(created.code()).map(LinkSnapshot::clickCount).orElse(-1L);
        return ProbeOutcome.check(clicks == 2, "2 redirects -> click count " + clicks);
    }

    private static ProbeOutcome idempotentReplay(ProbeContext context) {
        String key = context.alias("regression-replay");
        ProbeResponse first = context.create(new SyntheticLinkSpec(TARGET + "/replay", null, null, null, key));
        ProbeResponse replay = context.create(new SyntheticLinkSpec(TARGET + "/replay", null, null, null, key));
        ProbeResponse reused = context.create(new SyntheticLinkSpec(TARGET + "/other", null, null, null, key));
        boolean passed = first.code() != null && first.code().equals(replay.code()) && "IDEMPOTENCY_KEY_REUSED".equals(reused.errorCode());
        return ProbeOutcome.check(passed, "first " + first.code() + ", replay " + replay.code() + ", different body with the same key -> "
                + reused.errorCode());
    }
}
