package com.agentic.urlshortener.orchestration.engine;

import static com.agentic.urlshortener.orchestration.domain.RunStatus.AWAITING_HUMAN;
import static com.agentic.urlshortener.orchestration.domain.RunStatus.COMPENSATING;
import static com.agentic.urlshortener.orchestration.domain.RunStatus.COMPLETED;
import static com.agentic.urlshortener.orchestration.domain.RunStatus.CREATED;
import static com.agentic.urlshortener.orchestration.domain.RunStatus.PAUSED;
import static com.agentic.urlshortener.orchestration.domain.RunStatus.REJECTED;
import static com.agentic.urlshortener.orchestration.domain.RunStatus.RUNNING;
import static com.agentic.urlshortener.orchestration.domain.RunStatus.SAFE_STOPPED;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import com.agentic.urlshortener.orchestration.domain.RunStatus;

/** Run state machine of data-model.md; terminal runs never transition (FR-RPL-05). */
public final class RunTransitions {

    private static final Map<RunStatus, Set<RunStatus>> ALLOWED = new EnumMap<>(RunStatus.class);

    static {
        ALLOWED.put(CREATED, EnumSet.of(RUNNING));
        ALLOWED.put(RUNNING, EnumSet.of(AWAITING_HUMAN, PAUSED, COMPENSATING, COMPLETED, SAFE_STOPPED));
        ALLOWED.put(AWAITING_HUMAN, EnumSet.of(RUNNING, PAUSED, COMPENSATING, SAFE_STOPPED, REJECTED));
        ALLOWED.put(PAUSED, EnumSet.of(RUNNING, COMPENSATING, SAFE_STOPPED));
        ALLOWED.put(COMPENSATING, EnumSet.of(SAFE_STOPPED, REJECTED));
        ALLOWED.put(COMPLETED, EnumSet.noneOf(RunStatus.class));
        ALLOWED.put(REJECTED, EnumSet.noneOf(RunStatus.class));
        ALLOWED.put(SAFE_STOPPED, EnumSet.noneOf(RunStatus.class));
    }

    private RunTransitions() {
    }

    public static boolean isAllowed(RunStatus from, RunStatus to) {
        return ALLOWED.get(from).contains(to);
    }

    public static void check(RunStatus from, RunStatus to) {
        if (!isAllowed(from, to)) {
            throw new IllegalTransitionException("run", from, to);
        }
    }
}
