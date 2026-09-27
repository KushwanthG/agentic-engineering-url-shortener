package com.agentic.urlshortener.shortener.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import com.agentic.urlshortener.shortener.dto.CreateLinkCommand;
import com.agentic.urlshortener.shortener.service.LinkCreationService;
import com.agentic.urlshortener.support.ControllableTestConfig;
import com.agentic.urlshortener.support.IntegrationTest;
import com.agentic.urlshortener.support.MutableClock;

/** T029: public redirect resolution over HTTP. */
@IntegrationTest
@Import(ControllableTestConfig.class)
@Tag("FR-RED-01")
@Tag("FR-RED-02")
@Tag("FR-RED-03")
@Tag("FR-RED-04")
class RedirectControllerTest {

    private static final Instant NOW = Instant.parse("2026-09-26T10:00:00Z");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private LinkCreationService creation;

    @Autowired
    private MutableClock clock;

    @BeforeEach
    void reset() {
        clock.set(NOW);
    }

    private String create(String url, Instant expiresAt) {
        return creation.create(new CreateLinkCommand(url, expiresAt, null, null, "demo-consumer", null)).view().code();
    }

    @Test
    void redirectsWithoutCredentialsAndIsNotCacheable() throws Exception {
        String code = create("https://example.com/landing?utm=x", null);
        mvc.perform(get("/" + code))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, "https://example.com/landing?utm=x"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
    }

    @Test
    void unknownCodeIsNotFound() throws Exception {
        mvc.perform(get("/nosuchcode"))
                .andExpect(status().isNotFound())
                .andExpect(header().doesNotExist(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.code").value("LINK_NOT_FOUND"));
    }

    @Test
    void malformedCodesAreNotFound() throws Exception {
        mvc.perform(get("/ab")).andExpect(status().isNotFound()).andExpect(header().doesNotExist(HttpHeaders.LOCATION));
        mvc.perform(get("/" + "a".repeat(33))).andExpect(status().isNotFound());
    }

    @Test
    void expiredLinkIsGoneNotRedirected() throws Exception {
        String code = create("https://example.com/soon", NOW.plus(Duration.ofMinutes(5)));
        clock.advance(Duration.ofMinutes(6));
        mvc.perform(get("/" + code))
                .andExpect(status().isGone())
                .andExpect(header().doesNotExist(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.code").value("LINK_EXPIRED"));
    }
}
