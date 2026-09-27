package com.agentic.urlshortener.support;

import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.agentic.urlshortener.common.security.ApiPrincipal;
import com.agentic.urlshortener.common.security.Role;
import com.agentic.urlshortener.orchestration.domain.RunStatus;
import com.agentic.urlshortener.orchestration.domain.StageNode;
import com.agentic.urlshortener.orchestration.domain.StageStatus;
import com.agentic.urlshortener.orchestration.domain.StageType;
import com.agentic.urlshortener.orchestration.dto.RequirementSubmission;
import com.agentic.urlshortener.orchestration.repository.StageNodeRepository;
import com.agentic.urlshortener.orchestration.repository.WorkflowRunRepository;
import com.agentic.urlshortener.orchestration.service.WorkflowService;

/** Shared steps of the governance tests: submit a scripted run, wait for states, decide gates over HTTP. */
public final class GovernanceHarness {

    public static final ApiPrincipal ALICE = new ApiPrincipal("alice", "Alice", Set.of(Role.REQUESTER));
    /** Test-only principal holding requester, approver and release-owner roles (token {@link Tokens#DUAL_ROLE}). */
    public static final ApiPrincipal DAVE = new ApiPrincipal("dave", "Dave",
            Set.of(Role.REQUESTER, Role.APPROVER, Role.RELEASE_OWNER));
    public static final String SIMULATED = " (simulated human input)";

    private final MockMvc mvc;
    private final WorkflowService workflows;
    private final WorkflowRunRepository runs;
    private final StageNodeRepository nodes;

    public GovernanceHarness(MockMvc mvc, WorkflowService workflows, WorkflowRunRepository runs, StageNodeRepository nodes) {
        this.mvc = mvc;
        this.workflows = workflows;
        this.runs = runs;
        this.nodes = nodes;
    }

    public UUID submit(ApiPrincipal requester) {
        return submit(requester, null);
    }

    public UUID submit(ApiPrincipal requester, RequirementSubmission.SimulationOptions simulation) {
        return workflows.submit(new RequirementSubmission("GOV-1", "Governance test", "Scripted governance run", "NEW_CAPABILITY",
                List.of("Given x, when y, then z"), List.of(), simulation), requester);
    }

    public void awaitStatus(UUID runId, RunStatus status) {
        await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(25))
                .until(() -> runs.findById(runId).orElseThrow().getStatus() == status);
    }

    public void awaitGate(UUID runId, StageType gate) {
        await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(25))
                .until(() -> node(runId, gate).getStatus() == StageStatus.AWAITING_DECISION);
    }

    public StageNode node(UUID runId, StageType type) {
        return nodes.findByRunIdAndStageKey(runId, type).orElseThrow();
    }

    public ResultActions decide(UUID runId, StageType gate, String token, String decision) throws Exception {
        return mvc.perform(post("/api/v1/workflows/" + runId + "/gates/" + gate + "/decision")
                .header(HttpHeaders.AUTHORIZATION, Tokens.bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"" + decision + "\",\"rationale\":\"reviewed the bundle" + SIMULATED + "\"}"));
    }
}
