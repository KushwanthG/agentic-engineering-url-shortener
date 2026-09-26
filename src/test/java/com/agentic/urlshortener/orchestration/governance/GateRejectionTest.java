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
import com.agentic.urlshortener.orchestration.domain.WorkflowRun;
import com.agentic.urlshortener.orchestration.port.ApplicationPlanePort;
import com.agentic.urlshortener.orchestration.port.ProbeResponse;
import com.agentic.urlshortener.orchestration.port.SyntheticLinkSpec;
import com.agentic.urlshortener.orchestration.repository.StageAttemptRepository;
import com.agentic.urlshortener.orchestration.repository.StageNodeRepository;
import com.agentic.urlshortener.orchestration.repository.WorkflowRunRepository;
import com.agentic.urlshortener.orchestration.service.WorkflowService;
import com.agentic.urlshortener.support.GovernanceHarness;
import com.agentic.urlshortener.support.IntegrationTest;
import com.agentic.urlshortener.support.ScriptedAgent;
import com.agentic.urlshortener.support.Tokens;

/** T062 (FR-GOV-06): a rejection compensates leftover side effects, then ends the run {@code REJECTED}. */
@IntegrationTest
@Import(ScriptedAgent.Config.class)
@Tag("FR-GOV-06")
class GateRejectionTest {

    @Autowired private MockMvc mvc;
    @Autowired private WorkflowService workflows;
    @Autowired private WorkflowRunRepository runs;
    @Autowired private StageNodeRepository nodes;
    @Autowired private StageAttemptRepository attempts;
    @Autowired private ApplicationPlanePort port;
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
    void rejectingTheArchitectureGateEndsTheRunBeforeImplementation() throws Exception {
        UUID runId = harness.submit(GovernanceHarness.ALICE);
        harness.awaitGate(runId, ARCHITECTURE_APPROVAL);

        harness.decide(runId, ARCHITECTURE_APPROVAL, Tokens.APPROVER, "REJECT")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("REJECTED"));

        harness.awaitStatus(runId, RunStatus.REJECTED);
        assertThat(attempts.findByRunIdOrderByStartedAtAsc(runId)).noneMatch(a -> a.getStageKey() == StageType.IMPLEMENTATION);
        assertThat(harness.node(runId, StageType.IMPLEMENTATION).getStatus()).isEqualTo(StageStatus.CANCELLED);
    }

    @Test
    void rejectingTheReleaseGateRemovesLeftoverSyntheticDataAndChangesNoCapability() throws Exception {
        boolean releasedBefore = port.capability("custom-alias").released();
        UUID runId = harness.submit(GovernanceHarness.ALICE);
        harness.awaitGate(runId, ARCHITECTURE_APPROVAL);
        harness.decide(runId, ARCHITECTURE_APPROVAL, Tokens.APPROVER, "APPROVE").andExpect(status().isOk());
        harness.awaitGate(runId, RELEASE_APPROVAL);
        // Leftover probe data of this run (as if a probe's cleanup had not run).
        ProbeResponse leftover = port.createSyntheticLink(runId, new SyntheticLinkSpec("https://example.com/leftover", null, null, null));
        assertThat(leftover.outcome()).isEqualTo(ProbeResponse.Outcome.CREATED);

        harness.decide(runId, RELEASE_APPROVAL, Tokens.RELEASE_OWNER, "REJECT").andExpect(status().isOk());

        harness.awaitStatus(runId, RunStatus.REJECTED);
        WorkflowRun run = runs.findById(runId).orElseThrow();
        assertThat(run.getTerminalReason()).contains("RELEASE_APPROVAL").contains("carol");
        assertThat(port.findLink(leftover.code())).isEmpty();
        assertThat(port.capability("custom-alias").released()).isEqualTo(releasedBefore);
        assertThat(audit.chain(runId.toString())).anyMatch(e -> e.getAction().equals("COMPENSATION_ACTION")
                && e.getTarget().equals("SYNTHETIC_DATA") && e.getResult().equals("OK"));
        assertThat(audit.chain(runId.toString())).anyMatch(e -> e.getAction().equals("RUN_TRANSITION")
                && "COMPENSATING".equals(e.getToState()));
    }
}
