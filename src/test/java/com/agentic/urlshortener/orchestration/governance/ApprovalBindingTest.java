package com.agentic.urlshortener.orchestration.governance;

import static com.agentic.urlshortener.orchestration.domain.StageType.ARCHITECTURE_APPROVAL;
import static com.agentic.urlshortener.orchestration.domain.StageType.DESIGN;
import static com.agentic.urlshortener.orchestration.domain.StageType.RELEASE_APPROVAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.agentic.urlshortener.orchestration.agent.ArtifactDraft;
import com.agentic.urlshortener.orchestration.agent.StageResult;
import com.agentic.urlshortener.orchestration.audit.AuditService;
import com.agentic.urlshortener.orchestration.domain.Decision;
import com.agentic.urlshortener.orchestration.domain.RunStatus;
import com.agentic.urlshortener.orchestration.domain.StageStatus;
import com.agentic.urlshortener.orchestration.engine.ArtifactStore;
import com.agentic.urlshortener.orchestration.engine.RunCoordinator;
import com.agentic.urlshortener.orchestration.repository.DecisionRepository;
import com.agentic.urlshortener.orchestration.repository.StageNodeRepository;
import com.agentic.urlshortener.orchestration.repository.WorkflowRunRepository;
import com.agentic.urlshortener.orchestration.service.WorkflowService;
import com.agentic.urlshortener.support.GovernanceHarness;
import com.agentic.urlshortener.support.IntegrationTest;
import com.agentic.urlshortener.support.ScriptedAgent;
import com.agentic.urlshortener.support.Tokens;

/**
 * T060 (FR-GOV-05): an approval is bound to the fingerprints of the reviewed artifacts; a changed
 * bound artifact invalidates it with a reason and re-opens the gate, an unchanged bundle keeps it.
 */
@IntegrationTest
@Import(ScriptedAgent.Config.class)
@Tag("FR-GOV-05")
class ApprovalBindingTest {

    private static final String MATERIAL_DESIGN = "{\"materialChange\":true,\"materialReasons\":[\"public API change\"]}";

    @Autowired private MockMvc mvc;
    @Autowired private WorkflowService workflows;
    @Autowired private WorkflowRunRepository runs;
    @Autowired private StageNodeRepository nodes;
    @Autowired private DecisionRepository decisions;
    @Autowired private ArtifactStore artifacts;
    @Autowired private RunCoordinator coordinator;
    @Autowired private AuditService audit;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private ScriptedAgent.Scripts scripts;

    private GovernanceHarness harness;

    @BeforeEach
    void materialDesign() {
        harness = new GovernanceHarness(mvc, workflows, runs, nodes);
        scripts.reset();
        scripts.set(DESIGN, context -> new StageResult.Succeeded(List.of(ArtifactDraft.json("DESIGN", MATERIAL_DESIGN)),
                "scripted material design"));
    }

    private UUID runWaitingForReleaseApproval() throws Exception {
        UUID runId = harness.submit(GovernanceHarness.ALICE);
        harness.awaitGate(runId, ARCHITECTURE_APPROVAL);
        harness.decide(runId, ARCHITECTURE_APPROVAL, Tokens.APPROVER, "APPROVE").andExpect(status().isOk());
        harness.awaitGate(runId, RELEASE_APPROVAL);
        return runId;
    }

    private Decision architectureDecision(UUID runId) {
        return decisions.findByRunIdOrderByCreatedAtAsc(runId).stream()
                .filter(d -> d.getStageKey() == ARCHITECTURE_APPROVAL).findFirst().orElseThrow();
    }

    @Test
    void anUnchangedBundleKeepsTheApprovalValid() throws Exception {
        UUID runId = runWaitingForReleaseApproval();

        coordinator.advance(runId);

        assertThat(architectureDecision(runId).isValid()).isTrue();
        assertThat(harness.node(runId, ARCHITECTURE_APPROVAL).getStatus()).isEqualTo(StageStatus.SUCCEEDED);
        assertThat(harness.node(runId, RELEASE_APPROVAL).getStatus()).isEqualTo(StageStatus.AWAITING_DECISION);
    }

    @Test
    void aChangedBoundArtifactInvalidatesTheApprovalAndReopensTheGate() throws Exception {
        UUID runId = runWaitingForReleaseApproval();
        String approvedFingerprint = artifacts.current(runId).get("DESIGN").getFingerprint();

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> artifacts.store(runId, DESIGN, 1, 1,
                "test-edit", List.of(ArtifactDraft.json("DESIGN",
                        "{\"materialChange\":true,\"materialReasons\":[\"public API change\",\"new index\"]}")),
                Map.of(), Instant.now()));
        coordinator.advance(runId);

        Decision invalidated = architectureDecision(runId);
        assertThat(invalidated.isValid()).isFalse();
        assertThat(invalidated.getInvalidatedReason()).contains("DESIGN").contains(approvedFingerprint.substring(0, 12));
        assertThat(harness.node(runId, ARCHITECTURE_APPROVAL).getStatus()).isEqualTo(StageStatus.AWAITING_DECISION);
        assertThat(harness.node(runId, ARCHITECTURE_APPROVAL).getGeneration()).isEqualTo(2);
        assertThat(harness.node(runId, RELEASE_APPROVAL).getStatus()).isEqualTo(StageStatus.PENDING);
        assertThat(audit.chain(runId.toString())).anyMatch(e -> e.getAction().equals("DECISION_INVALIDATED"));

        // A fresh approval is bound to the new content and the run completes.
        harness.decide(runId, ARCHITECTURE_APPROVAL, Tokens.APPROVER, "APPROVE")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.boundFingerprints.DESIGN").value(artifacts.current(runId).get("DESIGN").getFingerprint()));
        harness.awaitGate(runId, RELEASE_APPROVAL);
        harness.decide(runId, RELEASE_APPROVAL, Tokens.RELEASE_OWNER, "APPROVE").andExpect(status().isOk());
        harness.awaitStatus(runId, RunStatus.COMPLETED);
    }
}
