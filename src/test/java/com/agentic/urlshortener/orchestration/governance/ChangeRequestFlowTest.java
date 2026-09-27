package com.agentic.urlshortener.orchestration.governance;

import static com.agentic.urlshortener.orchestration.domain.StageType.ARCHITECTURE_APPROVAL;
import static com.agentic.urlshortener.orchestration.domain.StageType.CHANGE_APPROVAL;
import static com.agentic.urlshortener.orchestration.domain.StageType.RELEASE;
import static com.agentic.urlshortener.orchestration.domain.StageType.RELEASE_APPROVAL;
import static com.agentic.urlshortener.orchestration.domain.StageType.REQUIREMENT_ANALYSIS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.agentic.urlshortener.common.security.ApiPrincipal;
import com.agentic.urlshortener.common.util.CanonicalJson;
import com.agentic.urlshortener.contract.OpenApiContract;
import com.agentic.urlshortener.orchestration.agent.ArtifactDraft;
import com.agentic.urlshortener.orchestration.agent.StageResult;
import com.agentic.urlshortener.orchestration.domain.AwaitingType;
import com.agentic.urlshortener.orchestration.domain.PlanVersion;
import com.agentic.urlshortener.orchestration.domain.RequirementVersion;
import com.agentic.urlshortener.orchestration.domain.RunStatus;
import com.agentic.urlshortener.orchestration.domain.StageStatus;
import com.agentic.urlshortener.orchestration.domain.StageType;
import com.agentic.urlshortener.orchestration.repository.PlanVersionRepository;
import com.agentic.urlshortener.orchestration.repository.RequirementVersionRepository;
import com.agentic.urlshortener.orchestration.repository.StageAttemptRepository;
import com.agentic.urlshortener.orchestration.repository.StageNodeRepository;
import com.agentic.urlshortener.orchestration.repository.WorkflowRunRepository;
import com.agentic.urlshortener.orchestration.service.WorkflowService;
import com.agentic.urlshortener.support.GovernanceHarness;
import com.agentic.urlshortener.support.IntegrationTest;
import com.agentic.urlshortener.support.ScriptedAgent;
import com.agentic.urlshortener.support.Tokens;

/**
 * T095 (FR-RPL-03, FR-RPL-04, FR-POL-01): an amended requirement that is not material, or arrives
 * before any approval, is applied at once (new requirement version, re-plan). A material change after
 * an approval inserts a CHANGE_APPROVAL gate before every stage not yet started; approving applies
 * it, rejecting removes the gate and the run continues. The impact lists affected stages and the
 * approvals that are re-validated.
 */
@IntegrationTest
@Import(ScriptedAgent.Config.class)
@Tag("FR-RPL-03")
@Tag("FR-RPL-04")
@Tag("FR-POL-01")
class ChangeRequestFlowTest {

    private static final String AMEND_NARRATIVE = "{\"amendment\":{\"ref\":\"GOV-1\",\"title\":\"Governance test\","
            + "\"narrative\":\"Scripted governance run, reworded\",\"type\":\"NEW_CAPABILITY\","
            + "\"acceptanceCriteria\":[\"Given x, when y, then z\"],\"constraints\":[]},\"reason\":\"wording only\"}";
    private static final String AMEND_CRITERIA = "{\"amendment\":{\"ref\":\"GOV-1\",\"title\":\"Governance test\","
            + "\"narrative\":\"Scripted governance run\",\"type\":\"NEW_CAPABILITY\","
            + "\"acceptanceCriteria\":[\"Given x, when y, then z\",\"Given a, when b, then c\"],\"constraints\":[]},"
            + "\"reason\":\"one more acceptance criterion\"}";

    @Autowired private MockMvc mvc;
    @Autowired private WorkflowService workflows;
    @Autowired private WorkflowRunRepository runs;
    @Autowired private StageNodeRepository nodes;
    @Autowired private StageAttemptRepository attempts;
    @Autowired private PlanVersionRepository plans;
    @Autowired private RequirementVersionRepository requirements;
    @Autowired private ScriptedAgent.Scripts scripts;

    private GovernanceHarness harness;

