package com.agentic.sdlc.platform.security;

import static com.agentic.sdlc.support.Tokens.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.agentic.sdlc.support.IntegrationTest;
import com.agentic.sdlc.support.Tokens;

/** T011: credentials never reach the logs, even when authentication fails (NFR-SEC-03). */
@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
@Tag("NFR-SEC-03")
class TokenNotLoggedTest {

    private static final String FORGED = "not-a-real-token-123";

    @Autowired
    private MockMvc mvc;

    @Test
    void rejectedAndAcceptedTokensAreNeverLogged(CapturedOutput output) throws Exception {
        mvc.perform(post("/api/v1/links").header(HttpHeaders.AUTHORIZATION, "Bearer " + FORGED)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/workflows").header(HttpHeaders.AUTHORIZATION, bearer(Tokens.AUDITOR)));

        assertThat(output.getAll()).contains("Rejected bearer token");
        assertThat(output.getAll()).doesNotContain(FORGED).doesNotContain(Tokens.AUDITOR);
    }
}
