package com.agentic.urlshortener.orchestration.governance;

import static com.agentic.urlshortener.orchestration.domain.StageType.ARCHITECTURE_APPROVAL;
import static com.agentic.urlshortener.orchestration.domain.StageType.DOCUMENTATION;
import static com.agentic.urlshortener.orchestration.domain.StageType.IMPLEMENTATION;
import static com.agentic.urlshortener.orchestration.domain.StageType.RELEASE_APPROVAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.agentic.urlshortener.common.security.ApiPrincipal;
import com.agentic.urlshortener.common.security.Role;
import com.agentic.urlshortener.orchestration.agent.ArtifactDraft;
import com.agentic.urlshortener.orchestration.agent.StageResult;
import com.agentic.urlshortener.orchestration.audit.AuditService;
import com.agentic.urlshortener.orchestration.domain.AwaitingType;
import com.agentic.urlshortener.orchestration.domain.Decision;
import com.agentic.urlshortener.orchestration.domain.RunStatus;
import com.agentic.urlshortener.orchestration.domain.StageNode;
import com.agentic.urlshortener.orchestration.domain.StageStatus;
import com.agentic.urlshortener.orchestration.domain.StageType;
import com.agentic.urlshortener.orchestration.dto.RequirementSubmission;
import com.agentic.urlshortener.orchestration.engine.IllegalTransitionException;
import com.agentic.urlshortener.orchestration.engine.RunCoordinator;
import com.agentic.urlshortener.orchestration.repository.DecisionRepository;
import com.agentic.urlshortener.orchestration.repository.StageAttemptRepository;
import com.agentic.urlshortener.orchestration.repository.StageNodeRepository;
import com.agentic.urlshortener.orchestration.repository.WorkflowRunRepository;
import com.agentic.urlshortener.orchestration.service.WorkflowService;
import com.agentic.urlshortener.support.IntegrationTest;
import com.agentic.urlshortener.support.ScriptedAgent;
import com.agentic.urlshortener.support.Tokens;

/** T042: gates open with a review bundle; only the right role decides; no gate succeeds without a decision. */
@IntegrationTest
@Import(ScriptedAgent.Config.class)
@Tag("FR-GOV-01")
@Tag("FR-GOV-03")
@Tag("FR-GOV-09")
@Tag("FR-RDY-04")
class GateDecisionBasicsTest {

    private static final ApiPrincipal ALICE = new ApiPrincipal("alice", "Alice", Set.of(Role.REQUESTER));

    @Autowired private MockMvc mvc;
    @Autowired private WorkflowService workflows;
    @Autowired private RunCoordinator coordinator;
    @Autowired private ScriptedAgent.Scripts scripts;
    @Autowired private WorkflowRunRepository runs;
    @Autowired private StageNodeRepository nodes;
    @Autowired private StageAttemptRepository attempts;
    @Autowired private DecisionRepository decisions;
    @Autowired private AuditService audit;

    @BeforeEach
    void materialDesign() {
        scripts.reset();
        scripts.set(StageType.DESIGN, context -> new StageResult.Succeeded(List.of(ArtifactDraft.json("DESIGN",
                "{\"materialChange\":true,\"materialReasons\":[\"public API change\"]}")), "scripted material design"));
    }

    private UUID runAwaitingArchitectureApproval() {
        UUID runId = workflows.submit(new RequirementSubmission("GATE-1", "Gate test", "Scripted gate run", "NEW_CAPABILITY",
                List.of("Given x, when y, then z"), List.of(), null), ALICE);
        awaitStatus(runId, RunStatus.AWAITING_HUMAN);
        return runId;
    }