    @BeforeEach
    void materialDesign() {
        harness = new GovernanceHarness(mvc, workflows, runs, nodes);
        scripts.reset();
        scripts.set(StageType.REQUIREMENT_INGESTION, context -> new StageResult.Succeeded(List.of(ArtifactDraft.json("REQUIREMENT",
                CanonicalJson.canonicalize(context.requirement()))), "scripted: the submitted requirement"));
        scripts.set(StageType.DESIGN, context -> new StageResult.Succeeded(List.of(ArtifactDraft.json("DESIGN",
                "{\"materialChange\":true,\"materialReasons\":[\"public API change\"],\"criteria\":"
                        + context.inputJson("NORMALIZED_REQUIREMENT").path("criteriaCount").asInt(0) + "}")), "scripted"));
        scripts.set(REQUIREMENT_ANALYSIS, context -> new StageResult.Succeeded(List.of(ArtifactDraft.json("NORMALIZED_REQUIREMENT",
                "{\"clarificationRequired\":false,\"clarificationRationale\":\"scripted\",\"criteriaCount\":"
                        + context.inputJson("REQUIREMENT").path("acceptanceCriteria").size() + "}")), "scripted"));
    }

    private ResultActions submitChange(UUID runId, String token, String body) throws Exception {
        return mvc.perform(post("/api/v1/workflows/" + runId + "/change-requests").header(HttpHeaders.AUTHORIZATION, Tokens.bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions decideChange(UUID runId, String changeId, String token, String decision) throws Exception {
        return mvc.perform(post("/api/v1/workflows/" + runId + "/change-requests/" + changeId + "/decision")
                .header(HttpHeaders.AUTHORIZATION, Tokens.bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"" + decision + "\",\"rationale\":\"reviewed the change impact"
                        + GovernanceHarness.SIMULATED + "\"}"));
    }

    private UUID runAtReleaseApproval(ApiPrincipal requester) throws Exception {
        UUID runId = harness.submit(requester);
        harness.awaitGate(runId, ARCHITECTURE_APPROVAL);
        harness.decide(runId, ARCHITECTURE_APPROVAL, Tokens.APPROVER, "APPROVE").andExpect(status().isOk());
        harness.awaitGate(runId, RELEASE_APPROVAL);
        return runId;
    }

    private void awaitChangeGate(UUID runId) {
        await().atMost(Duration.ofSeconds(20))
                .until(() -> nodes.findByRunIdAndStageKey(runId, CHANGE_APPROVAL).map(n -> n.getStatus() == StageStatus.AWAITING_DECISION)
                        .orElse(false));
    }

    private List<String> planTriggers(UUID runId) {
        return plans.findByRunIdOrderByVersionAsc(runId).stream().map(PlanVersion::getTriggerType).toList();
    }

    private int analysisAttempts(UUID runId) {
        return (int) attempts.findByRunIdOrderByStartedAtAsc(runId).stream().filter(a -> a.getStageKey() == REQUIREMENT_ANALYSIS).count();
    }

    @Test
    void aChangeBeforeAnyApprovalIsAppliedAtOnce() throws Exception {
        UUID runId = harness.submit(GovernanceHarness.ALICE);
        harness.awaitGate(runId, ARCHITECTURE_APPROVAL);

        MvcResult accepted = submitChange(runId, Tokens.REQUESTER, AMEND_CRITERIA)
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.status").value("APPLIED")).andReturn();
        OpenApiContract.assertResponseMatches("POST", "/api/v1/workflows/" + runId + "/change-requests", accepted);

        List<RequirementVersion> versions = requirements.findByRunIdOrderByVersionAsc(runId);
        assertThat(versions).hasSize(2);
        assertThat(versions.get(1).getSource()).isEqualTo("CHANGE_REQUEST");
        assertThat(planTriggers(runId)).containsExactly("INITIAL", "CHANGE_REQUEST");
        await().atMost(Duration.ofSeconds(20)).until(() -> analysisAttempts(runId) == 2);
        harness.awaitGate(runId, ARCHITECTURE_APPROVAL);
    }

    @Test
    void aNonMaterialChangeAfterApprovalIsAppliedAndTheApprovalCarriesOver() throws Exception {
        UUID runId = runAtReleaseApproval(GovernanceHarness.ALICE);

        submitChange(runId, Tokens.REQUESTER, AMEND_NARRATIVE)
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.status").value("APPLIED"))
                .andExpect(jsonPath("$.material").value(false));

        harness.awaitGate(runId, RELEASE_APPROVAL);
        assertThat(harness.node(runId, ARCHITECTURE_APPROVAL).getStatus()).isEqualTo(StageStatus.SUCCEEDED);
        assertThat(nodes.findByRunIdAndStageKey(runId, CHANGE_APPROVAL)).isEmpty();
    }

    @Test
    void aMaterialChangeAfterApprovalWaitsForAChangeApprovalAndApprovingAppliesIt() throws Exception {
        UUID runId = runAtReleaseApproval(GovernanceHarness.ALICE);

        MvcResult pending = submitChange(runId, Tokens.REQUESTER, AMEND_CRITERIA)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
                .andExpect(jsonPath("$.material").value(true))
                .andExpect(jsonPath("$.impact.materialReasons[0]").value(org.hamcrest.Matchers.containsString("acceptance criteria")))
                .andExpect(jsonPath("$.impact.invalidatedApprovals").value(org.hamcrest.Matchers.hasItem("ARCHITECTURE_APPROVAL")))
                .andReturn();
        String changeId = CanonicalJson.parse(pending.getResponse().getContentAsString()).path("changeRequestId").asString();
        awaitChangeGate(runId);
        assertThat(harness.node(runId, CHANGE_APPROVAL).getAwaiting()).isEqualTo(AwaitingType.CHANGE_APPROVAL);
        assertThat(harness.node(runId, RELEASE).getDependsOn()).contains(CHANGE_APPROVAL);
        assertThat(requirements.findByRunIdOrderByVersionAsc(runId)).hasSize(1);
        assertThat(planTriggers(runId)).containsExactly("INITIAL", "CHANGE_REQUEST");

        MvcResult applied = decideChange(runId, changeId, Tokens.APPROVER, "APPROVE")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPLIED"))
                .andExpect(jsonPath("$.decidedBy").value("bob")).andReturn();
        OpenApiContract.assertResponseMatches("POST", "/api/v1/workflows/" + runId + "/change-requests/" + changeId + "/decision", applied);

        assertThat(requirements.findByRunIdOrderByVersionAsc(runId)).hasSize(2);
        assertThat(planTriggers(runId)).containsExactly("INITIAL", "CHANGE_REQUEST", "CHANGE_DECISION");
        harness.awaitGate(runId, ARCHITECTURE_APPROVAL);
        assertThat(analysisAttempts(runId)).isEqualTo(2);
    }

    @Test
    void rejectingAMaterialChangeRemovesTheGateAndTheRunContinues() throws Exception {
        UUID runId = runAtReleaseApproval(GovernanceHarness.ALICE);
        String changeId = CanonicalJson.parse(submitChange(runId, Tokens.REQUESTER, AMEND_CRITERIA).andReturn().getResponse()
                .getContentAsString()).path("changeRequestId").asString();
        awaitChangeGate(runId);

        decideChange(runId, changeId, Tokens.APPROVER, "REJECT")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));

        assertThat(harness.node(runId, CHANGE_APPROVAL).getStatus()).isEqualTo(StageStatus.REMOVED);
        assertThat(harness.node(runId, RELEASE).getDependsOn()).doesNotContain(CHANGE_APPROVAL);
        assertThat(requirements.findByRunIdOrderByVersionAsc(runId)).hasSize(1);
        harness.decide(runId, RELEASE_APPROVAL, Tokens.RELEASE_OWNER, "APPROVE").andExpect(status().isOk());
        harness.awaitStatus(runId, RunStatus.COMPLETED);
    }

    @Test
    void theRequesterOfAChangeCannotApproveIt() throws Exception {
        UUID runId = runAtReleaseApproval(GovernanceHarness.DAVE);
        String changeId = CanonicalJson.parse(submitChange(runId, Tokens.DUAL_ROLE, AMEND_CRITERIA).andReturn().getResponse()
                .getContentAsString()).path("changeRequestId").asString();

        decideChange(runId, changeId, Tokens.DUAL_ROLE, "APPROVE")
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("SEPARATION_OF_DUTIES"));
    }
}
