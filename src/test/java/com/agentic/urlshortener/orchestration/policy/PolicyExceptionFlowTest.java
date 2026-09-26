package com.agentic.urlshortener.orchestration.policy;

import static com.agentic.urlshortener.orchestration.domain.StageType.ARCHITECTURE_APPROVAL;
import static com.agentic.urlshortener.orchestration.domain.StageType.COMPLIANCE_EVALUATION;
import static com.agentic.urlshortener.orchestration.domain.StageType.RELEASE_APPROVAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.agentic.urlshortener.common.util.CanonicalJson;
import com.agentic.urlshortener.contract.OpenApiContract;
import com.agentic.urlshortener.orchestration.domain.AwaitingType;
import com.agentic.urlshortener.orchestration.domain.Decision;
import com.agentic.urlshortener.orchestration.domain.DecisionType;
import com.agentic.urlshortener.orchestration.domain.PolicyEvaluation;
import com.agentic.urlshortener.orchestration.domain.RunStatus;
import com.agentic.urlshortener.orchestration.domain.StageNode;
import com.agentic.urlshortener.orchestration.domain.StageStatus;
import com.agentic.urlshortener.orchestration.domain.StageType;
import com.agentic.urlshortener.orchestration.dto.RequirementSubmission;
import com.agentic.urlshortener.orchestration.governance.DeadlineSweeper;
import com.agentic.urlshortener.orchestration.repository.DecisionRepository;
import com.agentic.urlshortener.orchestration.repository.PolicyEvaluationRepository;
import com.agentic.urlshortener.orchestration.repository.StageNodeRepository;
import com.agentic.urlshortener.orchestration.repository.WorkflowRunRepository;
import com.agentic.urlshortener.orchestration.service.WorkflowService;
import com.agentic.urlshortener.support.GovernanceHarness;
import com.agentic.urlshortener.support.IntegrationTest;
import com.agentic.urlshortener.support.RequirementFixtures;
import com.agentic.urlshortener.support.Tokens;

/**
 * T076 (FR-POL-03, FR-POL-04, FR-POL-06, RDR-06), with the real agents: a simulated mandatory failure
 * of DOC-001 blocks compliance until a time-bound exception with a compensating control is approved by
 * someone other than its requester; approval re-evaluates to READY_WITH_ACCEPTED_LIMITATIONS, rejection
 * and deadline safe-stop the run, and an exception expired at release time blocks the release
 * (FR-RDY-02). Human decisions are simulated input of labeled demo principals.
 */
@IntegrationTest
@Tag("FR-POL-03")
@Tag("FR-POL-04")
@Tag("FR-POL-06")
@Tag("FR-RDY-02")
@Tag("RDR-06")
class PolicyExceptionFlowTest {

    @Autowired private MockMvc mvc;
    @Autowired private WorkflowService workflows;
    @Autowired private WorkflowRunRepository runs;
    @Autowired private StageNodeRepository nodes;
    @Autowired private DecisionRepository decisions;
    @Autowired private PolicyEvaluationRepository evaluations;
    @Autowired private DeadlineSweeper sweeper;

    private GovernanceHarness harness() {
        return new GovernanceHarness(mvc, workflows, runs, nodes);
    }

    private UUID blockedRun(Integer gateDeadlineSeconds) throws Exception {
        UUID runId = workflows.submit(new RequirementSubmission("GF-001", "Custom aliases for short links.",
                "As an API consumer, I want to optionally choose a custom alias when I create a short link so that I can share "
                        + "memorable links.", "NEW_CAPABILITY", RequirementFixtures.GF_001_CRITERIA,
                List.of("Aliases are case-sensitive, like generated codes, and share the code namespace."),
                new RequirementSubmission.SimulationOptions(gateDeadlineSeconds, List.of(
                        new RequirementSubmission.FaultSpec("COMPLIANCE_EVALUATION", "POLICY_FAILURE", 1, null, "DOC-001")))),
                GovernanceHarness.ALICE);
        awaitGate(runId, ARCHITECTURE_APPROVAL);
        harness().decide(runId, ARCHITECTURE_APPROVAL, Tokens.APPROVER, "APPROVE").andExpect(status().isOk());
        awaitGate(runId, COMPLIANCE_EVALUATION);
        return runId;
    }

