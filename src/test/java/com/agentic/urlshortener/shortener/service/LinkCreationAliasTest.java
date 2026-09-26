package com.agentic.urlshortener.shortener.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.agentic.urlshortener.common.exception.ApiException;
import com.agentic.urlshortener.common.exception.ErrorCode;
import com.agentic.urlshortener.shortener.domain.Capability;
import com.agentic.urlshortener.shortener.dto.CreateLinkCommand;
import com.agentic.urlshortener.shortener.dto.CreatedLink;
import com.agentic.urlshortener.shortener.dto.Resolution;
import com.agentic.urlshortener.shortener.repository.ShortLinkRepository;
import com.agentic.urlshortener.support.IntegrationTest;

/** T054: the custom-alias path of link creation (GF-001 AC-1..AC-6), exercised in preview mode. */
@IntegrationTest
@Tag("FR-CAP-02")
@Tag("FR-CAP-01")
@Tag("SCN-A")
class LinkCreationAliasTest {

    @Autowired
    private LinkCreationService creation;

    @Autowired
    private RedirectService redirects;

    @Autowired
    private CapabilityService capabilities;

    @Autowired
    private ShortLinkRepository links;

    private <T> T released(java.util.function.Supplier<T> action) {
        return capabilities.withPreview(Capability.CUSTOM_ALIAS, Map.of(), action);
    }

    private static CreateLinkCommand command(String url, String alias) {
        return new CreateLinkCommand(url, null, alias, null, "demo-consumer", null);
    }

    private static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void aliasBecomesTheCodeAndResolves() {
        String alias = unique("spring-sale");
        CreatedLink created = released(() -> creation.create(command("https://example.com/spring", alias)));
        assertThat(created.view().code()).isEqualTo(alias);
        assertThat(created.view().customAlias()).isTrue();
        assertThat(links.findByCode(alias).orElseThrow().isCustomAlias()).isTrue();
        assertThat(redirects.resolve(alias, null)).isEqualTo(Resolution.redirect("https://example.com/spring"));
    }

    @Test
    void anAliasInUseConflicts() {
        String alias = unique("taken");
        released(() -> creation.create(command("https://example.com/a", alias)));
        assertThatThrownBy(() -> released(() -> creation.create(command("https://example.com/b", alias))))
                .isInstanceOf(ApiException.class).extracting(e -> ((ApiException) e).code()).isEqualTo(ErrorCode.ALIAS_CONFLICT);
    }

    @Test
    void anAliasEqualToAGeneratedCodeConflicts() {
        String generated = creation.create(command("https://example.com/generated", null)).view().code();
        assertThatThrownBy(() -> released(() -> creation.create(command("https://example.com/c", generated))))
                .isInstanceOf(ApiException.class).extracting(e -> ((ApiException) e).code()).isEqualTo(ErrorCode.ALIAS_CONFLICT);
    }

    @Test
    void invalidAndReservedAliasesAreRejected() {
        assertThatThrownBy(() -> released(() -> creation.create(command("https://example.com", "bad alias"))))
                .extracting(e -> ((ApiException) e).code()).isEqualTo(ErrorCode.INVALID_ALIAS);
        assertThatThrownBy(() -> released(() -> creation.create(command("https://example.com", "ab"))))
                .extracting(e -> ((ApiException) e).code()).isEqualTo(ErrorCode.INVALID_ALIAS);
        assertThatThrownBy(() -> released(() -> creation.create(command("https://example.com", "api"))))
                .extracting(e -> ((ApiException) e).code()).isEqualTo(ErrorCode.RESERVED_ALIAS);
    }

    @Test
    void unreleasedCapabilityRejectsAliases() {
        assertThatThrownBy(() -> creation.create(command("https://example.com", unique("early"))))
                .extracting(e -> ((ApiException) e).code()).isEqualTo(ErrorCode.CAPABILITY_NOT_AVAILABLE);
    }

    @Test
    void generatedLinksAreNotMarkedAsAliases() {
        CreatedLink created = creation.create(command("https://example.com/plain", null));
        assertThat(created.view().customAlias()).isFalse();
    }
}
