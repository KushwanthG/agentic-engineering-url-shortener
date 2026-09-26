package com.agentic.sdlc.contract;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.agentic.sdlc.support.IntegrationTest;

/** T014: proves the openapi-request-validator 3.0.0 harness validates a real response against the contract. */
@IntegrationTest
@Tag("NFR-CHG-01")
class ContractHarnessSpikeTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void unauthenticatedCreateLinkResponseConformsToTheContract() throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/links").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com\"}"))
                .andExpect(status().isUnauthorized())
                .andReturn();
        OpenApiContract.assertResponseMatches("POST", "/api/v1/links", result);
    }
}
