package com.agentic.urlshortener.shortener.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import com.agentic.urlshortener.common.exception.ApiException;
import com.agentic.urlshortener.common.exception.ErrorCode;
import com.agentic.urlshortener.shortener.dto.CreateLinkCommand;
import com.agentic.urlshortener.shortener.dto.CreatedLink;
import com.agentic.urlshortener.shortener.repository.ShortLinkRepository;
import com.agentic.urlshortener.support.ControllableTestConfig;
import com.agentic.urlshortener.support.IntegrationTest;
import com.agentic.urlshortener.support.MutableClock;

/** T023: per-consumer idempotency keys make retries safe, including concurrent duplicates (FR-LNK-08/09). */
@IntegrationTest
@Import(ControllableTestConfig.class)
@Tag("FR-LNK-08")
@Tag("FR-LNK-09")
class IdempotencyServiceTest {

    @Autowired
    private LinkCreationService service;

    @Autowired
    private ShortLinkRepository links;

    @Autowired
    private MutableClock clock;

    @BeforeEach
    void reset() {
        clock.set(Instant.parse("2026-09-26T10:00:00Z"));
    }

    private static CreateLinkCommand command(String url, String key) {
        return new CreateLinkCommand(url, null, null, null, "demo-consumer", key);
    }

    @Test
    void identicalRequestWithTheSameKeyReplaysTheOriginalLink() {
        String key = "key-" + UUID.randomUUID();
        CreatedLink first = service.create(command("https://example.com/idem", key));
        long count = links.count();
        CreatedLink second = service.create(command("https://example.com/idem", key));
        assertThat(second.replayed()).isTrue();
        assertThat(second.view()).isEqualTo(first.view());
        assertThat(links.count()).isEqualTo(count);
    }

    @Test
    void theSameKeyWithADifferentPayloadIsRejected() {
        String key = "key-" + UUID.randomUUID();
        service.create(command("https://example.com/one", key));
        assertThatThrownBy(() -> service.create(command("https://example.com/two", key)))
                .extracting(e -> ((ApiException) e).code()).isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REUSED);
    }

    @Test
    void keysAreScopedPerConsumer() {
        String key = "key-" + UUID.randomUUID();
        CreatedLink a = service.create(new CreateLinkCommand("https://example.com/c", null, null, null, "consumer-a", key));
        CreatedLink b = service.create(new CreateLinkCommand("https://example.com/c", null, null, null, "consumer-b", key));
        assertThat(a.view().code()).isNotEqualTo(b.view().code());
    }

    @Test
    void malformedKeysAreRejected() {
        assertThatThrownBy(() -> service.create(command("https://example.com/", "bad key!")))
                .extracting(e -> ((ApiException) e).code()).isEqualTo(ErrorCode.INVALID_IDEMPOTENCY_KEY);
        assertThatThrownBy(() -> service.create(command("https://example.com/", "k".repeat(129))))
                .extracting(e -> ((ApiException) e).code()).isEqualTo(ErrorCode.INVALID_IDEMPOTENCY_KEY);
    }

    @Test
    void anExpiredKeyIsReplacedByANewRecord() {
        String key = "key-" + UUID.randomUUID();
        CreatedLink first = service.create(command("https://example.com/old", key));
        clock.advance(Duration.ofHours(25));
        CreatedLink second = service.create(command("https://example.com/new", key));
        assertThat(second.replayed()).isFalse();
        assertThat(second.view().code()).isNotEqualTo(first.view().code());
    }

    @Test
    void concurrentIdenticalRequestsCreateExactlyOneLink() throws Exception {
        String key = "key-" + UUID.randomUUID();
        long before = links.count();
        ExecutorService pool = Executors.newFixedThreadPool(10);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<CreatedLink>> futures = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                return service.create(command("https://example.com/race", key));
            }));
        }
        start.countDown();
        List<CreatedLink> results = new ArrayList<>();
        for (Future<CreatedLink> future : futures) {
            results.add(future.get(60, TimeUnit.SECONDS));
        }
        pool.shutdown();

        Set<String> codes = results.stream().map(r -> r.view().code()).collect(Collectors.toSet());
        assertThat(codes).hasSize(1);
        assertThat(links.count()).isEqualTo(before + 1);
        assertThat(results.stream().filter(r -> !r.replayed()).count()).isEqualTo(1);
    }
}
