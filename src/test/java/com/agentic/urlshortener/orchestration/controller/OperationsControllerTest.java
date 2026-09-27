package com.agentic.urlshortener.orchestration.controller;

import static com.agentic.urlshortener.orchestration.domain.StageType.DESIGN;
import static com.agentic.urlshortener.orchestration.domain.StageType.RELEASE_APPROVAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
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

import com.agentic.urlshortener.contract.OpenApiContract;
import com.agentic.urlshortener.orchestration.domain.Decision;
import com.agentic.urlshortener.orchestration.domain.DecisionType;
import com.agentic.urlshortener.orchestration.domain.RunStatus;
import com.agentic.urlshortener.orchestration.domain.StageStatus;
import com.agentic.urlshortener.orchestration.domain.StageType;
import com.agentic.urlshortener.orchestration.repository.DecisionRepository;
import com.agentic.urlshortener.orchestration.repository.StageAttemptRepository;
import com.agentic.urlshortener.orchestration.repository.StageNodeRepository;
import com.agentic.urlshortener.orchestration.repository.WorkflowRunRepository;
import com.agentic.urlshortener.orchestration.service.WorkflowService;
import com.agentic.urlshortener.support.GovernanceHarness;
import com.agentic.urlshortener.support.IntegrationTest;
import com.agentic.urlshortener.support.ScriptedAgent;
import com.agentic.urlshortener.support.Tokens;

/**
 * T072 (FR-REL-07, FR-REL-06, RDR-07): the release owner pauses (in-flight work finishes, nothing new
 * starts), resumes, and safe-stops runs from running, waiting, and paused states. Responses are
 * validated against the contract.
 */
@IntegrationTest
@Import(ScriptedAgent.Config.class)
@Tag("FR-REL-07")
@Tag("FR-REL-06")
@Tag("RDR-07")
class OperationsControllerTest {

    @Autowired private MockMvc mvc;
    @Autowired private WorkflowService workflows;
    @Autowired private WorkflowRunRepository runs;
    @Autowired private StageNodeRepository nodes;
    @Autowired private StageAttemptRepository attempts;
    @Autowired private DecisionRepository decisions;
    @Autowired private ScriptedAgent.Scripts scripts;

    private GovernanceHarness harness;

    @BeforeEach
    void setUp() {
        harness = new GovernanceHarness(mvc, workflows, runs, nodes);
        scripts.reset();
    }

    private ResultActions operate(UUID runId, String action, String token) throws Exception {
        return mvc.perform(post("/api/v1/workflows/" + runId + "/" + action)
                .header(HttpHeaders.AUTHORIZATION, Tokens.bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"operator drill" + GovernanceHarness.SIMULATED + "\"}"));
    }

    private void assertContract(UUID runId, String action, MvcResult result) throws Exception {
        OpenApiContract.assertResponseMatches("POST", "/api/v1/workflows/" + runId + "/" + action, result);
    }

    private boolean started(UUID runId, StageType stage) {
        return attempts.findByRunIdOrderByStartedAtAsc(runId).stream().anyMatch(a -> a.getStageKey() == stage);
    }

    @Test
    void pauseLetsTheInFlightStageFinishAndStartsNothingNewUntilResume() throws Exception {
        scripts.sleepThenSucceed(DESIGN, 600);
        UUID runId = harness.submit(GovernanceHarness.ALICE);
        await().atMost(Duration.ofSeconds(10)).until(() -> started(runId, DESIGN));

        MvcResult paused = operate(runId, "pause", Tokens.RELEASE_OWNER)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PAUSED")).andReturn();
        assertContract(runId, "pause", paused);

        await().atMost(Duration.ofSeconds(10)).until(() -> harness.node(runId, DESIGN).getStatus() == StageStatus.SUCCEEDED);
        Thread.sleep(300);
        assertThat(runs.findById(runId).orElseThrow().getStatus()).isEqualTo(RunStatus.PAUSED);
        assertThat(started(runId, StageType.IMPLEMENTATION)).isFalse();

        MvcResult resumed = operate(runId, "resume", Tokens.RELEASE_OWNER).andExpect(status().isOk()).andReturn();
        assertContract(runId, "resume", resumed);
        harness.awaitGate(runId, RELEASE_APPROVAL);
        assertThat(decisions.findByRunIdOrderByCreatedAtAsc(runId)).filteredOn(d -> d.getDecisionType() == DecisionType.OPERATOR_ACTION)
                .extracting(Decision::getOutcome).containsExactly("PAUSED", "RESUMED");
    }

    @Test
    void safeStopFromRunningWaitingAndPaused() throws Exception {
        scripts.sleepThenSucceed(DESIGN, 800);
        UUID running = harness.submit(GovernanceHarness.ALICE);
        await().atMost(Duration.ofSeconds(10)).until(() -> started(running, DESIGN));
        MvcResult stopped = operate(running, "safe-stop", Tokens.RELEASE_OWNER)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SAFE_STOPPED"))
                .andExpect(jsonPath("$.terminalReason").value(org.hamcrest.Matchers.containsString("carol"))).andReturn();
        assertContract(running, "safe-stop", stopped);

        scripts.reset();
        UUID waiting = harness.submit(GovernanceHarness.ALICE);
        harness.awaitGate(waiting, RELEASE_APPROVAL);
        operate(waiting, "safe-stop", Tokens.RELEASE_OWNER).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SAFE_STOPPED"));

        UUID paused = harness.submit(GovernanceHarness.ALICE);
        harness.awaitGate(paused, RELEASE_APPROVAL);
        operate(paused, "pause", Tokens.RELEASE_OWNER).andExpect(status().isOk());
        operate(paused, "safe-stop", Tokens.RELEASE_OWNER).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SAFE_STOPPED"));
    }

    @Test
    void operationsOnATerminalRunAreConflictsAndResumeNeedsAPausedRun() throws Exception {
        UUID runId = harness.submit(GovernanceHarness.ALICE);
        harness.awaitGate(runId, RELEASE_APPROVAL);
        operate(runId, "resume", Tokens.RELEASE_OWNER).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ILLEGAL_STATE"));

        operate(runId, "safe-stop", Tokens.RELEASE_OWNER).andExpect(status().isOk());
        MvcResult conflict = operate(runId, "pause", Tokens.RELEASE_OWNER)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RUN_TERMINAL")).andReturn();
        assertContract(runId, "pause", conflict);
        operate(runId, "safe-stop", Tokens.RELEASE_OWNER).andExpect(status().isConflict());
    }

    @Test
    void onlyTheReleaseOwnerOperatesAndAReasonIsRequired() throws Exception {
        UUID runId = harness.submit(GovernanceHarness.ALICE);
        operate(runId, "pause", Tokens.APPROVER).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/workflows/" + runId + "/pause").header(HttpHeaders.AUTHORIZATION, Tokens.bearer(Tokens.RELEASE_OWNER))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"x\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/workflows/" + UUID.randomUUID() + "/pause")
                .header(HttpHeaders.AUTHORIZATION, Tokens.bearer(Tokens.RELEASE_OWNER))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"not a run\"}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RUN_NOT_FOUND"));
    }
}
