package com.agentic.urlshortener.orchestration.agent.probes;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Parallel probe stages of one run (TESTING ‖ SECURITY_VERIFICATION) share the run's synthetic-data
 * scope, whose cleanup deletes by run (ADR-010); their probe-and-cleanup sections must not interleave.
 */
@Tag("FR-ORC-16")
@Tag("FR-ORC-04")
class SyntheticScopeTest {

    @Test
    void sectionsOfTheSameRunNeverOverlap() throws Exception {
        UUID runId = UUID.randomUUID();
        AtomicInteger inside = new AtomicInteger();
        AtomicInteger maxInside = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            Future<?>[] futures = new Future<?>[4];
            for (int i = 0; i < futures.length; i++) {
                futures[i] = pool.submit(() -> {
                    start.await();
                    return SyntheticScope.exclusive(runId, () -> {
                        maxInside.accumulateAndGet(inside.incrementAndGet(), Math::max);
                        LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(50));
                        inside.decrementAndGet();
                        return null;
                    });
                });
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(maxInside.get()).isEqualTo(1);
    }

    @Test
    void theSectionResultAndExceptionsPassThrough() throws Exception {
        UUID runId = UUID.randomUUID();
        assertThat(SyntheticScope.exclusive(runId, () -> 42)).isEqualTo(42);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> SyntheticScope.exclusive(runId, () -> {
            throw new IllegalStateException("probe blew up");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(SyntheticScope.exclusive(runId, () -> "lock released")).isEqualTo("lock released");
    }
}
