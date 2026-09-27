package com.agentic.urlshortener.shortener;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.agentic.urlshortener.shortener.domain.Capability;
import com.agentic.urlshortener.shortener.dto.CreateLinkCommand;
import com.agentic.urlshortener.shortener.dto.Resolution;
import com.agentic.urlshortener.shortener.repository.ClickEventRepository;
import com.agentic.urlshortener.shortener.repository.ShortLinkRepository;
import com.agentic.urlshortener.shortener.service.CapabilityService;
import com.agentic.urlshortener.shortener.service.LinkCreationService;
import com.agentic.urlshortener.shortener.service.RedirectService;
import com.agentic.urlshortener.support.IntegrationTest;

/** T087 (FR-CAP-03, BF-001 AC-3, threat TH-CL-1): exactly N of more than N concurrent resolutions redirect. */
@IntegrationTest
@Tag("FR-CAP-03")
@Tag("SCN-B")
class ClickLimitConcurrencyTest {

    private static final int LIMIT = 5;
    private static final int RESOLUTIONS = 60;

    @Autowired private LinkCreationService creation;
    @Autowired private RedirectService redirects;
    @Autowired private CapabilityService capabilities;
    @Autowired private ShortLinkRepository links;
    @Autowired private ClickEventRepository clicks;

    @AfterEach
    void withdraw() {
        capabilities.setRelease(Capability.CLICK_LIMIT, false, Map.of(), "test", null, "test cleanup");
    }

    @Test
    void exactlyTheLimitOfConcurrentResolutionsRedirect() throws Exception {
        capabilities.setRelease(Capability.CLICK_LIMIT, true, Map.of(), "test", null, "test release");
        String code = creation.create(new CreateLinkCommand("https://example.com/race", null, null, (long) LIMIT, "demo-consumer", null))
                .view().code();

        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Resolution>> futures = new ArrayList<>();
        for (int i = 0; i < RESOLUTIONS; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                return redirects.resolve(code, null);
            }));
        }
        start.countDown();
        int redirected = 0;
        int expired = 0;
        for (Future<Resolution> future : futures) {
            Resolution.Outcome outcome = future.get(60, TimeUnit.SECONDS).outcome();
            redirected += outcome == Resolution.Outcome.REDIRECT ? 1 : 0;
            expired += outcome == Resolution.Outcome.EXPIRED ? 1 : 0;
        }
        pool.shutdown();

        var link = links.findByCode(code).orElseThrow();
        assertThat(redirected).isEqualTo(LIMIT);
        assertThat(expired).isEqualTo(RESOLUTIONS - LIMIT);
        assertThat(link.getClickCount()).isEqualTo(LIMIT);
        assertThat(clicks.countByLinkId(link.getId())).isEqualTo(LIMIT);
    }
}
