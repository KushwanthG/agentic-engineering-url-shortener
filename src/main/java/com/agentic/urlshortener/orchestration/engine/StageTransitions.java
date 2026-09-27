package com.agentic.urlshortener.orchestration.engine;

import static com.agentic.urlshortener.orchestration.domain.StageStatus.AWAITING_DECISION;
import static com.agentic.urlshortener.orchestration.domain.StageStatus.CANCELLED;
import static com.agentic.urlshortener.orchestration.domain.StageStatus.COMPENSATED;
import static com.agentic.urlshortener.orchestration.domain.StageStatus.FAILED;
import static com.agentic.urlshortener.orchestration.domain.StageStatus.PENDING;
import static com.agentic.urlshortener.orchestration.domain.StageStatus.READY;
import static com.agentic.urlshortener.orchestration.domain.StageStatus.REMOVED;
import static com.agentic.urlshortener.orchestration.domain.StageStatus.RETRY_WAIT;
import static com.agentic.urlshortener.orchestration.domain.StageStatus.RUNNING;
import static com.agentic.urlshortener.orchestration.domain.StageStatus.SKIPPED;
import static com.agentic.urlshortener.orchestration.domain.StageStatus.SUCCEEDED;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import com.agentic.urlshortener.orchestration.domain.StageStatus;

/**
 * Stage state machine of data-model.md. {@code PENDING -> FAILED} covers a failed entry criterion
 * (plan.md §3 scheduling algorithm). Terminal node states have no exit.
 */
public final class StageTransitions {

    private static final Map<StageStatus, Set<StageStatus>> ALLOWED = new EnumMap<>(StageStatus.class);

    static {
        ALLOWED.put(PENDING, EnumSet.of(READY, SKIPPED, CANCELLED, REMOVED, FAILED));
        ALLOWED.put(READY, EnumSet.of(RUNNING, SUCCEEDED, AWAITING_DECISION, CANCELLED));
        ALLOWED.put(RUNNING, EnumSet.of(SUCCEEDED, RETRY_WAIT, RUNNING, FAILED, AWAITING_DECISION, PENDING, CANCELLED));
        ALLOWED.put(RETRY_WAIT, EnumSet.of(RUNNING, PENDING, CANCELLED));
        ALLOWED.put(AWAITING_DECISION, EnumSet.of(SUCCEEDED, READY, FAILED, PENDING, CANCELLED));
        ALLOWED.put(SUCCEEDED, EnumSet.of(PENDING, COMPENSATED));
        ALLOWED.put(SKIPPED, EnumSet.of(PENDING));
        ALLOWED.put(FAILED, EnumSet.noneOf(StageStatus.class));
        ALLOWED.put(COMPENSATED, EnumSet.noneOf(StageStatus.class));
        ALLOWED.put(CANCELLED, EnumSet.noneOf(StageStatus.class));
        ALLOWED.put(REMOVED, EnumSet.noneOf(StageStatus.class));
    }

    private StageTransitions() {
    }

    public static boolean isAllowed(StageStatus from, StageStatus to) {
        return ALLOWED.get(from).contains(to);
    }

    public static void check(StageStatus from, StageStatus to) {
        if (!isAllowed(from, to)) {
            throw new IllegalTransitionException("stage", from, to);
        }
    }
}
