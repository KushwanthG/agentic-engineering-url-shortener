package com.agentic.urlshortener.shortener.controller;

import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.agentic.urlshortener.common.util.CanonicalJson;
import com.agentic.urlshortener.support.IntegrationTest;
import com.agentic.urlshortener.support.Tokens;

/** T027: the links API over HTTP (creation, metadata, analytics, errors, idempotency). */
@IntegrationTest
@Tag("FR-LNK-01")
@Tag("FR-LNK-11")
@Tag("FR-LNK-13")
@Tag("FR-ANL-03")
@Tag("FR-OPS-03")
class LinkControllerTest {

    @Autowired
    private MockMvc mvc;

    private ResultActions create(String body, String token, String idempotencyKey) throws Exception {
        var request = post("/api/v1/links").contentType(MediaType.APPLICATION_JSON).content(body);
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, Tokens.bearer(token));
        }
        if (idempotencyKey != null) {
            request.header("Idempotency-Key", idempotencyKey);
        }
        return mvc.perform(request);
    }

    private String createdCode(String url) throws Exception {
        String json = create("{\"url\":\"" + url + "\"}", Tokens.CONSUMER, null)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return CanonicalJson.parse(json).get("code").asString();
    }

    @Test
    void createsALinkWithLocationAndBody() throws Exception {
        create("{\"url\":\"HTTPS://Example.COM/docs?x=1\"}", Tokens.CONSUMER, null)
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, matchesPattern("/api/v1/links/[0-9A-Za-z]{7}")))
                .andExpect(header().doesNotExist("Idempotency-Replayed"))
                .andExpect(jsonPath("$.code").value(matchesPattern("[0-9A-Za-z]{7}")))
                .andExpect(jsonPath("$.shortUrl").value(startsWith("http://localhost:8080/")))
                .andExpect(jsonPath("$.targetUrl").value("https://example.com/docs?x=1"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.customAlias").value(false))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.expiresAt").doesNotExist());
    }

    @Test
    void rejectsInvalidUrlsWithTheirSpecificCode() throws Exception {
        String[][] cases = {
                {"javascript:alert(1)", "URL_SCHEME_NOT_ALLOWED"},
                {"https://u:p@example.com/", "URL_CREDENTIALS_NOT_ALLOWED"},
                {"https:///x", "URL_HOST_MISSING"},
                {"http://127.0.0.1/", "URL_HOST_NOT_ALLOWED"},
                {"https://example.com/" + "a".repeat(2100), "URL_TOO_LONG"},
                {"not a url", "URL_INVALID"}};
        for (String[] c : cases) {
            create("{\"url\":\"" + c[0] + "\"}", Tokens.CONSUMER, null)
                    .andExpect(status().isBadRequest())
                    .andExpect(header().string(HttpHeaders.CONTENT_TYPE, startsWith("application/problem+json")))
                    .andExpect(jsonPath("$.code").value(c[1]))
                    .andExpect(jsonPath("$.correlationId").exists());
        }
    }

    @Test
    void missingUrlAndMalformedExpiryAreValidationErrors() throws Exception {
        create("{}", Tokens.CONSUMER, null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        create("{\"url\":\"https://example.com\",\"expiresAt\":\"tomorrow\"}", Tokens.CONSUMER, null)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        create("{\"url\":\"https://example.com\",\"expiresAt\":\"2000-01-01T00:00:00Z\"}", Tokens.CONSUMER, null)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_EXPIRY"));
    }

    @Test
    void requiresAnAuthenticatedApiConsumer() throws Exception {
        create("{\"url\":\"https://example.com\"}", null, null)
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        create("{\"url\":\"https://example.com\"}", "wrong-token", null)
                .andExpect(status().isUnauthorized());
        create("{\"url\":\"https://example.com\"}", Tokens.REQUESTER, null)
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(get("/api/v1/links/abc1234")).andExpect(status().isUnauthorized());
    }

    @Test
    void replaysAnIdempotentRequestAndRejectsKeyReuse() throws Exception {
        String key = "k-" + UUID.randomUUID();
        String first = create("{\"url\":\"https://example.com/idem\"}", Tokens.CONSUMER, key)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String replay = create("{\"url\":\"https://example.com/idem\"}", Tokens.CONSUMER, key)
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotency-Replayed", "true"))
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(CanonicalJson.parse(replay)).isEqualTo(CanonicalJson.parse(first));

        create("{\"url\":\"https://example.com/other\"}", Tokens.CONSUMER, key)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
        create("{\"url\":\"https://example.com\"}", Tokens.CONSUMER, "bad key")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_IDEMPOTENCY_KEY"));
    }

    @Test
    void unreleasedCapabilityInputsAreRejected() throws Exception {
        create("{\"url\":\"https://example.com\",\"alias\":\"spring-sale\"}", Tokens.CONSUMER, null)
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("CAPABILITY_NOT_AVAILABLE"));
        create("{\"url\":\"https://example.com\",\"maxClicks\":3}", Tokens.CONSUMER, null)
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("CAPABILITY_NOT_AVAILABLE"));
    }

    @Test
    void returnsMetadataAndNotFound() throws Exception {
        String code = createdCode("https://example.com/meta");
        mvc.perform(get("/api/v1/links/" + code).header(HttpHeaders.AUTHORIZATION, Tokens.bearer(Tokens.CONSUMER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.targetUrl").value("https://example.com/meta"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        mvc.perform(get("/api/v1/links/nosuch1").header(HttpHeaders.AUTHORIZATION, Tokens.bearer(Tokens.CONSUMER)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("LINK_NOT_FOUND"));
    }

    @Test
    void statsReflectEveryResolution() throws Exception {
        String code = createdCode("https://example.com/stats");
        for (int i = 0; i < 3; i++) {
            mvc.perform(get("/" + code)).andExpect(status().isFound());
        }
        mvc.perform(get("/api/v1/links/" + code + "/stats").header(HttpHeaders.AUTHORIZATION, Tokens.bearer(Tokens.CONSUMER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.totalClicks").value(3))
                .andExpect(jsonPath("$.lastAccessedAt").exists())
                .andExpect(jsonPath("$.windowDays").value(30))
                .andExpect(jsonPath("$.daily[0].clicks").value(3));
    }
}
