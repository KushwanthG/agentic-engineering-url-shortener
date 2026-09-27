package com.agentic.urlshortener.orchestration.governance;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.agentic.urlshortener.common.util.CanonicalJson;
import com.agentic.urlshortener.orchestration.knowledge.CapabilityEntry;

import tools.jackson.databind.JsonNode;

/**
 * Turns clarification answers into the next requirement version (FR-GOV-08, ADR-011),
 * deterministically. Each answer is interpreted by the type of ambiguity it resolves. A duration
 * becomes a release parameter; an undefined concept or an unbounded item becomes a scope exclusion;
 * a conflict or an existing-data rule becomes a constraint; the change type is set; and acceptance
 * criteria are derived from the capability's catalog templates. Only answers that resolve their
 * ambiguity are recorded as clarifications, so an unresolved one is asked again in the next round.
 */
public final class ClarificationDerivation {

    private static final Pattern DAYS = Pattern.compile("(\\d{1,5})\\s*days?", Pattern.CASE_INSENSITIVE);
    private static final Pattern QUOTED = Pattern.compile("\"([^\"]+)\"");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\w+)}");

    private ClarificationDerivation() {
    }

    /** One answered question: its type, text, chosen option (label) or free text, and the decision that records it. */
    public record Answer(String questionId, String ambiguityType, String question, String optionId, String optionLabel,
            String freeText, String decisionId) {

        String text() {
            return freeText != null ? freeText : optionId + ": " + optionLabel;
        }
    }

    /** The next requirement document and which answers resolved their ambiguity. */
    public record Result(String document, List<String> resolvedQuestionIds, String declaredType) {
    }

    public static Result derive(JsonNode current, int round, List<Answer> answers, List<CapabilityEntry> capabilities) {
        Map<String, Object> document = CanonicalJson.read(CanonicalJson.write(current), Map.class);
        String type = current.path("type").asString("UNSPECIFIED");
        Set<String> constraints = new LinkedHashSet<>(strings(current.path("constraints")));
        Set<String> exclusions = new LinkedHashSet<>(strings(current.path("scopeExclusions")));
        Map<String, Object> parameters = new LinkedHashMap<>(CanonicalJson.read(CanonicalJson.write(current.path("parameters").isObject()
                ? current.path("parameters") : CanonicalJson.parse("{}")), Map.class));
        List<Map<String, Object>> clarifications = new ArrayList<>();
        current.path("clarifications").forEach(c -> clarifications.add(CanonicalJson.read(CanonicalJson.write(c), Map.class)));
        List<String> freeCriteria = new ArrayList<>();
        boolean deriveCriteria = false;
        List<String> resolved = new ArrayList<>();

        for (Answer answer : answers) {
            String phrase = quoted(answer.question()).orElse(answer.question());
            boolean resolves = switch (answer.ambiguityType()) {
                case "VAGUE_TERM" -> {
                    if ("D".equals(answer.optionId())) {
                        yield true;
                    }
                    Matcher days = DAYS.matcher(answer.text());
                    if (days.find()) {
                        parameters.put("defaultExpiryDays", Integer.parseInt(days.group(1)));
                        yield true;
                    }
                    yield false;
                }
                case "UNDEFINED_CONCEPT" -> {
                    exclusions.add("A".equals(answer.optionId())
                            ? "\"" + phrase + "\": out of scope; no link is exempt"
                            : "\"" + phrase + "\": to be introduced as a separate capability");
                    yield true;
                }
                case "CONFLICT" -> {
                    if (!"A".equals(answer.optionId())) {
                        yield false;
                    }
                    constraints.add("The general rule applies without exceptions (\"" + phrase + "\").");
                    yield true;
                }
                case "UNBOUNDED_SCOPE" -> {
                    if ("A".equals(answer.optionId())) {
                        exclusions.add("\"" + phrase + "\": deferred to the backlog");
                        yield true;
                    }
                    if (answer.freeText() == null) {
                        yield false;
                    }
                    freeCriteria.add(answer.freeText());
                    yield true;
                }
                case "MISSING_ACCEPTANCE_CRITERIA" -> {
                    if ("A".equals(answer.optionId())) {
                        deriveCriteria = true;
                        yield true;
                    }
                    if (answer.freeText() == null) {
                        yield false;
                    }
                    freeCriteria.addAll(answer.freeText().lines().filter(l -> !l.isBlank()).toList());
                    yield true;
                }
                case "UNSPECIFIED_TYPE" -> {
                    type = "B".equals(answer.optionId()) ? "CHANGE_TO_EXISTING" : "NEW_CAPABILITY";
                    yield true;
                }
                case "EXISTING_DATA" -> {
                    constraints.add("A".equals(answer.optionId())
                            ? "Existing links are not changed; only links created after the release are affected."
                            : "Existing links are updated (requires a separate data-migration decision).");
                    yield true;
                }
                default -> answer.freeText() != null;
            };
            if (resolves) {
                resolved.add(answer.questionId());
                Map<String, Object> record = new LinkedHashMap<>();
                record.put("questionId", "R" + round + "-" + answer.questionId());
                record.put("question", answer.question());
                record.put("answer", answer.text());
                record.put("decisionId", answer.decisionId());
                clarifications.add(record);
            }
        }

        List<Map<String, Object>> criteria = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (JsonNode ac : current.path("acceptanceCriteria")) {
            if (seen.add(ac.path("text").asString())) {
                criteria.add(CanonicalJson.read(CanonicalJson.write(ac), Map.class));
            }
        }
        List<String> derived = new ArrayList<>(freeCriteria);
        if (deriveCriteria) {
            capabilities.forEach(c -> c.acceptanceTemplates().forEach(t -> fill(t, parameters).ifPresent(derived::add)));
        }
        for (String text : derived) {
            if (seen.add(text)) {
                Map<String, Object> ac = new LinkedHashMap<>();
                ac.put("id", "AC-" + (criteria.size() + 1));
                ac.put("text", text);
                ac.put("origin", "DERIVED_FROM_DECISION");
                criteria.add(ac);
            }
        }

        document.put("type", type);
        document.put("acceptanceCriteria", criteria);
        document.put("constraints", List.copyOf(constraints));
        document.put("scopeExclusions", List.copyOf(exclusions));
        document.put("parameters", parameters);
        document.put("clarifications", clarifications);
        return new Result(CanonicalJson.write(document), resolved, type);
    }

    private static Optional<String> fill(String template, Map<String, Object> parameters) {
        Matcher placeholder = PLACEHOLDER.matcher(template);
        StringBuilder filled = new StringBuilder();
        while (placeholder.find()) {
            Object value = parameters.get(placeholder.group(1));
            if (value == null) {
                return Optional.empty();
            }
            placeholder.appendReplacement(filled, Matcher.quoteReplacement(String.valueOf(value)));
        }
        placeholder.appendTail(filled);
        return Optional.of(filled.toString());
    }

    private static Optional<String> quoted(String question) {
        Matcher matcher = QUOTED.matcher(question);
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    private static List<String> strings(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(v -> values.add(v.asString()));
        return values;
    }
}
