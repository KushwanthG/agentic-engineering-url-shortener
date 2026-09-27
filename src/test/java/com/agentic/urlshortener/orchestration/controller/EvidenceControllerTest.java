package com.agentic.urlshortener.orchestration.controller;

import static com.agentic.urlshortener.orchestration.domain.StageType.RELEASE_APPROVAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.agentic.urlshortener.contract.OpenApiContract;
import com.agentic.urlshortener.orchestration.policy.PolicySetLoader;
import com.agentic.urlshortener.orchestration.repository.StageNodeRepository;
import com.agentic.urlshortener.orchestration.repository.WorkflowRunRepository;
import com.agentic.urlshortener.orchestration.service.WorkflowService;
import com.agentic.urlshortener.support.GovernanceHarness;
import com.agentic.urlshortener.support.IntegrationTest;
import com.agentic.urlshortener.support.ScriptedAgent;
import com.agentic.urlshortener.support.Tokens;

/**
 * T101 (FR-AUD-02, FR-AUD-03, SC-006): a reviewer reads a run's audit trail, re-verifies its hash
 * chain (and sees tampering by direct SQL as a broken chain with the first broken sequence), reads
 * the final summary, the active policy set, and the capability release states. Every read role may
 * inspect evidence; API consumers may not. Responses are validated against the contract.
 */
@IntegrationTest
@Import(ScriptedAgent.Config.class)
@Tag("FR-AUD-02")
@Tag("FR-AUD-03")
@Tag("SC-006")
class EvidenceControllerTest {

    @Autowired private MockMvc mvc;
    @Autowired private WorkflowService workflows;
    @Autowired private WorkflowRunRepository runs;
    @Autowired private StageNodeRepository nodes;
    @Autowired private ScriptedAgent.Scripts scripts;
    @Autowired private PolicySetLoader policySets;
    @Autowired private DataSource dataSource;

    private GovernanceHarness harness;

    @BeforeEach
    void setUp() {
        harness = new GovernanceHarness(mvc, workflows, runs, nodes);
        scripts.reset();
    }

    private ResultActions read(String path, String token) throws Exception {
        return mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, Tokens.bearer(token)));
    }

    private MvcResult readValid(String path, String token) throws Exception {
        MvcResult result = read(path, token).andExpect(status().isOk()).andReturn();
        OpenApiContract.assertResponseMatches("GET", path, result);
        return result;
    }

    @Test
    void theAuditTrailIsReadableAndVerifiesAsIntact() throws Exception {
        UUID runId = harness.submit(GovernanceHarness.ALICE);
        harness.awaitGate(runId, RELEASE_APPROVAL);
        String base = "/api/v1/workflows/" + runId;

        readValid(base + "/audit", Tokens.AUDITOR);
        read(base + "/audit", Tokens.AUDITOR)
                .andExpect(jsonPath("$[0].seq").value(1))
                .andExpect(jsonPath("$[0].chainId").value(runId.toString()))
                .andExpect(jsonPath("$[0].prevHash").isString())
                .andExpect(jsonPath("$.length()").value(greaterThan(5)));

        readValid(base + "/audit/verification", Tokens.AUDITOR);
        read(base + "/audit/verification", Tokens.REQUESTER)
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.firstBrokenSeq").doesNotExist())
                .andExpect(jsonPath("$.eventsChecked").value(greaterThan(5)));
    }

    @Test
    void tamperingByDirectSqlIsReportedWithTheFirstBrokenSequence() throws Exception {
        UUID runId = harness.submit(GovernanceHarness.ALICE);
        harness.awaitGate(runId, RELEASE_APPROVAL);
        int changed = new JdbcTemplate(dataSource)
                .update("UPDATE audit_event SET reason = 'forged by direct SQL' WHERE chain_id = ? AND seq = 3", runId.toString());
        assertThat(changed).isEqualTo(1);

        String path = "/api/v1/workflows/" + runId + "/audit/verification";
        readValid(path, Tokens.AUDITOR);
        read(path, Tokens.AUDITOR)
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.firstBrokenSeq").value(3))
                .andExpect(jsonPath("$.message").value(containsString("3")));
    }

    @Test
    void theFinalSummaryOfATerminalRunIsMarkdownAndMissingBeforeTheRunEnds() throws Exception {
        UUID runId = harness.submit(GovernanceHarness.ALICE);
        harness.awaitGate(runId, RELEASE_APPROVAL);
        String path = "/api/v1/workflows/" + runId + "/summary";
        read(path, Tokens.AUDITOR).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        mvc.perform(post("/api/v1/workflows/" + runId + "/safe-stop")
                        .header(HttpHeaders.AUTHORIZATION, Tokens.bearer(Tokens.RELEASE_OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"evidence test stop" + GovernanceHarness.SIMULATED + "\"}"))
                .andExpect(status().isOk());

        MvcResult summary = read(path, Tokens.AUDITOR).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/markdown")).andReturn();
        OpenApiContract.assertResponseMatches("GET", path, summary);
        // Scripted agents write a placeholder summary; the endpoint must return the stored artifact verbatim.
        assertThat(summary.getResponse().getContentAsString()).isEqualTo("# scripted FINAL_SUMMARY");
    }

    @Test
    void thePolicySetAndCapabilityStatesArePublishedToEveryReadRole() throws Exception {
        readValid("/api/v1/policies", Tokens.AUDITOR);
        read("/api/v1/policies", Tokens.APPROVER)
                .andExpect(jsonPath("$.version").value(policySets.current().version()))
                .andExpect(jsonPath("$.policies.length()").value(policySets.current().policies().size()))
                .andExpect(jsonPath("$.policies[0].severity").isString());

        readValid("/api/v1/capabilities", Tokens.RELEASE_OWNER);
        read("/api/v1/capabilities", Tokens.AUDITOR)
                .andExpect(jsonPath("$[?(@.capabilityId == 'custom-alias')].released").isArray())
                .andExpect(jsonPath("$[?(@.capabilityId == 'click-limit')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.capabilityId == 'default-expiry')]").isNotEmpty());
    }

    @Test
    void consumersAndAnonymousCallersCannotReadEvidence() throws Exception {
        UUID runId = harness.submit(GovernanceHarness.ALICE);
        for (String path : new String[] {"/api/v1/workflows/" + runId + "/audit", "/api/v1/workflows/" + runId + "/audit/verification",
                "/api/v1/workflows/" + runId + "/summary", "/api/v1/policies", "/api/v1/capabilities", "/api/v1/reliability/report"}) {
            read(path, Tokens.CONSUMER).andExpect(status().isForbidden());
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
        read("/api/v1/workflows/" + UUID.randomUUID() + "/audit", Tokens.AUDITOR)
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RUN_NOT_FOUND"));
    }
}
