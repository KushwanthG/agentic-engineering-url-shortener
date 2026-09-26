package com.agentic.urlshortener.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.stream.Stream;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.agentic.urlshortener.support.IntegrationTest;
import com.agentic.urlshortener.support.Tokens;

/**
 * T065 (NFR-SEC-02, FR-GOV-03): every control-plane endpoint refuses unauthenticated callers (401)
 * and principals without the endpoint's role (403), before any handler runs. Verification test.
 */
@IntegrationTest
@Tag("NFR-SEC-02")
@Tag("FR-GOV-03")
@Tag("NFR-AUT-01")
class GovernanceSecurityMatrixTest {

    private static final String RUN = "/api/v1/workflows/00000000-0000-0000-0000-000000000001";

    @Autowired private MockMvc mvc;

    static Stream<Arguments> refusedRoles() {
        return Stream.of(
                Arguments.of("POST", "/api/v1/workflows", Tokens.CONSUMER),
                Arguments.of("POST", "/api/v1/workflows", Tokens.APPROVER),
                Arguments.of("POST", "/api/v1/workflows", Tokens.AUDITOR),
                Arguments.of("POST", RUN + "/gates/ARCHITECTURE_APPROVAL/decision", Tokens.REQUESTER),
                Arguments.of("POST", RUN + "/gates/ARCHITECTURE_APPROVAL/decision", Tokens.AUDITOR),
                Arguments.of("POST", RUN + "/gates/ARCHITECTURE_APPROVAL/decision", Tokens.CONSUMER),
                Arguments.of("POST", RUN + "/clarifications", Tokens.REQUESTER),
                Arguments.of("POST", RUN + "/change-requests", Tokens.APPROVER),
                Arguments.of("POST", RUN + "/change-requests/x/decision", Tokens.REQUESTER),
                Arguments.of("POST", RUN + "/policy-exceptions", Tokens.AUDITOR),
                Arguments.of("POST", RUN + "/policy-exceptions/x/decision", Tokens.REQUESTER),
                Arguments.of("POST", RUN + "/pause", Tokens.APPROVER),
                Arguments.of("POST", RUN + "/resume", Tokens.REQUESTER),
                Arguments.of("POST", RUN + "/safe-stop", Tokens.AUDITOR),
                Arguments.of("GET", "/api/v1/workflows", Tokens.CONSUMER),
                Arguments.of("GET", RUN + "/timeline", Tokens.CONSUMER),
                Arguments.of("GET", "/api/v1/reliability/metrics", Tokens.CONSUMER));
    }

    static Stream<Arguments> endpoints() {
        return refusedRoles().map(a -> Arguments.of(a.get()[0], a.get()[1])).distinct();
    }

    private MockHttpServletRequestBuilder request(String method, String path) {
        return "GET".equals(method) ? get(path)
                : post(path).contentType(MediaType.APPLICATION_JSON).content("{}");
    }

    @ParameterizedTest(name = "{0} {1} without a token → 401")
    @MethodSource("endpoints")
    void unauthenticatedCallersAreRefused(String method, String path) throws Exception {
        mvc.perform(request(method, path))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @ParameterizedTest(name = "{0} {1} as {2} → 403")
    @MethodSource("refusedRoles")
    void principalsWithoutTheRoleAreRefused(String method, String path, String token) throws Exception {
        mvc.perform(request(method, path).header(HttpHeaders.AUTHORIZATION, Tokens.bearer(token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
}
