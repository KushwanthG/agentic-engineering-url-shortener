package com.agentic.urlshortener.orchestration.agent.probes;

import static com.agentic.urlshortener.orchestration.agent.probes.AcceptanceProbe.pattern;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import com.agentic.urlshortener.orchestration.port.LinkSnapshot;
import com.agentic.urlshortener.orchestration.port.ProbeResponse;
import com.agentic.urlshortener.orchestration.port.SyntheticLinkSpec;

/**
 * Acceptance probes of the default-expiry capability (SCN-C criteria derived from decisions D1 and D4).
 * The expected number of days is read from the stored link itself: the probe checks that the default
 * equals the released parameter, which the release plan (not the probe) fixes. DE-P3 creates its
 * "before the release" link on a separate thread, where the capability preview of the probing thread
 * does not apply, exactly as for a link created before the release.
 */
final class DefaultExpiryProbes {

    private static final String CAPABILITY = "default-expiry";
    private static final String TARGET = "https://www.example.com/expiring";
    private static final Duration TOLERANCE = Duration.ofMinutes(1);

    private DefaultExpiryProbes() {
    }

    static List<AcceptanceProbe> all() {
        return List.of(
                new AcceptanceProbe("DE-P1", CAPABILITY, "a link created without an expiry receives the released default expiry",
                        pattern("without an explicit expiry"), DefaultExpiryProbes::defaultApplied),
                new AcceptanceProbe("DE-P2", CAPABILITY, "an explicit expiry is kept", pattern("explicit expiry is kept"),
                        DefaultExpiryProbes::explicitKept),
                new AcceptanceProbe("DE-P3", CAPABILITY, "a link created before the release keeps its original expiry",
                        pattern("keeps its original expiry"), DefaultExpiryProbes::earlierLinkUnchanged));
    }

    private static ProbeOutcome defaultApplied(ProbeContext context) {
        Optional<Object> decided = context.parameter("defaultExpiryDays");
        if (decided.isEmpty() || !(decided.get() instanceof Number days)) {
            return ProbeOutcome.fail("no default expiry was decided for the release (parameter defaultExpiryDays missing)");
        }
        Instant expected = Instant.now().plus(days.longValue(), ChronoUnit.DAYS);
        ProbeResponse created = context.create(TARGET, null);
        Optional<Instant> expiresAt = context.find(created.code()).map(LinkSnapshot::expiresAt);
        boolean passed = expiresAt.map(e -> Duration.between(expected, e).abs().compareTo(TOLERANCE) <= 0).orElse(false);
        return ProbeOutcome.check(passed, "link without an expiry expires at " + expiresAt.orElse(null) + "; expected creation + "
                + days + " days");
    }

    private static ProbeOutcome explicitKept(ProbeContext context) {
        Instant explicit = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        ProbeResponse created = context.create(new SyntheticLinkSpec(TARGET + "/explicit", null, null, explicit));
        Optional<Instant> stored = context.find(created.code()).map(LinkSnapshot::expiresAt);
        return ProbeOutcome.check(stored.map(explicit::equals).orElse(false),
                "explicit expiry " + explicit + " -> stored " + stored.orElse(null));
    }

    private static ProbeOutcome earlierLinkUnchanged(ProbeContext context) {
        try {
            ProbeResponse earlier = CompletableFuture.supplyAsync(() -> context.create(TARGET + "/earlier", null))
                    .get(30, TimeUnit.SECONDS);
            Optional<Instant> beforeResolve = context.find(earlier.code()).map(LinkSnapshot::expiresAt);
            ProbeResponse resolved = context.resolve(earlier.code());
            Optional<Instant> afterResolve = context.find(earlier.code()).map(LinkSnapshot::expiresAt);
            boolean passed = beforeResolve.isEmpty() && afterResolve.isEmpty() && resolved.outcome() == ProbeResponse.Outcome.REDIRECT;
            return ProbeOutcome.check(passed, "link created without the capability in effect: expiry " + beforeResolve.orElse(null)
                    + ", after resolving " + afterResolve.orElse(null) + " (" + resolved.outcome() + ")");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ProbeOutcome.fail("interrupted");
        } catch (Exception e) {
            return ProbeOutcome.fail("could not create the earlier link: " + e.getClass().getSimpleName());
        }
    }
}
