package com.agentic.urlshortener.contract;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.agentic.urlshortener.shortener.dto.CreateLinkCommand;
import com.agentic.urlshortener.shortener.repository.ShortLinkRepository;
import com.agentic.urlshortener.shortener.service.LinkCreationService;
import com.agentic.urlshortener.support.ControllableTestConfig;
import com.agentic.urlshortener.support.IntegrationTest;
import com.agentic.urlshortener.support.MutableClock;
import com.agentic.urlshortener.support.Tokens;

/** T032: every links and redirect response is validated against {@code openapi.yaml} (NFR-CHG-01). */
@Tag("NFR-CHG-01")
@Tag("FR-LNK-01")
@Tag("FR-RED-01")
class LinkApiContractTest {

    private static MvcResult createLink(MockMvc mvc, String body, String token, String key) throws Exception {
        var request = post("/api/v1/links").contentType(MediaType.APPLICATION_JSON).content(body);
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, Tokens.bearer(token));
        }
        if (key != null) {
            request.header("Idempotency-Key", key);
        }
        return mvc.perform(request).andReturn();
    }

    private static void assertContract(String method, String path, MvcResult result, int expectedStatus) throws Exception {
        org.assertj.core.api.Assertions.assertThat(result.getResponse().getStatus()).isEqualTo(expectedStatus);
        OpenApiContract.assertResponseMatches(method, path, result);
    }

    @Nested
    @IntegrationTest
    @Import(ControllableTestConfig.class)
    class NormalOperation {

        @Autowired
        private MockMvc mvc;

        @Autowired
        private LinkCreationService creation;

        @Autowired
        private MutableClock clock;

        @BeforeEach
        void reset() {
            clock.set(Instant.parse("2026-09-26T10:00:00Z"));
        }

        @Test
        void creationResponsesMatchTheContract() throws Exception {
            String key = "contract-" + UUID.randomUUID();
            assertContract("POST", "/api/v1/links",
                    createLink(mvc, "{\"url\":\"https://example.com/c\",\"expiresAt\":\"2026-12-31T23:59:59Z\"}", Tokens.CONSUMER, key), 201);
            assertContract("POST", "/api/v1/links",
                    createLink(mvc, "{\"url\":\"https://example.com/c\",\"expiresAt\":\"2026-12-31T23:59:59Z\"}", Tokens.CONSUMER, key), 201);
            assertContract("POST", "/api/v1/links", createLink(mvc, "{\"url\":\"javascript:x\"}", Tokens.CONSUMER, null), 400);
            assertContract("POST", "/api/v1/links", createLink(mvc, "{\"url\":\"https://example.com\"}", null, null), 401);
            assertContract("POST", "/api/v1/links", createLink(mvc, "{\"url\":\"https://example.com\"}", Tokens.AUDITOR, null), 403);
            assertContract("POST", "/api/v1/links", createLink(mvc, "{\"url\":\"https://example.com/d\"}", Tokens.CONSUMER, key), 409);
            assertContract("POST", "/api/v1/links",
                    createLink(mvc, "{\"url\":\"https://example.com\",\"alias\":\"spring-sale\"}", Tokens.CONSUMER, null), 422);
        }

        @Test
        void readAndRedirectResponsesMatchTheContract() throws Exception {
            String code = creation.create(new CreateLinkCommand("https://example.com/r", clock.instant().plus(Duration.ofHours(1)),
                    null, null, "demo-consumer", null)).view().code();
            String auth = Tokens.bearer(Tokens.CONSUMER);

            assertContract("GET", "/" + code, mvc.perform(get("/" + code)).andReturn(), 302);
            assertContract("GET", "/api/v1/links/" + code,
                    mvc.perform(get("/api/v1/links/" + code).header(HttpHeaders.AUTHORIZATION, auth)).andReturn(), 200);
            assertContract("GET", "/api/v1/links/" + code + "/stats",
                    mvc.perform(get("/api/v1/links/" + code + "/stats").header(HttpHeaders.AUTHORIZATION, auth)).andReturn(), 200);
            assertContract("GET", "/api/v1/links/missing1",
                    mvc.perform(get("/api/v1/links/missing1").header(HttpHeaders.AUTHORIZATION, auth)).andReturn(), 404);
            assertContract("GET", "/missing1", mvc.perform(get("/missing1")).andReturn(), 404);

            clock.advance(Duration.ofHours(2));
            assertContract("GET", "/" + code, mvc.perform(get("/" + code)).andReturn(), 410);
            assertContract("GET", "/api/v1/links/" + code,
                    mvc.perform(get("/api/v1/links/" + code).header(HttpHeaders.AUTHORIZATION, auth)).andReturn(), 200);
        }
    }

    @Nested
    @IntegrationTest
    @TestPropertySource(properties = {"app.shortener.rate-limit.creation-per-minute=1", "app.shortener.rate-limit.not-found-per-minute=1"})
    class RateLimited {

        @Autowired
        private MockMvc mvc;

        @Test
        void throttledResponsesMatchTheContract() throws Exception {
            createLink(mvc, "{\"url\":\"https://example.com/1\"}", Tokens.CONSUMER, null);
            assertContract("POST", "/api/v1/links", createLink(mvc, "{\"url\":\"https://example.com/2\"}", Tokens.CONSUMER, null), 429);
            mvc.perform(get("/nothere1")).andExpect(status().isNotFound());
            assertContract("GET", "/nothere2", mvc.perform(get("/nothere2")).andReturn(), 429);
        }
    }

    @Nested
    @IntegrationTest
    class StoreDown {

        @Autowired
        private MockMvc mvc;

        @MockitoBean
        private ShortLinkRepository links;

        @Test
        void unavailableResponsesMatchTheContract() throws Exception {
            given(links.findByCode(any())).willThrow(new DataAccessResourceFailureException("simulated outage (test)"));
            given(links.saveAndFlush(any())).willThrow(new DataAccessResourceFailureException("simulated outage (test)"));
            assertContract("GET", "/abc1234", mvc.perform(get("/abc1234")).andReturn(), 503);
            assertContract("POST", "/api/v1/links", createLink(mvc, "{\"url\":\"https://example.com\"}", Tokens.CONSUMER, null), 503);
        }
    }
}
