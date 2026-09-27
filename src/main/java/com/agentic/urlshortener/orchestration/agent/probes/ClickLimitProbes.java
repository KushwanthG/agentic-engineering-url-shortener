package com.agentic.urlshortener.orchestration.agent.probes;

import static com.agentic.urlshortener.orchestration.agent.probes.AcceptanceProbe.pattern;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.agentic.urlshortener.orchestration.port.LinkSnapshot;
import com.agentic.urlshortener.orchestration.port.ProbeResponse;
import com.agentic.urlshortener.orchestration.port.SyntheticLinkSpec;

/**
 * Acceptance probes of the click-limit capability (BF-001 AC-1..AC-6), run against the live service
 * on synthetic links of the run. CL-P3 resolves concurrently; CL-P6 uses the port's synthetic
 * click-store outage, which affects only this run's synthetic links on the probing thread.
 */
final class ClickLimitProbes {

    private static final String CAPABILITY = "click-limit";
    private static final String TARGET = "https://www.example.com/limited";

    private ClickLimitProbes() {
    }

    static List<AcceptanceProbe> all() {
        return List.of(
                new AcceptanceProbe("CL-P1", CAPABILITY, "a link with a maximum of 2 clicks is created and reports its limit",
                        pattern("maximum of 2 clicks, then the link is created"), ClickLimitProbes::createdWithLimit),
                new AcceptanceProbe("CL-P2", CAPABILITY, "a 2-click link redirects twice, then resolves as expired",
                        pattern("resolved 3 times"), ClickLimitProbes::twoRedirectsThenExpired),
                new AcceptanceProbe("CL-P3", CAPABILITY, "exactly N of more than N concurrent resolutions redirect",
                        pattern("concurrently"), ClickLimitProbes::concurrentResolutions),
                new AcceptanceProbe("CL-P4", CAPABILITY, "a link without a limit keeps redirecting",
                        pattern("created before the capability was released"), ClickLimitProbes::unlimitedKeepsRedirecting),
                new AcceptanceProbe("CL-P5", CAPABILITY, "limits outside 1..1,000,000 are rejected with INVALID_CLICK_LIMIT",
                        pattern("INVALID_CLICK_LIMIT"), ClickLimitProbes::outOfRange),
                new AcceptanceProbe("CL-P6", CAPABILITY, "a limited link whose click cannot be recorded is refused",
                        pattern("cannot be recorded"), ClickLimitProbes::failsClosed));
    }

    private static ProbeResponse limited(ProbeContext context, long maxClicks) {
        return context.create(new SyntheticLinkSpec(TARGET, null, maxClicks, null));
    }

    private static ProbeOutcome createdWithLimit(ProbeContext context) {
        ProbeResponse created = limited(context, 2);
        Long limit = created.code() == null ? null : context.find(created.code()).map(LinkSnapshot::maxClicks).orElse(null);
        return ProbeOutcome.check(created.outcome() == ProbeResponse.Outcome.CREATED && Long.valueOf(2).equals(limit),
                "create maxClicks=2 -> " + created.outcome() + ", stored limit " + limit);
    }

    private static ProbeOutcome twoRedirectsThenExpired(ProbeContext context) {
        ProbeResponse created = limited(context, 2);
        List<ProbeResponse.Outcome> outcomes = List.of(context.resolve(created.code()).outcome(), context.resolve(created.code()).outcome(),
                context.resolve(created.code()).outcome());
        return ProbeOutcome.check(outcomes.equals(List.of(ProbeResponse.Outcome.REDIRECT, ProbeResponse.Outcome.REDIRECT,
                ProbeResponse.Outcome.EXPIRED)), "3 resolutions -> " + outcomes);
    }

    private static ProbeOutcome concurrentResolutions(ProbeContext context) {
        int limit = 3;
        int clients = 12;
        String code = limited(context, limit).code();
        ExecutorService pool = Executors.newFixedThreadPool(clients);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<ProbeResponse.Outcome>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < clients; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return context.resolve(code).outcome();
                }));
            }
            start.countDown();
            int redirected = 0;
            for (Future<ProbeResponse.Outcome> future : futures) {
                redirected += future.get(30, TimeUnit.SECONDS) == ProbeResponse.Outcome.REDIRECT ? 1 : 0;
            }
            long counted = context.find(code).map(LinkSnapshot::clickCount).orElse(-1L);
            return ProbeOutcome.check(redirected == limit && counted == limit,
                    clients + " concurrent resolutions of a " + limit + "-click link -> " + redirected + " redirects, click count "
                            + counted);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ProbeOutcome.fail("interrupted");
        } catch (Exception e) {
            return ProbeOutcome.fail("concurrent resolution failed: " + e.getClass().getSimpleName());
        } finally {
            pool.shutdownNow();
        }
    }

    private static ProbeOutcome unlimitedKeepsRedirecting(ProbeContext context) {
        ProbeResponse created = context.create(TARGET + "/unlimited", null);
        int redirected = 0;
        for (int i = 0; i < 4; i++) {
            redirected += context.resolve(created.code()).outcome() == ProbeResponse.Outcome.REDIRECT ? 1 : 0;
        }
        return ProbeOutcome.check(redirected == 4, "link without a limit: " + redirected + " of 4 resolutions redirected");
    }

    private static ProbeOutcome outOfRange(ProbeContext context) {
        String low = limited(context, 0).errorCode();
        String high = limited(context, 1_000_001).errorCode();
        ProbeResponse lowest = limited(context, 1);
        ProbeResponse highest = limited(context, 1_000_000);
        boolean passed = "INVALID_CLICK_LIMIT".equals(low) && "INVALID_CLICK_LIMIT".equals(high)
                && lowest.outcome() == ProbeResponse.Outcome.CREATED && highest.outcome() == ProbeResponse.Outcome.CREATED;
        return ProbeOutcome.check(passed, "0 -> " + low + ", 1000001 -> " + high + ", 1 -> " + lowest.outcome() + ", 1000000 -> "
                + highest.outcome());
    }

    private static ProbeOutcome failsClosed(ProbeContext context) {
        String code = limited(context, 5).code();
        ProbeResponse duringOutage = context.withSyntheticClickOutage(() -> context.resolve(code));
        ProbeResponse afterOutage = context.resolve(code);
        return ProbeOutcome.check(duringOutage.outcome() == ProbeResponse.Outcome.UNAVAILABLE
                        && afterOutage.outcome() == ProbeResponse.Outcome.REDIRECT,
                "click store unavailable -> " + duringOutage.outcome() + "; available again -> " + afterOutage.outcome());
    }
}
