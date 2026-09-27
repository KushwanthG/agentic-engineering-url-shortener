package com.agentic.urlshortener.orchestration.governance;

import static com.agentic.urlshortener.orchestration.domain.StageType.ARCHITECTURE_APPROVAL;
import static com.agentic.urlshortener.orchestration.domain.StageType.RELEASE_APPROVAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.agentic.urlshortener.orchestration.repository.DecisionRepository;
import com.agentic.urlshortener.orchestration.repository.StageNodeRepository;
import com.agentic.urlshortener.orchestration.repository.WorkflowRunRepository;
import com.agentic.urlshortener.orchestration.service.WorkflowService;
import com.agentic.urlshortener.support.GovernanceHarness;
import com.agentic.urlshortener.support.IntegrationTest;
import com.agentic.urlshortener.support.ScriptedAgent;
import com.agentic.urlshortener.support.Tokens;

/** T059 (FR-GOV-04): the requester of a run cannot approve its architecture or release gate, even holding the role. */
@IntegrationTest
@Import(ScriptedAgent.Config.class)
@Tag("FR-GOV-04")
class SeparationOfDutiesTest {

    @Autowired private MockMvc mvc;
    @Autowired private WorkflowService workflows;
    @Autowired private WorkflowRunRepository runs;
    @Autowired private StageNodeRepository nodes;
    @Autowired private DecisionRepository decisions;
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

    @Test
    void theRequesterCannotApproveTheArchitectureGateOfTheirOwnRun() throws Exception {
        UUID runId = harness.submit(GovernanceHarness.DAVE);
        harness.awaitGate(runId, ARCHITECTURE_APPROVAL);

        harness.decide(runId, ARCHITECTURE_APPROVAL, Tokens.DUAL_ROLE, "APPROVE")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SEPARATION_OF_DUTIES"));

        assertThat(harness.node(runId, ARCHITECTURE_APPROVAL).getStatus()).isEqualTo(StageStatus.AWAITING_DECISION);
        assertThat(decisions.findByRunIdOrderByCreatedAtAsc(runId)).isEmpty();
        assertThat(audit.chain(runId.toString())).anyMatch(e -> e.getAction().equals("DECISION_REFUSED")
                && e.getActorId().equals("dave") && e.getReason().contains("requester"));
    }

    @Test
    void theRequesterCannotApproveTheReleaseGateButAnotherReleaseOwnerCan() throws Exception {
        UUID runId = harness.submit(GovernanceHarness.DAVE);
        harness.awaitGate(runId, ARCHITECTURE_APPROVAL);
        harness.decide(runId, ARCHITECTURE_APPROVAL, Tokens.APPROVER, "APPROVE").andExpect(status().isOk());
        harness.awaitGate(runId, RELEASE_APPROVAL);

        harness.decide(runId, RELEASE_APPROVAL, Tokens.DUAL_ROLE, "APPROVE")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SEPARATION_OF_DUTIES"));
        harness.decide(runId, RELEASE_APPROVAL, Tokens.RELEASE_OWNER, "APPROVE")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actorId").value("carol"));
        harness.awaitStatus(runId, RunStatus.COMPLETED);
    }

    @Test
    void aDualRolePrincipalMayDecideRunsRequestedBySomeoneElse() throws Exception {
        UUID runId = harness.submit(GovernanceHarness.ALICE);
        harness.awaitGate(runId, ARCHITECTURE_APPROVAL);
        harness.decide(runId, ARCHITECTURE_APPROVAL, Tokens.DUAL_ROLE, "APPROVE")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actorId").value("dave"));
    }
}
