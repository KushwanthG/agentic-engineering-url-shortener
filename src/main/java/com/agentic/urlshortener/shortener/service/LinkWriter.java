package com.agentic.urlshortener.shortener.service;

import java.util.function.Function;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.agentic.urlshortener.shortener.domain.IdempotencyRecord;
import com.agentic.urlshortener.shortener.domain.ShortLink;
import com.agentic.urlshortener.shortener.repository.IdempotencyRecordRepository;
import com.agentic.urlshortener.shortener.repository.ShortLinkRepository;

/**
 * Inserts a link, and its idempotency record when there is one, in one short transaction. A unique
 * violation rolls both back, so a failed attempt never leaves a partial link (FR-LNK-05).
 */
@Component
public class LinkWriter {

    private final ShortLinkRepository links;
    private final IdempotencyRecordRepository records;

    public LinkWriter(ShortLinkRepository links, IdempotencyRecordRepository records) {
        this.links = links;
        this.records = records;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ShortLink insert(ShortLink link, Function<ShortLink, IdempotencyRecord> recordFactory) {
        ShortLink saved = links.saveAndFlush(link);
        if (recordFactory != null) {
            records.saveAndFlush(recordFactory.apply(saved));
        }
        return saved;
    }
}
