package com.agentic.urlshortener.shortener.service;

import java.time.Instant;
import java.util.function.Supplier;

import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.agentic.urlshortener.shortener.domain.ClickEvent;
import com.agentic.urlshortener.shortener.repository.ClickEventRepository;
import com.agentic.urlshortener.shortener.repository.ShortLinkRepository;

/**
 * Records one click in its own transaction: an atomic counter update plus a click event with the
 * referrer host only (FR-ANL-01/02). Isolated so that a failure here cannot undo the redirect.
 */
@Component
public class ClickRecorder {

    /**
     * Probe-only simulation of an unavailable click store for synthetic links on the calling thread,
     * so the fail-closed rule of click-limited links (BF-001 AC-6) can be verified against the running
     * service. It never affects consumer links or other threads.
     */
    private static final ThreadLocal<Boolean> SYNTHETIC_OUTAGE = ThreadLocal.withInitial(() -> false);

    private final ShortLinkRepository links;
    private final ClickEventRepository clicks;

    public ClickRecorder(ShortLinkRepository links, ClickEventRepository clicks) {
        this.links = links;
        this.clicks = clicks;
    }

    /** Runs {@code action} with the click store unavailable for synthetic links on this thread. */
    public static <T> T withSyntheticOutage(Supplier<T> action) {
        SYNTHETIC_OUTAGE.set(true);
        try {
            return action.get();
        } finally {
            SYNTHETIC_OUTAGE.remove();
        }
    }

    /**
     * Counts a click of a click-limited link only while it is below its limit (atomic conditional
     * update). Returns false when the link is exhausted; throws when the click cannot be recorded, which
     * the caller turns into a refused redirect (fail closed).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean recordWithinLimit(long linkId, boolean synthetic, Instant occurredAt, String referrerHost) {
        if (synthetic && SYNTHETIC_OUTAGE.get()) {
            throw new DataAccessResourceFailureException("simulated click-store outage (probe on a synthetic link)");
        }
        if (links.incrementClicksWithinLimit(linkId, occurredAt) == 0) {
            return false;
        }
        clicks.save(new ClickEvent(linkId, occurredAt, referrerHost));
        return true;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(long linkId, Instant occurredAt, String referrerHost) {
        links.incrementClicks(linkId, occurredAt);
        clicks.save(new ClickEvent(linkId, occurredAt, referrerHost));
    }
}
