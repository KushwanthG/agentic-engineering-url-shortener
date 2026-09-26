package com.agentic.urlshortener.shortener.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.agentic.urlshortener.common.exception.ApiException;
import com.agentic.urlshortener.common.exception.ErrorCode;
import com.agentic.urlshortener.shortener.config.ShortenerProperties;
import com.agentic.urlshortener.shortener.domain.ShortLink;
import com.agentic.urlshortener.shortener.dto.LinkStatsResponse;
import com.agentic.urlshortener.shortener.dto.LinkView;
import com.agentic.urlshortener.shortener.repository.ClickEventRepository;
import com.agentic.urlshortener.shortener.repository.ShortLinkRepository;

/** Link metadata and analytics (FR-LNK-11, FR-ANL-03). */
@Service
@Transactional(readOnly = true)
public class LinkQueryService {

    private final ShortLinkRepository links;
    private final ClickEventRepository clicks;
    private final ShortenerProperties properties;
    private final Clock clock;

    public LinkQueryService(ShortLinkRepository links, ClickEventRepository clicks, ShortenerProperties properties, Clock clock) {
        this.links = links;
        this.clicks = clicks;
        this.properties = properties;
        this.clock = clock;
    }

    public LinkView get(String code) {
        return LinkView.of(find(code), properties.baseUrl(), clock.instant());
    }

    /** Totals plus per-day counts for the current UTC day and the preceding {@code windowDays - 1} days. */
    public LinkStatsResponse stats(String code) {
        ShortLink link = find(code);
        int window = properties.analyticsWindowDays();
        Instant since = clock.instant().atZone(ZoneOffset.UTC).toLocalDate().minusDays(window - 1L)
                .atStartOfDay(ZoneOffset.UTC).toInstant();
        var daily = clicks.dailyCounts(link.getId(), since).stream()
                .map(d -> new LinkStatsResponse.Daily(d.date(), d.clicks()))
                .toList();
        return new LinkStatsResponse(link.getCode(), link.getClickCount(), link.getLastAccessedAt(), window, daily);
    }

    private ShortLink find(String code) {
        return links.findByCode(code)
                .orElseThrow(() -> new ApiException(ErrorCode.LINK_NOT_FOUND, "No short link exists for this code."));
    }
}
