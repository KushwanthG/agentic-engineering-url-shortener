package com.agentic.urlshortener.shortener.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.agentic.urlshortener.common.util.CanonicalJson;
import com.agentic.urlshortener.shortener.domain.Capability;
import com.agentic.urlshortener.support.IntegrationTest;
import com.agentic.urlshortener.support.Tokens;

/**
 * T087 (FR-CAP-03, FR-ANL-04, FR-CAP-01; BF-001 AC-1, AC-2, AC-4, AC-5, AC-6): click-limited links
 * redirect at most {@code maxClicks} times, then resolve as expired; links without a limit are
 * unaffected; the limit needs the released capability and a value in 1..1,000,000; a limited link
 * whose click cannot be recorded is refused (fail closed) while unlimited links stay fail-open; a
 * stored limit stays enforced after the capability is withdrawn.
 */
@IntegrationTest
@Tag("FR-CAP-03")
@Tag("FR-ANL-04")
@Tag("FR-CAP-01")
@Tag("SCN-B")
class ClickLimitTest {

    @Autowired private MockMvc mvc;
    @Autowired private CapabilityService capabilities;
    @MockitoSpyBean private ClickRecorder clickRecorder;

    @AfterEach
    void withdraw() {
        reset(clickRecorder);
        capabilities.setRelease(Capability.CLICK_LIMIT, false, Map.of(), "test", null, "test cleanup");
    }

    private void release() {
        capabilities.setRelease(Capability.CLICK_LIMIT, true, Map.of(), "test", null, "test release");
    }

    private ResultActions create(String body) throws Exception {
        return mvc.perform(post("/api/v1/links").header(HttpHeaders.AUTHORIZATION, Tokens.bearer(Tokens.CONSUMER))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String createLimited(long maxClicks) throws Exception {
        String body = create("{\"url\":\"https://example.com/limited\",\"maxClicks\":" + maxClicks + "}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return CanonicalJson.parse(body).path("code").asString();
    }

    private ResultActions resolve(String code) throws Exception {
        return mvc.perform(get("/" + code));
    }

    @Test
    void aLimitNeedsTheReleasedCapability() throws Exception {
        create("{\"url\":\"https://example.com/limited\",\"maxClicks\":2}")
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("CAPABILITY_NOT_AVAILABLE"));
    }

    @Test
    void aLinkWithTwoClicksRedirectsTwiceThenExpires() throws Exception {
        release();
        String body = create("{\"url\":\"https://example.com/twice\",\"maxClicks\":2}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.maxClicks").value(2))
                .andReturn().getResponse().getContentAsString();
        String code = CanonicalJson.parse(body).path("code").asString();

        resolve(code).andExpect(status().isFound());
        resolve(code).andExpect(status().isFound());
        resolve(code).andExpect(status().isGone()).andExpect(jsonPath("$.code").value("LINK_EXPIRED"));
        mvc.perform(get("/api/v1/links/" + code).header(HttpHeaders.AUTHORIZATION, Tokens.bearer(Tokens.CONSUMER)))
                .andExpect(jsonPath("$.maxClicks").value(2)).andExpect(jsonPath("$.status").value("EXPIRED"));
        mvc.perform(get("/api/v1/links/" + code + "/stats").header(HttpHeaders.AUTHORIZATION, Tokens.bearer(Tokens.CONSUMER)))
                .andExpect(jsonPath("$.totalClicks").value(2));
    }

    @Test
    void theLimitMustBeBetweenOneAndOneMillion() throws Exception {
        release();
        for (String invalid : new String[] { "0", "-1", "1000001" }) {
            create("{\"url\":\"https://example.com/limited\",\"maxClicks\":" + invalid + "}")
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_CLICK_LIMIT"));
        }
        createLimited(1);
        createLimited(1_000_000);
    }

    @Test
    void linksWithoutALimitAreUnaffected() throws Exception {
        release();
        String body = create("{\"url\":\"https://example.com/unlimited\"}").andExpect(status().isCreated())
                .andExpect(jsonPath("$.maxClicks").doesNotExist()).andReturn().getResponse().getContentAsString();
        String code = CanonicalJson.parse(body).path("code").asString();
        for (int i = 0; i < 5; i++) {
            resolve(code).andExpect(status().isFound());
        }
    }

    @Test
    void aLimitedLinkFailsClosedWhileAnUnlimitedLinkFailsOpen() throws Exception {
        release();
        String limited = createLimited(5);
        String unlimited = CanonicalJson.parse(create("{\"url\":\"https://example.com/open\"}").andReturn().getResponse()
                .getContentAsString()).path("code").asString();
        doThrow(new DataAccessResourceFailureException("click store down")).when(clickRecorder)
                .recordWithinLimit(anyLong(), anyBoolean(), any(), any());
        doThrow(new DataAccessResourceFailureException("click store down")).when(clickRecorder).record(anyLong(), any(), any());

        resolve(limited).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("STORE_UNAVAILABLE"));
        resolve(unlimited).andExpect(status().isFound());
    }

    @Test
    void aStoredLimitStaysEnforcedAfterTheCapabilityIsWithdrawn() throws Exception {
        release();
        String code = createLimited(1);
        capabilities.setRelease(Capability.CLICK_LIMIT, false, Map.of(), "test", null, "withdrawn");

        resolve(code).andExpect(status().isFound());
        resolve(code).andExpect(status().isGone());
        create("{\"url\":\"https://example.com/limited\",\"maxClicks\":1}").andExpect(status().isUnprocessableContent());
    }
}
