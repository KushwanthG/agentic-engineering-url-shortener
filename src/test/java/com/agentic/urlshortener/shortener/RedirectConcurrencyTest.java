package com.agentic.urlshortener.shortener;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.agentic.urlshortener.shortener.dto.CreateLinkCommand;
import com.agentic.urlshortener.shortener.dto.Resolution;
import com.agentic.urlshortener.shortener.repository.ClickEventRepository;
import com.agentic.urlshortener.shortener.repository.ShortLinkRepository;
import com.agentic.urlshortener.shortener.service.LinkCreationService;
import com.agentic.urlshortener.shortener.service.RedirectService;
import com.agentic.urlshortener.support.IntegrationTest;

/** T031: under concurrent resolution the click count equals the number of successful redirects (SC-005). */
@IntegrationTest
@Tag("FR-ANL-05")
@Tag("SC-005")
class RedirectConcurrencyTest {

    private static final int RESOLUTIONS = 200;

    @Autowired
    private LinkCreationService creation;

    @Autowired
    private RedirectService redirects;

    @Autowired
    private ShortLinkRepository links;

    @Autowired
    private ClickEventRepository clicks;

    @Test
    void clickCountEqualsSuccessfulRedirects() throws Exception {
        String code = creation.create(new CreateLinkCommand("https://example.com/hot", null, null, null, "demo-consumer", null))
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
        for (Future<Resolution> future : futures) {
            if (future.get(60, TimeUnit.SECONDS).outcome() == Resolution.Outcome.REDIRECT) {
                redirected++;
            }
        }
        pool.shutdown();

        var link = links.findByCode(code).orElseThrow();
        assertThat(redirected).isEqualTo(RESOLUTIONS);
        assertThat(link.getClickCount()).isEqualTo(redirected);
        assertThat(clicks.countByLinkId(link.getId())).isEqualTo(redirected);
    }
}
