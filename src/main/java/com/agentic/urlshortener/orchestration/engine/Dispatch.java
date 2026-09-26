package com.agentic.urlshortener.orchestration.engine;

import com.agentic.urlshortener.orchestration.agent.StageAgent;
import com.agentic.urlshortener.orchestration.agent.StageContext;

/**
 * An attempt ready to run: identified by (run, stage, generation, attempt number) so that a late or
 * duplicate result can be recognized and discarded (FR-REL-09). The context was assembled inside the
 * scheduling transaction; the agent runs outside it.
 */
public record Dispatch(StageAgent agent, StageContext context, int generation, int attemptNo, long schedulingCycle) {
}
