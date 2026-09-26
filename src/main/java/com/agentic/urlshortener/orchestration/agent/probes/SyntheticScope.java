package com.agentic.urlshortener.orchestration.agent.probes;

import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Mutual exclusion of the probe-and-cleanup sections of one run. Synthetic links are owned by the run
 * and removed by run (ADR-010), so when parallel stages (TESTING ‖ SECURITY_VERIFICATION) both probe,
 * one stage's cleanup would delete the other's in-flight probe data, and run-scoped aliases would
 * collide. The stages still dispatch and run concurrently; only this section is exclusive per run.
 * Locks are striped (bounded memory); unrelated runs rarely share a stripe and at worst wait briefly.
 */
public final class SyntheticScope {

    private static final ReentrantLock[] STRIPES = new ReentrantLock[64];

    static {
        for (int i = 0; i < STRIPES.length; i++) {
            STRIPES[i] = new ReentrantLock();
        }
    }

    private SyntheticScope() {
    }

    /** Runs {@code section} while holding the run's synthetic-data scope. */
    public static <T> T exclusive(UUID runId, Supplier<T> section) {
        ReentrantLock lock = STRIPES[Math.floorMod(runId.hashCode(), STRIPES.length)];
        lock.lock();
        try {
            return section.get();
        } finally {
            lock.unlock();
        }
    }
}
