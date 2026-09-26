package com.agentic.sdlc.platform.security;

import static com.agentic.sdlc.support.Tokens.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.agentic.sdlc.support.IntegrationTest;
import com.agentic.sdlc.support.Tokens;

/** T011: public vs. protected routes, role checks, problem bodies, stateless sessions (ADR-015). */
@IntegrationTest
@Tag("NFR-SEC-02")
@Tag("FR-LNK-13")
@Tag("FR-GOV-03")
@Tag("NFR-SCA-01")
class SecurityMatrixTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void healthAndInfoArePublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/actuator/info")).andExpect(status().isOk());
    }

    @Test
    void metricsEndpointsRequireTheAuditorRole() throws Exception {
        mvc.perform(get("/actuator/prometheus")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/prometheus").header(HttpHeaders.AUTHORIZATION, bearer(Tokens.CONSUMER)))
                .andExpect(status().isForbidden());
        // Spring Boot disables metrics export (and so the Prometheus registry) in tests; /metrics is always present.
        mvc.perform(get("/actuator/metrics").header(HttpHeaders.AUTHORIZATION, bearer(Tokens.AUDITOR)))
                .andExpect(status().isOk());
    }

    @Test
    void linkApiRejectsMissingCredentialsWithAProblem() throws Exception {
        mvc.perform(post("/api/v1/links").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());
    }

    @Test
    void linkApiRejectsUnknownTokens() throws Exception {
        mvc.perform(post("/api/v1/links").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-known-token")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void linkApiRejectsPrincipalsWithoutTheConsumerRole() throws Exception {
        mvc.perform(post("/api/v1/links").header(HttpHeaders.AUTHORIZATION, bearer(Tokens.REQUESTER))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void linkApiAdmitsApiConsumers() throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/links").header(HttpHeaders.AUTHORIZATION, bearer(Tokens.CONSUMER))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn();
        assertThat(result.getResponse().getStatus()).isNotIn(401, 403);
    }

    @Test
    void redirectsArePublic() throws Exception {
        MvcResult result = mvc.perform(get("/aB3dE9x")).andReturn();
        assertThat(result.getResponse().getStatus()).isNotIn(401, 403);
    }

    @Test
    void controlPlaneReadsRequireAControlPlaneRole() throws Exception {
        mvc.perform(get("/api/v1/workflows").header(HttpHeaders.AUTHORIZATION, bearer(Tokens.CONSUMER)))
                .andExpect(status().isForbidden());
        MvcResult result = mvc.perform(get("/api/v1/workflows").header(HttpHeaders.AUTHORIZATION, bearer(Tokens.AUDITOR)))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isNotIn(401, 403);
    }

    @Test
    void submittingRequirementsRequiresTheRequesterRole() throws Exception {
        mvc.perform(post("/api/v1/workflows").header(HttpHeaders.AUTHORIZATION, bearer(Tokens.APPROVER))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        MvcResult result = mvc.perform(post("/api/v1/workflows").header(HttpHeaders.AUTHORIZATION, bearer(Tokens.REQUESTER))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn();
        assertThat(result.getResponse().getStatus()).isNotIn(401, 403);
    }

    @Test
    void operatorActionsRequireTheReleaseOwnerRole() throws Exception {
        mvc.perform(post("/api/v1/workflows/00000000-0000-0000-0000-000000000001/safe-stop")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Tokens.APPROVER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"test\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void unknownPathsAreDeniedByDefault() throws Exception {
        MvcResult result = mvc.perform(get("/internal/secret/area")).andReturn();
        assertThat(result.getResponse().getStatus()).isIn(401, 403);
    }

    @Test
    void noHttpSessionIsCreated() throws Exception {
        MvcResult result = mvc.perform(get("/api/v1/workflows").header(HttpHeaders.AUTHORIZATION, bearer(Tokens.AUDITOR)))
                .andReturn();
        assertThat(result.getRequest().getSession(false)).isNull();
        assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).isNull();
    }
}
