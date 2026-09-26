package com.agentic.urlshortener.shortener.service;

import java.time.Instant;

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

    private final ShortLinkRepository links;
    private final ClickEventRepository clicks;

    public ClickRecorder(ShortLinkRepository links, ClickEventRepository clicks) {
        this.links = links;
        this.clicks = clicks;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(long linkId, Instant occurredAt, String referrerHost) {
        links.incrementClicks(linkId, occurredAt);
        clicks.save(new ClickEvent(linkId, occurredAt, referrerHost));
    }
}
