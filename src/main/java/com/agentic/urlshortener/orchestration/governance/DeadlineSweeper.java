package com.agentic.urlshortener.orchestration.governance;

import java.time.Clock;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.agentic.urlshortener.orchestration.domain.StageNode;
import com.agentic.urlshortener.orchestration.domain.StageStatus;
import com.agentic.urlshortener.orchestration.engine.RunCoordinator;
import com.agentic.urlshortener.orchestration.repository.StageNodeRepository;

/**
 * Finds decisions whose deadline has passed (FR-GOV-07, PVT-13) and escalates them: the gate is never
 * approved by default, the escalation is audited, and the run safe-stops. The interval is
 * {@code app.orchestration.deadline-sweep-interval} (default 5 s).
 */
@Component
public class DeadlineSweeper {

    private final StageNodeRepository nodes;
    private final RunCoordinator coordinator;
    private final Clock clock;

    public DeadlineSweeper(StageNodeRepository nodes, RunCoordinator coordinator, Clock clock) {
        this.nodes = nodes;
        this.coordinator = coordinator;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.orchestration.deadline-sweep-interval:PT5S}",
            initialDelayString = "${app.orchestration.deadline-sweep-interval:PT5S}")
    public void sweep() {
        for (StageNode node : nodes.findByStatusAndDecisionDeadlineBefore(StageStatus.AWAITING_DECISION, clock.instant())) {
            coordinator.escalateExpiredGate(node.getRunId(), node.getStageKey());
        }
    }
}
