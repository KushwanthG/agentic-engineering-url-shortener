package com.agentic.urlshortener.orchestration.engine;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

/**
 * One lock per run: scheduling decisions, attempt results, and human decisions of a run are applied
 * one at a time (plan.md §3). JPA optimistic locking remains the safety net. Valid for the single
 * process the H2 file database admits (ADR-003, ADR-006).
 */
@Component
public class RunLocks {

    private final Map<UUID, ReentrantLock> locks = new ConcurrentHashMap<>();

    public <T> T withLock(UUID runId, Supplier<T> action) {
        ReentrantLock lock = locks.computeIfAbsent(runId, id -> new ReentrantLock());
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }
}
