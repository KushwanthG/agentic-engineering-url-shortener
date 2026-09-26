package com.agentic.urlshortener.orchestration.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** T044: the capability catalog that grounds requirement analysis, design, verification, and release. */
@Tag("FR-ORC-13")
@Tag("FR-ORC-14")
@Tag("SCN-A")
@Tag("SCN-B")
@Tag("SCN-C")
class CapabilityCatalogTest {

    private final CapabilityCatalog catalog = CapabilityCatalog.load();

    @Test
    void containsTheBaselineAndTheScenarioCapabilities() {
        assertThat(catalog.all()).extracting(CapabilityEntry::id)
                .containsExactlyInAnyOrder("link-creation", "redirect", "expiration", "analytics", "custom-alias", "click-limit",
                        "default-expiry");
        assertThat(catalog.all()).filteredOn(CapabilityEntry::baseline).extracting(CapabilityEntry::id)
                .containsExactlyInAnyOrder("link-creation", "redirect", "expiration", "analytics");
    }

    @Test
    void matchesTheGreenfieldRequirementToCustomAlias() {
        List<CapabilityCatalog.Match> matches = catalog.match(
                "Custom aliases for short links. As an API consumer, I want to optionally choose a custom alias when I create a short link");
        assertThat(matches).extracting(m -> m.capability().id()).contains("custom-alias");
        CapabilityCatalog.Match alias = matches.stream().filter(m -> m.capability().id().equals("custom-alias")).findFirst().orElseThrow();
        assertThat(alias.matchedTerms()).contains("alias");
    }

    @Test
    void matchesTheBrownfieldRequirementToClickLimit() {
        assertThat(catalog.match("I want to limit how many times a short link can be used so that I can share one-time links"))
                .extracting(m -> m.capability().id()).contains("click-limit");
    }

    @Test
    void scenarioCapabilitiesDeclareEverythingTheAgentsNeed() {
        for (String id : List.of("custom-alias", "click-limit", "default-expiry")) {
            CapabilityEntry entry = catalog.get(id).orElseThrow();
            assertThat(entry.components()).as(id).isNotEmpty();
            assertThat(entry.release()).as(id).isNotNull();
            assertThat(entry.rollback()).as(id).isNotBlank();
            assertThat(entry.acceptanceProbes()).as(id).isNotEmpty();
            assertThat(entry.threats()).as(id).isNotEmpty()
                    .allSatisfy(t -> assertThat(t.severity().equals("HIGH") ? t.verifiedBy() : List.of("n/a")).isNotEmpty());
            assertThat(entry.docAnchors()).as(id).isNotEmpty();
            assertThat(entry.tasks()).as(id).isNotEmpty();
        }
    }

    @Test
    void customAliasDeclaresItsContractAndSchemaDelta() {
        CapabilityEntry alias = catalog.get("custom-alias").orElseThrow();
        assertThat(alias.changeType()).isEqualTo("NEW_CAPABILITY");
        assertThat(alias.api().contractVersion()).isEqualTo("1.1.0");
        assertThat(alias.api().fields()).containsExactlyInAnyOrder("alias", "customAlias");
        assertThat(alias.api().errorCodes()).containsExactlyInAnyOrder("INVALID_ALIAS", "RESERVED_ALIAS", "ALIAS_CONFLICT");
        assertThat(alias.schema().migration()).isEqualTo("3");
        assertThat(catalog.get("click-limit").orElseThrow().changeType()).isEqualTo("CHANGE_TO_EXISTING");
        assertThat(catalog.get("default-expiry").orElseThrow().release().parameters()).containsKey("defaultExpiryDays");
    }

    @Test
    void unknownTextMatchesNothing() {
        assertThat(catalog.match("Integrate with the payroll system and send monthly invoices")).isEmpty();
    }
}
