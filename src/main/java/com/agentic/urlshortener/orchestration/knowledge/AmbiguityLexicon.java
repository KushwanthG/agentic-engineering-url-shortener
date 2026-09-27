package com.agentic.urlshortener.orchestration.knowledge;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * Detects text-level ambiguity (vague terms, undefined concepts, conflicting rules, unbounded scope)
 * and supplies the clarification question templates (FR-GOV-08, ADR-017). The patterns are data in
 * {@code orchestration/ambiguity-lexicon.yaml}.
 */
@Component
public class AmbiguityLexicon {

    private static final String LOCATION = "orchestration/ambiguity-lexicon.yaml";

    /** One ambiguity found in the text; every text finding blocks until clarified. */
    public record Finding(String type, String text, String severity, boolean blocking, String explanation) {
    }

    public record Option(String optionId, String label, String impact) {
    }

    public record QuestionTemplate(String question, boolean allowFreeText, List<Option> options) {

        /** The question with {@code {text}} replaced by the ambiguous text. */
        public String render(String text) {
            return question.replace("{text}", text);
        }
    }

    record Term(String pattern, String severity, String explanation) {
    }

    record ConflictPair(String first, String second, String severity, String explanation) {
    }

    record LexiconFile(String version, List<Term> vagueTerms, List<Term> undefinedConcepts, List<ConflictPair> conflicts,
            List<Term> unboundedScope, Map<String, QuestionTemplate> questions) {
    }

    private final LexiconFile lexicon;

    public AmbiguityLexicon() {
        this.lexicon = YamlResources.read(LOCATION, LexiconFile.class);
    }

    public static AmbiguityLexicon load() {
        return new AmbiguityLexicon();
    }

    public String version() {
        return lexicon.version();
    }

    public List<Finding> detect(String text) {
        List<Finding> findings = new ArrayList<>();
        addTerms(findings, "VAGUE_TERM", lexicon.vagueTerms(), text);
        addTerms(findings, "UNDEFINED_CONCEPT", lexicon.undefinedConcepts(), text);
        for (ConflictPair pair : lexicon.conflicts()) {
            Matcher first = compile(pair.first()).matcher(text);
            Matcher second = compile(pair.second()).matcher(text);
            if (first.find() && second.find()) {
                findings.add(new Finding("CONFLICT", first.group() + " / " + second.group(), pair.severity(), true, pair.explanation()));
            }
        }
        addTerms(findings, "UNBOUNDED_SCOPE", lexicon.unboundedScope(), text);
        return findings;
    }

    public Optional<QuestionTemplate> question(String ambiguityType) {
        return Optional.ofNullable(lexicon.questions().get(ambiguityType));
    }

    private static void addTerms(List<Finding> findings, String type, List<Term> terms, String text) {
        for (Term term : terms) {
            Matcher matcher = compile(term.pattern()).matcher(text);
            if (matcher.find()) {
                findings.add(new Finding(type, matcher.group(), term.severity(), true, term.explanation()));
            }
        }
    }

    private static Pattern compile(String regex) {
        return Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
    }
}
