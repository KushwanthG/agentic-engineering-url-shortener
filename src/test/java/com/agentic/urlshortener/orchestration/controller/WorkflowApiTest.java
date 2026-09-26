package com.agentic.urlshortener.orchestration.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import org.springframework.test.web.servlet.ResultActions;

import com.agentic.urlshortener.common.util.CanonicalJson;
import com.agentic.urlshortener.orchestration.agent.ArtifactDraft;
import com.agentic.urlshortener.orchestration.agent.StageResult;
import com.agentic.urlshortener.orchestration.domain.RunStatus;
import com.agentic.urlshortener.orchestration.domain.StageType;
import com.agentic.urlshortener.orchestration.repository.WorkflowRunRepository;
import com.agentic.urlshortener.support.IntegrationTest;
import com.agentic.urlshortener.support.RequirementFixtures;
import com.agentic.urlshortener.support.ScriptedAgent;
import com.agentic.urlshortener.support.Tokens;

import tools.jackson.databind.JsonNode;

/** T056: the workflow API exposes run state, plan, artifacts with lineage, decisions, timeline, and policy outcomes (FR-ORC-09). */
@IntegrationTest
@Import(ScriptedAgent.Config.class)
@Tag("FR-ORC-01")
@Tag("FR-ORC-09")
@Tag("FR-AUD-03")
@Tag("FR-AUD-06")
class WorkflowApiTest {

    @Autowired private MockMvc mvc;
    @Autowired private ScriptedAgent.Scripts scripts;
    @Autowired private WorkflowRunRepository runs;

    @BeforeEach
    void materialDesign() {
        scripts.reset();
        scripts.set(StageType.DESIGN, context -> new StageResult.Succeeded(List.of(ArtifactDraft.json("DESIGN",
                "{\"materialChange\":true,\"materialReasons\":[\"public API change\"]}")), "scripted material design"));
    }