    private void awaitStatus(UUID runId, RunStatus status) {
        await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(25))
                .until(() -> runs.findById(runId).orElseThrow().getStatus() == status);
    }

    private StageNode node(UUID runId, StageType type) {
        return nodes.findByRunIdAndStageKey(runId, type).orElseThrow();
    }

    private org.springframework.test.web.servlet.ResultActions decide(UUID runId, StageType gate, String token, String decision)
            throws Exception {
        return mvc.perform(post("/api/v1/workflows/" + runId + "/gates/" + gate + "/decision")
                .header(HttpHeaders.AUTHORIZATION, Tokens.bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"" + decision + "\",\"rationale\":\"reviewed the bundle (simulated human input)\"}"));
    }

    @Test
    void materialDesignOpensTheArchitectureGateWithADeadline() {
        UUID runId = runAwaitingArchitectureApproval();
        StageNode gate = node(runId, ARCHITECTURE_APPROVAL);
        assertThat(gate.getStatus()).isEqualTo(StageStatus.AWAITING_DECISION);
        assertThat(gate.getAwaiting()).isEqualTo(AwaitingType.APPROVAL);
        assertThat(gate.getDecisionDeadline()).isAfter(Instant.now());
        assertThat(node(runId, IMPLEMENTATION).getStatus()).isEqualTo(StageStatus.PENDING);
        assertThat(node(runId, DOCUMENTATION).getStatus()).isEqualTo(StageStatus.PENDING);
    }

    @Test
    void approverApprovesAndDependentsStartWithTheDecisionBoundToTheBundle() throws Exception {
        UUID runId = runAwaitingArchitectureApproval();
        decide(runId, ARCHITECTURE_APPROVAL, Tokens.APPROVER, "APPROVE")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("GATE"))
                .andExpect(jsonPath("$.outcome").value("APPROVED"))
                .andExpect(jsonPath("$.actorType").value("HUMAN"))
                .andExpect(jsonPath("$.actorId").value("bob"))
                .andExpect(jsonPath("$.actorRole").value("APPROVER"))
                .andExpect(jsonPath("$.boundFingerprints.DESIGN").exists())
                .andExpect(jsonPath("$.boundFingerprints.THREAT_MODEL").exists())
                .andExpect(jsonPath("$.valid").value(true));

        await().atMost(Duration.ofSeconds(20)).until(() -> attempts.findByRunIdOrderByStartedAtAsc(runId).stream()
                .anyMatch(a -> a.getStageKey() == IMPLEMENTATION));
        assertThat(attempts.findByRunIdOrderByStartedAtAsc(runId)).anyMatch(a -> a.getStageKey() == DOCUMENTATION);
        awaitStatus(runId, RunStatus.AWAITING_HUMAN);
        assertThat(node(runId, RELEASE_APPROVAL).getStatus()).isEqualTo(StageStatus.AWAITING_DECISION);
    }

    @Test
    void onlyTheReleaseOwnerDecidesTheReleaseGate() throws Exception {
        UUID runId = runAwaitingArchitectureApproval();
        decide(runId, ARCHITECTURE_APPROVAL, Tokens.APPROVER, "APPROVE").andExpect(status().isOk());
        await().atMost(Duration.ofSeconds(20)).until(() -> node(runId, RELEASE_APPROVAL).getStatus() == StageStatus.AWAITING_DECISION);

        decide(runId, RELEASE_APPROVAL, Tokens.APPROVER, "APPROVE")
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        decide(runId, RELEASE_APPROVAL, Tokens.AUDITOR, "APPROVE").andExpect(status().isForbidden());
        decide(runId, RELEASE_APPROVAL, Tokens.RELEASE_OWNER, "APPROVE")
                .andExpect(status().isOk()).andExpect(jsonPath("$.actorId").value("carol"))
                .andExpect(jsonPath("$.actorRole").value("RELEASE_OWNER"));
        awaitStatus(runId, RunStatus.COMPLETED);

        assertThat(audit.chain(runId.toString())).anyMatch(e -> e.getAction().equals("DECISION_REFUSED") && e.getActorId().equals("bob"));
    }

    @Test
    void rejectionEndsTheRunAsRejected() throws Exception {
        UUID runId = runAwaitingArchitectureApproval();
        decide(runId, ARCHITECTURE_APPROVAL, Tokens.APPROVER, "REJECT").andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("REJECTED"));
        awaitStatus(runId, RunStatus.REJECTED);
        assertThat(runs.findById(runId).orElseThrow().getTerminalReason()).contains("rejected by bob");
        assertThat(attempts.findByRunIdOrderByStartedAtAsc(runId)).noneMatch(a -> a.getStageKey() == IMPLEMENTATION);
        decide(runId, ARCHITECTURE_APPROVAL, Tokens.APPROVER, "APPROVE").andExpect(status().isConflict());
    }

    @Test
    void theEngineCannotCompleteAGateWithoutAValidDecision() {
        UUID runId = runAwaitingArchitectureApproval();
        for (int i = 0; i < 3; i++) {
            coordinator.advance(runId);
        }
        assertThat(node(runId, ARCHITECTURE_APPROVAL).getStatus()).isEqualTo(StageStatus.AWAITING_DECISION);
        assertThat(decisions.findByRunIdOrderByCreatedAtAsc(runId)).isEmpty();

        StageNode gate = node(runId, ARCHITECTURE_APPROVAL);
        assertThatThrownBy(() -> gate.succeed(Instant.now()))
                .isInstanceOf(IllegalTransitionException.class).hasMessageContaining("decision");
        Decision foreign = Decision.create(runId, RELEASE_APPROVAL, com.agentic.urlshortener.orchestration.domain.DecisionType.GATE,
                "APPROVED", com.agentic.urlshortener.orchestration.domain.ActorType.HUMAN, "carol", "RELEASE_OWNER", "wrong gate",
                null, "{}", Instant.now());
        assertThatThrownBy(() -> gate.succeedGate(foreign, Instant.now())).isInstanceOf(IllegalTransitionException.class);
    }

    @Test
    void invalidDecisionBodiesAreRejected() throws Exception {
        UUID runId = runAwaitingArchitectureApproval();
        mvc.perform(post("/api/v1/workflows/" + runId + "/gates/ARCHITECTURE_APPROVAL/decision")
                        .header(HttpHeaders.AUTHORIZATION, Tokens.bearer(Tokens.APPROVER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"MAYBE\",\"rationale\":\"x\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        decide(UUID.randomUUID(), ARCHITECTURE_APPROVAL, Tokens.APPROVER, "APPROVE")
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RUN_NOT_FOUND"));
    }
}
