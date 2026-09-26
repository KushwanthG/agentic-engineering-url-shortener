package com.agentic.urlshortener.orchestration.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.agentic.urlshortener.orchestration.domain.ActorType;
import com.agentic.urlshortener.support.IntegrationTest;
import com.agentic.urlshortener.orchestration.domain.AuditEvent;
import com.agentic.urlshortener.orchestration.domain.AuditRecord;

/** T012: append-only, per-chain SHA-256 hash chain with contiguous sequence numbers (FR-AUD-01/02). */
@IntegrationTest
@Tag("FR-AUD-01")
@Tag("FR-AUD-02")
class AuditChainTest {

    private static final String GENESIS = "0".repeat(64);

    @Autowired
    private AuditService audit;

    @Test
    void appendsContiguousLinkedEventsPerRun() {
        UUID run = UUID.randomUUID();
        AuditEvent first = audit.append(record(run, "RUN_CREATED"));
        AuditEvent second = audit.append(record(run, "STAGE_TRANSITION"));
        AuditEvent third = audit.append(record(run, "RUN_TERMINATED"));

        assertThat(List.of(first.getSeq(), second.getSeq(), third.getSeq())).containsExactly(1L, 2L, 3L);
        assertThat(first.getChainId()).isEqualTo(run.toString());
        assertThat(first.getPrevHash()).isEqualTo(GENESIS);
        assertThat(second.getPrevHash()).isEqualTo(first.getHash());
        assertThat(third.getPrevHash()).isEqualTo(second.getHash());
        assertThat(first.getHash()).isEqualTo(AuditHashing.hash(first)).hasSize(64);

        AuditVerification verification = audit.verify(run.toString());
        assertThat(verification.valid()).isTrue();
        assertThat(verification.eventsChecked()).isEqualTo(3);
        assertThat(verification.firstBrokenSeq()).isNull();
    }

    @Test
    void chainsAreIndependent() {
        UUID runA = UUID.randomUUID();
        UUID runB = UUID.randomUUID();
        audit.append(record(runA, "A1"));
        audit.append(record(runA, "A2"));
        AuditEvent b1 = audit.append(record(runB, "B1"));
        assertThat(b1.getSeq()).isEqualTo(1L);
        assertThat(b1.getPrevHash()).isEqualTo(GENESIS);
    }

    @Test
    void eventsWithoutARunGoToTheGlobalChain() {
        AuditEvent event = audit.append(new AuditRecord(null, ActorType.SYSTEM, "capability-service", "CAPABILITY_CHANGED",
                "capability:custom-alias", "UNRELEASED", "RELEASED", "OK", "test", null));
        assertThat(event.getChainId()).isEqualTo(AuditService.GLOBAL_CHAIN);
        assertThat(audit.verify(AuditService.GLOBAL_CHAIN).valid()).isTrue();
    }

    // Repeated on fresh chains: the first appends race to create the chain head (regression guard).
    @RepeatedTest(10)
    void concurrentAppendsKeepTheChainContiguousAndValid() throws Exception {
        UUID run = UUID.randomUUID();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int t = 0; t < 8; t++) {
            futures.add(pool.submit(() -> {
                start.await();
                for (int i = 0; i < 10; i++) {
                    audit.append(record(run, "CONCURRENT"));
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> future : futures) {
            future.get(60, TimeUnit.SECONDS);
        }
        pool.shutdown();

        List<AuditEvent> events = audit.chain(run.toString());
        assertThat(events).hasSize(80);
        assertThat(events).extracting(AuditEvent::getSeq).containsExactlyElementsOf(
                java.util.stream.LongStream.rangeClosed(1, 80).boxed().toList());
        assertThat(audit.verify(run.toString()).valid()).isTrue();
    }

    private static AuditRecord record(UUID run, String action) {
        return new AuditRecord(run, ActorType.SYSTEM, "test", action, "run:" + run, null, null, "OK", "unit test", "{\"k\":1}");
    }
}
