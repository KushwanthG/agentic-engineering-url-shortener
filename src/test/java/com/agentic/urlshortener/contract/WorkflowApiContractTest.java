package com.agentic.urlshortener.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.agentic.urlshortener.common.util.CanonicalJson;
import com.agentic.urlshortener.orchestration.domain.RunStatus;
import com.agentic.urlshortener.orchestration.repository.WorkflowRunRepository;
import com.agentic.urlshortener.support.IntegrationTest;
import com.agentic.urlshortener.support.RequirementFixtures;
import com.agentic.urlshortener.support.ScriptedAgent;
import com.agentic.urlshortener.support.Tokens;

/** T056: workflow API responses validated against {@code openapi.yaml} (NFR-CHG-01). */
@IntegrationTest
@Import(ScriptedAgent.Config.class)
@Tag("NFR-CHG-01")
@Tag("FR-ORC-09")
class WorkflowApiContractTest {

    @Autowired private MockMvc mvc;
    @Autowired private WorkflowRunRepository runs;

    private MvcResult get(String path, String token) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.get(path)
                .header(HttpHeaders.AUTHORIZATION, Tokens.bearer(token))).andReturn();
    }

    private static void assertContract(String method, String path, MvcResult result, int status) throws Exception {
        assertThat(result.getResponse().getStatus()).as(path).isEqualTo(status);
        OpenApiContract.assertResponseMatches(method, path, result);
    }

    @Test
    void everyWorkflowResponseMatchesTheContract() throws Exception {
        MvcResult created = mvc.perform(post("/api/v1/workflows").header(HttpHeaders.AUTHORIZATION, Tokens.bearer(Tokens.REQUESTER))
                .contentType(MediaType.APPLICATION_JSON).content(RequirementFixtures.API_SUBMISSION))
                .andReturn();
        assertContract("POST", "/api/v1/workflows", created, 201);
        UUID runId = UUID.fromString(CanonicalJson.parse(created.getResponse().getContentAsString()).path("runId").asString());
        await().atMost(Duration.ofSeconds(20)).until(() -> runs.findById(runId).orElseThrow().getStatus() == RunStatus.AWAITING_HUMAN);

        String base = "/api/v1/workflows/" + runId;
        assertContract("GET", "/api/v1/workflows", get("/api/v1/workflows", Tokens.AUDITOR), 200);
        assertContract("GET", base, get(base, Tokens.AUDITOR), 200);
        for (String sub : new String[] {"/plan-versions", "/requirement-versions", "/artifacts", "/decisions", "/timeline",
                "/policy-evaluations"}) {
            assertContract("GET", base + sub, get(base + sub, Tokens.AUDITOR), 200);
        }
        String artifactId = CanonicalJson.parse(get(base + "/artifacts", Tokens.AUDITOR).getResponse().getContentAsString())
                .path(0).path("artifactId").asString();
        assertContract("GET", base + "/artifacts/" + artifactId, get(base + "/artifacts/" + artifactId, Tokens.AUDITOR), 200);

        assertContract("GET", "/api/v1/workflows/" + UUID.randomUUID(), get("/api/v1/workflows/" + UUID.randomUUID(), Tokens.AUDITOR), 404);
        assertContract("GET", "/api/v1/workflows", get("/api/v1/workflows", Tokens.CONSUMER), 403);
        assertContract("POST", "/api/v1/workflows", mvc.perform(post("/api/v1/workflows")
                .header(HttpHeaders.AUTHORIZATION, Tokens.bearer(Tokens.REQUESTER)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"\",\"narrative\":\"x\"}")).andReturn(), 400);
        assertContract("POST", base + "/gates/ARCHITECTURE_APPROVAL/decision", mvc.perform(post(base + "/gates/ARCHITECTURE_APPROVAL/decision")
                .header(HttpHeaders.AUTHORIZATION, Tokens.bearer(Tokens.APPROVER)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"APPROVE\",\"rationale\":\"not awaiting\"}")).andReturn(), 409);
        assertContract("POST", base + "/gates/RELEASE_APPROVAL/decision", mvc.perform(post(base + "/gates/RELEASE_APPROVAL/decision")
                .header(HttpHeaders.AUTHORIZATION, Tokens.bearer(Tokens.RELEASE_OWNER)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"APPROVE\",\"rationale\":\"release (simulated human input)\"}")).andReturn(), 200);
    }
}
