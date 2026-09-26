package com.agentic.urlshortener.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.Map;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.agentic.urlshortener.common.util.CanonicalJson;
import com.agentic.urlshortener.shortener.repository.ShortLinkRepository;
import com.agentic.urlshortener.support.IntegrationTest;
import com.agentic.urlshortener.support.MaliciousUrlCatalog;
import com.agentic.urlshortener.support.Tokens;

/** T031: the whole malicious-URL catalog is rejected through the public HTTP API and creates nothing (NFR-SEC-01). */
@IntegrationTest
@Tag("NFR-SEC-01")
@Tag("FR-LNK-03")
class UrlSecurityCatalogIT {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ShortLinkRepository links;

    @Test
    void everyCatalogEntryIsRejectedWith400() throws Exception {
        long before = links.count();
        int checked = 0;
        for (MaliciousUrlCatalog.Entry entry : MaliciousUrlCatalog.entries()) {
            String url = entry.url();
            String expectedCode = entry.code().name();
            MvcResult result = mvc.perform(post("/api/v1/links")
                            .header(HttpHeaders.AUTHORIZATION, Tokens.bearer(Tokens.CONSUMER))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(CanonicalJson.write(Map.of("url", url))))
                    .andReturn();
            assertThat(result.getResponse().getStatus()).as(url).isEqualTo(400);
            assertThat(CanonicalJson.parse(result.getResponse().getContentAsString()).get("code").asString())
                    .as(url).isEqualTo(expectedCode);
            checked++;
        }
        assertThat(checked).isGreaterThanOrEqualTo(25);
        assertThat(links.count()).isEqualTo(before);
    }
}
