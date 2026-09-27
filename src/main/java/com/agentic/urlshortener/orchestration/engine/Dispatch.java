package com.agentic.urlshortener.orchestration.engine;

import java.time.Duration;

import com.agentic.urlshortener.orchestration.agent.StageAgent;
import com.agentic.urlshortener.orchestration.agent.StageContext;

/**
 * One persisted attempt handed to the dispatcher after commit: the agent, its context, the
 * {@code (generation, attemptNo)} pair that identifies it, the scheduling cycle that started it, and
 * its timeout.
 */
public record Dispatch(StageAgent agent, StageContext context, int generation, int attemptNo, long schedulingCycle,
        Duration timeout) {
}