    private void awaitGate(UUID runId, StageType stage) {
        await().atMost(Duration.ofSeconds(60)).pollInterval(Duration.ofMillis(50)).until(() -> {
            assertThat(runs.findById(runId).orElseThrow().getStatus().isTerminal()).as("run ended early").isFalse();
            return nodes.findByRunIdAndStageKey(runId, stage).orElseThrow().getStatus() == StageStatus.AWAITING_DECISION;
        });
    }

    private ResultActions requestException(UUID runId, String token, String policyId, Instant expiresAt) throws Exception {
        return mvc.perform(post("/api/v1/workflows/" + runId + "/policy-exceptions")
                .header(HttpHeaders.AUTHORIZATION, Tokens.bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"policyId\":\"" + policyId + "\",\"reason\":\"documentation lands in the next release (simulated)\","
                        + "\"scope\":\"run " + runId + " only\",\"compensatingControl\":\"release notes link to the runbook draft\","
                        + "\"expiresAt\":\"" + expiresAt + "\"}"));
    }

    private ResultActions decideException(UUID runId, String exceptionId, String token, String decision) throws Exception {
        return mvc.perform(post("/api/v1/workflows/" + runId + "/policy-exceptions/" + exceptionId + "/decision")
                .header(HttpHeaders.AUTHORIZATION, Tokens.bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"" + decision + "\",\"rationale\":\"reviewed the compensating control"
                        + GovernanceHarness.SIMULATED + "\"}"));
    }

    private static String id(MvcResult result) throws Exception {
        return CanonicalJson.parse(result.getResponse().getContentAsString()).path("exceptionId").asString();
    }

    private List<PolicyEvaluation> doc001(UUID runId) {
        return evaluations.findByRunIdOrderByEvaluatedAtAsc(runId).stream().filter(e -> e.getPolicyId().equals("DOC-001")).toList();
    }

    @Test
    void aMandatoryFailureBlocksAndAPendingExceptionDoesNotUnblock() throws Exception {
        UUID runId = blockedRun(null);
        StageNode compliance = harness().node(runId, COMPLIANCE_EVALUATION);
        assertThat(compliance.getAwaiting()).isEqualTo(AwaitingType.POLICY_EXCEPTION);
        assertThat(runs.findById(runId).orElseThrow().getReadiness()).isEqualTo("NOT_READY");
        assertThat(doc001(runId)).singleElement().satisfies(e -> {
            assertThat(e.getOutcome()).isEqualTo("FAIL");
            assertThat(e.isSimulated()).isTrue();
        });

        MvcResult requested = requestException(runId, Tokens.REQUESTER, "DOC-001", Instant.now().plus(1, ChronoUnit.DAYS))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.requestedBy").value("alice")).andReturn();
        OpenApiContract.assertResponseMatches("POST", "/api/v1/workflows/" + runId + "/policy-exceptions", requested);
        Thread.sleep(300);
        assertThat(harness().node(runId, COMPLIANCE_EVALUATION).getStatus()).isEqualTo(StageStatus.AWAITING_DECISION);

        requestException(runId, Tokens.REQUESTER, "SEC-001", Instant.now().plus(1, ChronoUnit.DAYS))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ILLEGAL_STATE"));
        requestException(runId, Tokens.REQUESTER, "DOC-001", Instant.now().minus(1, ChronoUnit.HOURS))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/workflows/" + runId + "/policy-exceptions")
                .header(HttpHeaders.AUTHORIZATION, Tokens.bearer(Tokens.REQUESTER))
                .contentType(MediaType.APPLICATION_JSON).content("{\"policyId\":\"DOC-001\",\"reason\":\"no control given\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anExceptionApprovedByAnotherApproverReEvaluatesToAcceptedLimitations() throws Exception {
        UUID runId = blockedRun(null);
        String exceptionId = id(requestException(runId, Tokens.DUAL_ROLE, "DOC-001", Instant.now().plus(1, ChronoUnit.DAYS))
                .andExpect(status().isCreated()).andReturn());

        decideException(runId, exceptionId, Tokens.DUAL_ROLE, "APPROVE")
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("SEPARATION_OF_DUTIES"));
        MvcResult approved = decideException(runId, exceptionId, Tokens.APPROVER, "APPROVE")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.decidedBy").value("bob")).andReturn();
        OpenApiContract.assertResponseMatches("POST", "/api/v1/workflows/" + runId + "/policy-exceptions/" + exceptionId + "/decision",
                approved);

        awaitGate(runId, RELEASE_APPROVAL);
        assertThat(runs.findById(runId).orElseThrow().getReadiness()).isEqualTo("READY_WITH_ACCEPTED_LIMITATIONS");
        PolicyEvaluation covered = doc001(runId).get(doc001(runId).size() - 1);
        assertThat(covered.getOutcome()).isEqualTo("EXCEPTION_REQUESTED");
        assertThat(covered.getExceptionId()).hasToString(exceptionId);
        assertThat(decisions.findByRunIdOrderByCreatedAtAsc(runId)).extracting(Decision::getDecisionType)
                .contains(DecisionType.EXCEPTION_REQUEST, DecisionType.EXCEPTION_DECISION);
    }

