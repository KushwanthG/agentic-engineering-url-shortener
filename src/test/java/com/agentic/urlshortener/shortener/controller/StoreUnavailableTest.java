package com.agentic.urlshortener.shortener.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.agentic.urlshortener.shortener.repository.ShortLinkRepository;
import com.agentic.urlshortener.support.IntegrationTest;
import com.agentic.urlshortener.support.Tokens;

/** T030: an unavailable link store fails fast with a retryable 503 and turns readiness DOWN (FR-OPS-01/02). */
@IntegrationTest
@Tag("FR-OPS-01")
@Tag("FR-OPS-02")
class StoreUnavailableTest {

    private static final long FAIL_FAST_MILLIS = 2_000;

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ShortLinkRepository links;

    @BeforeEach
    void storeIsDown() {
        DataAccessResourceFailureException down = new DataAccessResourceFailureException("simulated link store outage (test)");
        given(links.findByCode(any())).willThrow(down);
        given(links.saveAndFlush(any())).willThrow(down);
        given(links.probe()).willThrow(down);
    }

    @Test
    void redirectFailsFastWithRetryable503() throws Exception {
        long start = System.nanoTime();
        mvc.perform(get("/abc1234"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.code").value("STORE_UNAVAILABLE"));
        assertThat((System.nanoTime() - start) / 1_000_000).isLessThan(FAIL_FAST_MILLIS);
    }

    @Test
    void creationFailsFastWithRetryable503() throws Exception {
        long start = System.nanoTime();
        mvc.perform(post("/api/v1/links").header(HttpHeaders.AUTHORIZATION, Tokens.bearer(Tokens.CONSUMER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"url\":\"https://example.com\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("STORE_UNAVAILABLE"));
        assertThat((System.nanoTime() - start) / 1_000_000).isLessThan(FAIL_FAST_MILLIS);
    }

    @Test
    void readinessIsDownWhileLivenessStaysUp() throws Exception {
        mvc.perform(get("/actuator/health/readiness")).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"));
        mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
