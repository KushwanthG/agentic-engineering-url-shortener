package com.agentic.urlshortener.shortener.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.agentic.urlshortener.support.IntegrationTest;
import com.agentic.urlshortener.support.Tokens;

/** T027/T029: creation per consumer and not-found outcomes per client address are rate-limited (429 + Retry-After). */
@IntegrationTest
@TestPropertySource(properties = {
        "app.shortener.rate-limit.creation-per-minute=3",
        "app.shortener.rate-limit.not-found-per-minute=4"})
@Tag("FR-LNK-10")
@Tag("FR-RED-05")
@Tag("NFR-SEC-04")
class RateLimitHttpTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void creationIsLimitedPerConsumer() throws Exception {
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/v1/links").header(HttpHeaders.AUTHORIZATION, Tokens.bearer(Tokens.CONSUMER))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"url\":\"https://example.com/" + i + "\"}"))
                    .andExpect(status().isCreated());
        }
        mvc.perform(post("/api/v1/links").header(HttpHeaders.AUTHORIZATION, Tokens.bearer(Tokens.CONSUMER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"url\":\"https://example.com/over\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }

    @Test
    void notFoundOutcomesAreThrottledPerClientAddress() throws Exception {
        for (int i = 0; i < 4; i++) {
            mvc.perform(get("/probe" + i).with(r -> { r.setRemoteAddr("198.51.100.7"); return r; }))
                    .andExpect(status().isNotFound());
        }
        mvc.perform(get("/probe9").with(r -> { r.setRemoteAddr("198.51.100.7"); return r; }))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
        mvc.perform(get("/probe9").with(r -> { r.setRemoteAddr("198.51.100.8"); return r; }))
                .andExpect(status().isNotFound());
    }
}
