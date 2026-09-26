package com.agentic.urlshortener.orchestration.governance;

import static com.agentic.urlshortener.orchestration.domain.StageType.ARCHITECTURE_APPROVAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import com.agentic.urlshortener.orchestration.agent.ArtifactDraft;
import com.agentic.urlshortener.orchestration.agent.StageResult;
import com.agentic.urlshortener.orchestration.audit.AuditService;
import com.agentic.urlshortener.orchestration.domain.RunStatus;
import com.agentic.urlshortener.orchestration.domain.StageStatus;
import com.agentic.urlshortener.orchestration.domain.StageType;
import com.agentic.urlshortener.orchestration.domain.WorkflowRun;
import com.agentic.urlshortener.orchestration.dto.RequirementSubmission;
import com.agentic.urlshortener.orchestration.repository.DecisionRepository;
import com.agentic.urlshortener.orchestration.repository.StageNodeRepository;
import com.agentic.urlshortener.orchestration.repository.WorkflowRunRepository;
import com.agentic.urlshortener.orchestration.service.WorkflowService;
import com.agentic.urlshortener.support.GovernanceHarness;
import com.agentic.urlshortener.support.IntegrationTest;
import com.agentic.urlshortener.support.ScriptedAgent;
import com.agentic.urlshortener.support.Tokens;

/**
 * T061 (FR-GOV-07, RDR-04): a gate without a decision by its deadline is never approved; the run
 * safe-stops with an escalation, and a late decision is refused. The simulated 1-second deadline is
 * demonstration input (fault injection enabled in the test profile).
 */
@IntegrationTest
@Import(ScriptedAgent.Config.class)
@Tag("FR-GOV-07")
@Tag("FR-REL-06")
@Tag("RDR-04")
class GateDeadlineTest {

    @Autowired private MockMvc mvc;
    @Autowired private WorkflowService workflows;
    @Autowired private WorkflowRunRepository runs;
    @Autowired private StageNodeRepository nodes;
    @Autowired private DecisionRepository decisions;
    @Autowired private DeadlineSweeper sweeper;
    @Autowired private AuditService audit;
    @Autowired private ScriptedAgent.Scripts scripts;

    private GovernanceHarness harness;

    @BeforeEach
    void materialDesign() {
        harness = new GovernanceHarness(mvc, workflows, runs, nodes);
        scripts.reset();
        scripts.set(StageType.DESIGN, context -> new StageResult.Succeeded(List.of(ArtifactDraft.json("DESIGN",
                "{\"materialChange\":true,\"materialReasons\":[\"public API change\"]}")), "scripted material design"));
    }

    private UUID gateWithPassedDeadline() {
        UUID runId = harness.submit(GovernanceHarness.ALICE, new RequirementSubmission.SimulationOptions(1, List.of()));
        harness.awaitGate(runId, ARCHITECTURE_APPROVAL);
        Instant deadline = harness.node(runId, ARCHITECTURE_APPROVAL).getDecisionDeadline();
        await().atMost(Duration.ofSeconds(5)).until(() -> Instant.now().isAfter(deadline));
        return runId;
    }

    @Test
    void anExpiredGateSafeStopsTheRunWithAnEscalationAndIsNeverApproved() {
        UUID runId = gateWithPassedDeadline();

        sweeper.sweep();

        WorkflowRun run = runs.findById(runId).orElseThrow();
        assertThat(run.getStatus()).isEqualTo(RunStatus.SAFE_STOPPED);
        assertThat(run.getTerminalReason()).contains("deadline").contains("ARCHITECTURE_APPROVAL");
        assertThat(harness.node(runId, ARCHITECTURE_APPROVAL).getStatus()).isEqualTo(StageStatus.CANCELLED);
        assertThat(harness.node(runId, StageType.IMPLEMENTATION).getStatus()).isEqualTo(StageStatus.CANCELLED);
        assertThat(decisions.findByRunIdOrderByCreatedAtAsc(runId)).noneMatch(d -> "APPROVED".equals(d.getOutcome()));
        assertThat(audit.chain(runId.toString())).anyMatch(e -> e.getAction().equals("GATE_ESCALATED")
                && e.getTarget().equals("ARCHITECTURE_APPROVAL"));
    }

    @Test
    void aDecisionAfterTheDeadlineIsRefused() throws Exception {
        UUID runId = gateWithPassedDeadline();

        harness.decide(runId, ARCHITECTURE_APPROVAL, Tokens.APPROVER, "APPROVE")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DEADLINE_PASSED"));

        assertThat(harness.node(runId, ARCHITECTURE_APPROVAL).getStatus()).isNotEqualTo(StageStatus.SUCCEEDED);
        assertThat(decisions.findByRunIdOrderByCreatedAtAsc(runId)).isEmpty();
    }

    @Test
    void aGateWithinItsDeadlineIsLeftAlone() {
        UUID runId = harness.submit(GovernanceHarness.ALICE);
        harness.awaitGate(runId, ARCHITECTURE_APPROVAL);

        sweeper.sweep();

        assertThat(runs.findById(runId).orElseThrow().getStatus()).isEqualTo(RunStatus.AWAITING_HUMAN);
    }
}
