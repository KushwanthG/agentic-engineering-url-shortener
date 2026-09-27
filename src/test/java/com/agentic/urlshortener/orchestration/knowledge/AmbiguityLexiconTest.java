package com.agentic.urlshortener.orchestration.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** T045: text-level ambiguity detection; a well-defined requirement must stay clean (no false positives). */
@Tag("FR-ORC-13")
@Tag("FR-GOV-08")
@Tag("SCN-A")
@Tag("SCN-C")
class AmbiguityLexiconTest {

    private static final String GF_001 = """
            Custom aliases for short links.
            As an API consumer, I want to optionally choose a custom alias when I create a short link so that I can share memorable links.
            Given the capability is released, when a consumer creates a link for a valid URL with the alias `spring-sale`, then the link is created and its short code is `spring-sale`.
            Given the alias `spring-sale` is in use, when another link is created with the alias `spring-sale`, then creation is rejected with error code `ALIAS_CONFLICT`.
            Given an alias containing characters other than letters, digits, hyphen, or underscore, when a link is created, then creation is rejected with error code `INVALID_ALIAS`.
            Given an alias shorter than 3 or longer than 32 characters, when a link is created, then creation is rejected with error code `INVALID_ALIAS`.
            Given a reserved alias such as `api`, when a link is created, then creation is rejected with error code `RESERVED_ALIAS`.
            Given a link created with an alias, when the alias is resolved, then the client is redirected to the target URL.
            Aliases are case-sensitive, like generated codes, and share the code namespace.
            """;

    private static final String AMB_001 = """
            Better link expiry.
            Links should expire after a while so old links don't pile up, but premium users' links should never expire. Also make the analytics better.
            """;

    private final AmbiguityLexicon lexicon = AmbiguityLexicon.load();

    @Test
    void wellDefinedRequirementHasNoTextAmbiguity() {
        assertThat(lexicon.detect(GF_001)).isEmpty();
    }

    @Test
    void detectsTheVagueTermUndefinedConceptConflictAndUnboundedScopeOfAmb001() {
        List<AmbiguityLexicon.Finding> findings = lexicon.detect(AMB_001);
        assertThat(findings).extracting(AmbiguityLexicon.Finding::type)
                .containsExactlyInAnyOrder("VAGUE_TERM", "UNDEFINED_CONCEPT", "CONFLICT", "UNBOUNDED_SCOPE");
        assertThat(finding(findings, "VAGUE_TERM").text()).isEqualTo("after a while");
        assertThat(finding(findings, "VAGUE_TERM").severity()).isEqualTo("HIGH");
        assertThat(finding(findings, "UNDEFINED_CONCEPT").text()).containsIgnoringCase("premium users");
        assertThat(finding(findings, "CONFLICT").severity()).isEqualTo("HIGH");
        assertThat(finding(findings, "UNBOUNDED_SCOPE").text()).containsIgnoringCase("make the analytics better");
        assertThat(finding(findings, "UNBOUNDED_SCOPE").severity()).isEqualTo("MEDIUM");
        assertThat(findings).allSatisfy(f -> assertThat(f.blocking()).isTrue());
    }

    @Test
    void everyAmbiguityTypeHasAQuestionTemplateWithOptionsAndImpacts() {
        for (String type : List.of("VAGUE_TERM", "UNDEFINED_CONCEPT", "CONFLICT", "UNBOUNDED_SCOPE", "MISSING_ACCEPTANCE_CRITERIA",
                "UNSPECIFIED_TYPE", "UNKNOWN_CAPABILITY")) {
            AmbiguityLexicon.QuestionTemplate template = lexicon.question(type).orElseThrow();
            assertThat(template.question()).as(type).isNotBlank();
            assertThat(template.options()).as(type).isNotEmpty()
                    .allSatisfy(o -> assertThat(o.impact()).isNotBlank());
        }
    }

    private static AmbiguityLexicon.Finding finding(List<AmbiguityLexicon.Finding> findings, String type) {
        return findings.stream().filter(f -> f.type().equals(type)).findFirst().orElseThrow();
    }
}