    private ResultActions getAs(String token, String path) throws Exception {
        return mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, Tokens.bearer(token)));
    }

    private UUID submit() throws Exception {
        String location = mvc.perform(post("/api/v1/workflows").header(HttpHeaders.AUTHORIZATION, Tokens.bearer(Tokens.REQUESTER))
                        .contentType(MediaType.APPLICATION_JSON).content(RequirementFixtures.API_SUBMISSION))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, matchesPattern("/api/v1/workflows/[0-9a-f-]{36}")))
                .andExpect(jsonPath("$.title").value("Workflow API test"))
                .andExpect(jsonPath("$.requestedBy").value("alice"))
                .andExpect(jsonPath("$.policySetVersion").value("1.0.0"))
                .andReturn().getResponse().getHeader(HttpHeaders.LOCATION);
        return UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
    }

    private void awaitStatus(UUID runId, RunStatus status) {
        await().atMost(Duration.ofSeconds(20)).until(() -> runs.findById(runId).orElseThrow().getStatus() == status);
    }

    private void approve(UUID runId, StageType gate, String token) throws Exception {
        mvc.perform(post("/api/v1/workflows/" + runId + "/gates/" + gate + "/decision")
                        .header(HttpHeaders.AUTHORIZATION, Tokens.bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVE\",\"rationale\":\"reviewed (simulated human input)\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void runDetailShowsStagesAndPendingActions() throws Exception {
        UUID runId = submit();
        awaitStatus(runId, RunStatus.AWAITING_HUMAN);
        getAs(Tokens.AUDITOR, "/api/v1/workflows/" + runId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").value(runId.toString()))
                .andExpect(jsonPath("$.status").value("AWAITING_HUMAN"))
                .andExpect(jsonPath("$.classification").value("NEW_CAPABILITY"))
                .andExpect(jsonPath("$.planVersion").value(1))
                .andExpect(jsonPath("$.stages.length()").value(16))
                .andExpect(jsonPath("$.stages[?(@.key == 'CLARIFICATION')].status").value(hasItem("SKIPPED")))
                .andExpect(jsonPath("$.pendingActions[0].stageKey").value("ARCHITECTURE_APPROVAL"))
                .andExpect(jsonPath("$.pendingActions[0].actionType").value("APPROVAL"))
                .andExpect(jsonPath("$.pendingActions[0].requiredRole").value("APPROVER"))
                .andExpect(jsonPath("$.pendingActions[0].reviewArtifacts[0].type").value("DESIGN"))
                .andExpect(jsonPath("$.simulated").value(false));
        getAs(Tokens.APPROVER, "/api/v1/workflows").andExpect(status().isOk())
                .andExpect(jsonPath("$[*].runId").value(hasItem(runId.toString())));
    }

    @Test
    void historyEndpointsExposePlanRequirementsArtifactsDecisionsTimelineAndPolicies() throws Exception {
        UUID runId = submit();
        awaitStatus(runId, RunStatus.AWAITING_HUMAN);
        approve(runId, StageType.ARCHITECTURE_APPROVAL, Tokens.APPROVER);
        await().atMost(Duration.ofSeconds(20)).until(() -> runs.findById(runId).orElseThrow().getStatus() == RunStatus.AWAITING_HUMAN
                && CanonicalJson.parse(getAs(Tokens.AUDITOR, "/api/v1/workflows/" + runId).andReturn().getResponse().getContentAsString())
                        .path("pendingActions").path(0).path("stageKey").asString().equals("RELEASE_APPROVAL"));
        String base = "/api/v1/workflows/" + runId;

        getAs(Tokens.AUDITOR, base + "/plan-versions").andExpect(status().isOk())
                .andExpect(jsonPath("$[0].version").value(1)).andExpect(jsonPath("$[0].trigger").value("INITIAL"))
                .andExpect(jsonPath("$[0].stages.length()").value(16));
        getAs(Tokens.AUDITOR, base + "/requirement-versions").andExpect(status().isOk())
                .andExpect(jsonPath("$[0].version").value(1)).andExpect(jsonPath("$[0].source").value("SUBMITTED"));
        getAs(Tokens.AUDITOR, base + "/decisions").andExpect(status().isOk())
                .andExpect(jsonPath("$[0].stageKey").value("ARCHITECTURE_APPROVAL"))
                .andExpect(jsonPath("$[0].actorId").value("bob"));
        getAs(Tokens.AUDITOR, base + "/timeline").andExpect(status().isOk())
                .andExpect(jsonPath("$[0].stageKey").value("REQUIREMENT_INGESTION"))
                .andExpect(jsonPath("$[0].durationMillis").exists());
        getAs(Tokens.AUDITOR, base + "/policy-evaluations").andExpect(status().isOk());

        JsonNode artifacts = CanonicalJson.parse(getAs(Tokens.AUDITOR, base + "/artifacts").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        String changeSetId = null;
        for (JsonNode artifact : artifacts) {
            if (artifact.path("type").asString().equals("CHANGE_SET")) {
                changeSetId = artifact.path("artifactId").asString();
            }
        }
        assertThat(changeSetId).isNotNull();
        getAs(Tokens.AUDITOR, base + "/artifacts/" + changeSetId).andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("CHANGE_SET"))
                .andExpect(jsonPath("$.content").exists())
                .andExpect(jsonPath("$.inputs[*].type").value(hasItem("DESIGN")))
                .andExpect(jsonPath("$.lineage[*].stageKey").value(hasItem("ARCHITECTURE_APPROVAL")));
    }

    @Test
    void unknownRunsAndArtifactsAreNotFound() throws Exception {
        getAs(Tokens.AUDITOR, "/api/v1/workflows/" + UUID.randomUUID()).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RUN_NOT_FOUND"));
        UUID runId = submit();
        getAs(Tokens.AUDITOR, "/api/v1/workflows/" + runId + "/artifacts/" + UUID.randomUUID()).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ARTIFACT_NOT_FOUND"));
    }

    @Test
    void rolesAreEnforced() throws Exception {
        mvc.perform(post("/api/v1/workflows").header(HttpHeaders.AUTHORIZATION, Tokens.bearer(Tokens.APPROVER))
                .contentType(MediaType.APPLICATION_JSON).content(RequirementFixtures.API_SUBMISSION)).andExpect(status().isForbidden());
        getAs(Tokens.CONSUMER, "/api/v1/workflows").andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/workflows")).andExpect(status().isUnauthorized());
    }

    @Test
    void invalidSubmissionsAreRejected() throws Exception {
        mvc.perform(post("/api/v1/workflows").header(HttpHeaders.AUTHORIZATION, Tokens.bearer(Tokens.REQUESTER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"\",\"narrative\":\"x\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(post("/api/v1/workflows").header(HttpHeaders.AUTHORIZATION, Tokens.bearer(Tokens.REQUESTER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"t\",\"narrative\":\"x\",\"type\":\"WHATEVER\"}"))
                .andExpect(status().isBadRequest());
    }
}