    @Test
    void aRejectedExceptionSafeStopsTheRun() throws Exception {
        UUID runId = blockedRun(null);
        String exceptionId = id(requestException(runId, Tokens.REQUESTER, "DOC-001", Instant.now().plus(1, ChronoUnit.DAYS))
                .andReturn());

        decideException(runId, exceptionId, Tokens.APPROVER, "REJECT").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        harness().awaitStatus(runId, RunStatus.SAFE_STOPPED);
        Decision stop = decisions.findByRunIdOrderByCreatedAtAsc(runId).stream()
                .filter(d -> d.getDecisionType() == DecisionType.SAFE_STOP).findFirst().orElseThrow();
        assertThat(CanonicalJson.parse(stop.getPayload()).path("trigger").asString()).isEqualTo("POLICY_EXCEPTION_REJECTED");
    }

    @Test
    void anExceptionThatExpiredBeforeTheReleaseDecisionBlocksTheRelease() throws Exception {
        UUID runId = blockedRun(null);
        Instant expiry = Instant.now().plusSeconds(6);
        String exceptionId = id(requestException(runId, Tokens.REQUESTER, "DOC-001", expiry).andReturn());
        decideException(runId, exceptionId, Tokens.APPROVER, "APPROVE").andExpect(status().isOk());
        awaitGate(runId, RELEASE_APPROVAL);
        await().atMost(Duration.ofSeconds(15)).until(() -> Instant.now().isAfter(expiry));

        harness().decide(runId, RELEASE_APPROVAL, Tokens.RELEASE_OWNER, "APPROVE")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RELEASE_NOT_READY"));
        assertThat(harness().node(runId, RELEASE_APPROVAL).getStatus()).isEqualTo(StageStatus.AWAITING_DECISION);
    }

    @Test
    void anUnansweredExceptionDeadlineSafeStopsTheRun() throws Exception {
        UUID runId = blockedRun(8);
        Instant deadline = harness().node(runId, COMPLIANCE_EVALUATION).getDecisionDeadline();
        await().atMost(Duration.ofSeconds(15)).until(() -> Instant.now().isAfter(deadline));

        sweeper.sweep();

        assertThat(runs.findById(runId).orElseThrow().getStatus()).isEqualTo(RunStatus.SAFE_STOPPED);
        assertThat(runs.findById(runId).orElseThrow().getTerminalReason()).contains("COMPLIANCE_EVALUATION").contains("POLICY_EXCEPTION");
    }
}
