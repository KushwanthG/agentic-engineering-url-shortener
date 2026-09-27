package com.agentic.urlshortener.shortener.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.agentic.urlshortener.shortener.domain.Capability;
import com.agentic.urlshortener.support.IntegrationTest;
import com.agentic.urlshortener.support.Tokens;

/** T043: a preview enables an unreleased capability only inside the scoped in-process call (ADR-018). */
@IntegrationTest
@Tag("FR-CAP-01")
@Tag("FR-ORC-16")
class PreviewIsolationTest {

    @Autowired
    private CapabilityService capabilities;

    @Autowired
    private MockMvc mvc;

    @Test
    void previewIsVisibleOnlyInsideTheScopedCall() {
        assertThat(capabilities.isReleased(Capability.CUSTOM_ALIAS)).isFalse();
        boolean inside = capabilities.withPreview(Capability.CUSTOM_ALIAS, Map.of(), () -> capabilities.isReleased(Capability.CUSTOM_ALIAS));
        assertThat(inside).isTrue();
        assertThat(capabilities.isReleased(Capability.CUSTOM_ALIAS)).isFalse();
    }

    @Test
    void previewParametersAreVisibleInside() {
        Map<String, Object> inside = capabilities.withPreview(Capability.DEFAULT_EXPIRY, Map.of("defaultExpiryDays", 30),
                () -> capabilities.parameters(Capability.DEFAULT_EXPIRY));
        assertThat(inside).containsEntry("defaultExpiryDays", 30);
        assertThat(capabilities.parameters(Capability.DEFAULT_EXPIRY)).isEmpty();
    }

    @Test
    void previewIsClearedWhenTheActionFails() {
        assertThatThrownBy(() -> capabilities.withPreview(Capability.CUSTOM_ALIAS, Map.of(), () -> {
            throw new IllegalStateException("probe failed");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(capabilities.isReleased(Capability.CUSTOM_ALIAS)).isFalse();
    }

    @Test
    void concurrentRequestsAndHttpCannotSeeAPreview() throws Exception {
        CountDownLatch insidePreview = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CompletableFuture<Void> previewing = CompletableFuture.runAsync(() -> capabilities.withPreview(Capability.CUSTOM_ALIAS, Map.of(), () -> {
            insidePreview.countDown();
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return null;
        }));
        assertThat(insidePreview.await(10, TimeUnit.SECONDS)).isTrue();
        try {
            assertThat(capabilities.isReleased(Capability.CUSTOM_ALIAS)).isFalse();
            int status = mvc.perform(post("/api/v1/links").header(HttpHeaders.AUTHORIZATION, Tokens.bearer(Tokens.CONSUMER))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"url\":\"https://example.com\",\"alias\":\"preview-leak\"}"))
                    .andReturn().getResponse().getStatus();
            assertThat(status).isEqualTo(422);
        } finally {
            release.countDown();
            previewing.get(10, TimeUnit.SECONDS);
        }
    }
}
