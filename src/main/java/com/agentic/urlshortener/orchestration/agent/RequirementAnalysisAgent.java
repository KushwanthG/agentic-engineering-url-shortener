package com.agentic.urlshortener.orchestration.agent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.agentic.urlshortener.common.util.CanonicalJson;
import com.agentic.urlshortener.orchestration.domain.StageType;
import com.agentic.urlshortener.orchestration.knowledge.AmbiguityLexicon;
import com.agentic.urlshortener.orchestration.knowledge.CapabilityCatalog;
import com.agentic.urlshortener.orchestration.knowledge.CapabilityEntry;

import tools.jackson.databind.JsonNode;

/**
 * REQUIREMENT_ANALYSIS (FR-ORC-13): normalizes the requirement, matches catalog capabilities,
 * classifies it, runs the five quality checks, and detects ambiguity. Each check records its evidence;
 * when nothing blocks, the rationale for skipping clarification is recorded. When something blocks,
 * a {@code CLARIFICATION_REQUEST} with options and impacts is produced as well.
 */
@Component
public class RequirementAnalysisAgent implements StageAgent {

    private static final Pattern PERSONAL_DATA = Pattern.compile(
            "\\b(ip address(es)?|e-?mail address(es)?|user agents?|full names?|phone numbers?)\\b", Pattern.CASE_INSENSITIVE);

    private final CapabilityCatalog catalog;
    private final AmbiguityLexicon lexicon;

    public RequirementAnalysisAgent(CapabilityCatalog catalog, AmbiguityLexicon lexicon) {
        this.catalog = catalog;
        this.lexicon = lexicon;
    }

    @Override
    public StageType stageType() {
        return StageType.REQUIREMENT_ANALYSIS;
    }

    @Override
    public String agentId() {
        return "requirement-analyst@1.0";
    }

    @Override
    public Set<AgentPermission> permissions() {
        return Set.of();
    }

    @Override
    public StageResult execute(StageContext context) {
        JsonNode requirement = context.inputJson("REQUIREMENT");
        String title = requirement.path("title").asString("");
        String narrative = requirement.path("narrative").asString("");
        String declaredType = requirement.path("type").asString("UNSPECIFIED");

        List<AcceptanceCriterionParser.Parsed> criteria = new ArrayList<>();
        StringBuilder text = new StringBuilder(title).append('\n').append(narrative).append('\n');
        for (JsonNode ac : requirement.path("acceptanceCriteria")) {
            criteria.add(AcceptanceCriterionParser.parse(ac.path("id").asString(), ac.path("text").asString()));
            text.append(ac.path("text").asString()).append('\n');
        }
        List<String> constraints = new ArrayList<>();
        requirement.path("constraints").forEach(c -> constraints.add(c.asString()));
        constraints.forEach(c -> text.append(c).append('\n'));
        String fullText = text.toString();

        List<CapabilityCatalog.Match> matches = catalog.match(fullText);
        List<CapabilityEntry> scenario = matches.stream().map(CapabilityCatalog.Match::capability).filter(c -> !c.baseline()).toList();
        List<String> capabilityIds = matches.stream().map(m -> m.capability().id()).toList();

        List<Map<String, Object>> ambiguities = new ArrayList<>();
        for (AmbiguityLexicon.Finding finding : lexicon.detect(fullText)) {
            ambiguities.add(ambiguity(ambiguities.size() + 1, finding.type(), finding.severity(), finding.blocking(), finding.text(),
                    affects(capabilityIds), finding.explanation()));
        }
        if (criteria.isEmpty()) {
            ambiguities.add(ambiguity(ambiguities.size() + 1, "MISSING_ACCEPTANCE_CRITERIA", "HIGH", true, "(no acceptance criteria)",
                    List.of("verification"), "without acceptance criteria no outcome can be verified"));
        }
        for (AcceptanceCriterionParser.Parsed criterion : criteria) {
            if (!criterion.testable()) {
                ambiguities.add(ambiguity(ambiguities.size() + 1, "UNTESTABLE_CRITERION", "MEDIUM", true, criterion.text(),
                        List.of(criterion.id()), "the criterion does not state a precondition and an observable outcome"));
            }
        }
        if ("UNSPECIFIED".equals(declaredType)) {
            ambiguities.add(ambiguity(ambiguities.size() + 1, "UNSPECIFIED_TYPE", "LOW", false, "(type not specified)",
                    List.of("plan"), "whether this is a new capability or a change to existing behavior decides the plan"));
        }
        if (matches.isEmpty()) {
            ambiguities.add(ambiguity(ambiguities.size() + 1, "UNKNOWN_CAPABILITY", "HIGH", true, title,
                    List.of("scope"), "no capability of the catalog matches the requirement"));
        }

        String classification = "UNSPECIFIED".equals(declaredType) ? "UNDETERMINED" : declaredType;
        List<Map<String, Object>> checks = List.of(
                completeness(title, narrative, criteria),
                consistency(declaredType, scenario, ambiguities),
                testability(criteria),
                policy(fullText),
                boundary(matches));
        boolean clarificationRequired = ambiguities.stream().anyMatch(a -> Boolean.TRUE.equals(a.get("blocking")));
        String rationale = clarificationRequired
                ? blockingSummary(ambiguities)
                : "All five quality checks passed and no blocking ambiguity was detected: " + criteria.size()
                        + " acceptance criteria are testable (Given/When/Then), the declared type " + declaredType
                        + " is consistent with the catalog capabilities " + capabilityIds
                        + ", and the lexicon found no vague terms, undefined concepts, conflicts, or unbounded scope.";

        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("requirementRef", requirement.path("ref").isNull() ? null : requirement.path("ref").asString(null));
        normalized.put("requirementVersion", context.requirementVersion());
        normalized.put("classification", classification);
        normalized.put("classificationBasis", classificationBasis(declaredType, scenario));
        normalized.put("actors", List.of("API consumer"));
        normalized.put("capabilities", matches.stream().map(m -> Map.of("id", m.capability().id(), "matchedTerms", m.matchedTerms(),
                "baseline", m.capability().baseline())).toList());
        normalized.put("acceptanceCriteria", criteria.stream().map(RequirementAnalysisAgent::criterion).toList());
        normalized.put("constraints", constraints);
        normalized.put("parameters", requirement.path("parameters").isObject()
                ? CanonicalJson.read(CanonicalJson.write(requirement.path("parameters")), Map.class) : Map.of());
        normalized.put("qualityChecks", checks);
        normalized.put("ambiguities", ambiguities);
        normalized.put("clarificationRequired", clarificationRequired);
        normalized.put("clarificationRationale", rationale);

        List<ArtifactDraft> drafts = new ArrayList<>();
        drafts.add(ArtifactDraft.json("NORMALIZED_REQUIREMENT", CanonicalJson.write(normalized)));
        if (clarificationRequired) {
            drafts.add(ArtifactDraft.json("CLARIFICATION_REQUEST", CanonicalJson.write(clarificationRequest(context, ambiguities, scenario))));
        }
        return new StageResult.Succeeded(drafts, clarificationRequired ? rationale : "no clarification required");
    }

    private Map<String, Object> clarificationRequest(StageContext context, List<Map<String, Object>> ambiguities,
            List<CapabilityEntry> scenario) {
        List<Map<String, Object>> questions = new ArrayList<>();
        for (Map<String, Object> ambiguity : ambiguities) {
            lexicon.question((String) ambiguity.get("type")).ifPresent(template -> questions.add(question(questions.size() + 1,
                    (String) ambiguity.get("id"), template.render((String) ambiguity.get("text")), (String) ambiguity.get("severity"),
                    ambiguity.get("affects"), template)));
        }
        boolean changesExisting = scenario.stream().anyMatch(c -> "CHANGE_TO_EXISTING".equals(c.changeType()));
        if (changesExisting) {
            lexicon.question("EXISTING_DATA").ifPresent(template -> questions.add(question(questions.size() + 1, "EXISTING_DATA",
                    template.question(), "MEDIUM", List.of("existing links"), template)));
        }
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("round", context.requirementVersion());
        request.put("raisedBy", agentId());
        request.put("reason", blockingSummary(ambiguities));
        request.put("questions", questions);
        return request;
    }

    private static Map<String, Object> question(int index, String ambiguityId, String text, String severity, Object affects,
            AmbiguityLexicon.QuestionTemplate template) {
        Map<String, Object> question = new LinkedHashMap<>();
        question.put("questionId", "Q-" + index);
        question.put("ambiguityId", ambiguityId);
        question.put("question", text);
        question.put("severity", severity);
        question.put("affects", affects);
        question.put("options", template.options().stream()
                .map(o -> Map.of("optionId", o.optionId(), "label", o.label(), "impact", o.impact())).toList());
        question.put("allowFreeText", template.allowFreeText());
        return question;
    }

    private static Map<String, Object> ambiguity(int index, String type, String severity, boolean blocking, String text,
            List<String> affects, String explanation) {
        Map<String, Object> ambiguity = new LinkedHashMap<>();
        ambiguity.put("id", "AMB-" + index);
        ambiguity.put("type", type);
        ambiguity.put("severity", severity);
        ambiguity.put("blocking", blocking);
        ambiguity.put("text", text);
        ambiguity.put("affects", affects);
        ambiguity.put("explanation", explanation);
        return ambiguity;
    }

    private static List<String> affects(List<String> capabilityIds) {
        return capabilityIds.isEmpty() ? List.of("scope") : capabilityIds;
    }

    private static Map<String, Object> criterion(AcceptanceCriterionParser.Parsed parsed) {
        Map<String, Object> criterion = new LinkedHashMap<>();
        criterion.put("id", parsed.id());
        criterion.put("text", parsed.text());
        criterion.put("given", parsed.given());
        criterion.put("when", parsed.when());
        criterion.put("then", parsed.then());
        criterion.put("testable", parsed.testable());
        return criterion;
    }

    private static Map<String, Object> result(String check, boolean pass, String evidence) {
        return Map.of("check", check, "result", pass ? "PASS" : "FAIL", "evidence", evidence);
    }

    private static Map<String, Object> completeness(String title, String narrative, List<AcceptanceCriterionParser.Parsed> criteria) {
        boolean pass = !title.isBlank() && !narrative.isBlank() && !criteria.isEmpty();
        return result("COMPLETENESS", pass, pass ? "title, narrative, and " + criteria.size() + " acceptance criteria are present"
                : "missing: " + (criteria.isEmpty() ? "acceptance criteria" : "title or narrative"));
    }

    private static Map<String, Object> consistency(String declaredType, List<CapabilityEntry> scenario, List<Map<String, Object>> ambiguities) {
        boolean conflict = ambiguities.stream().anyMatch(a -> "CONFLICT".equals(a.get("type")));
        List<String> mismatched = scenario.stream()
                .filter(c -> !"UNSPECIFIED".equals(declaredType) && c.changeType() != null && !c.changeType().equals(declaredType))
                .map(CapabilityEntry::id).toList();
        boolean pass = !conflict && mismatched.isEmpty();
        String evidence = conflict ? "the requirement contains contradicting rules"
                : !mismatched.isEmpty() ? "declared type " + declaredType + " does not match the catalog change type of " + mismatched
                : "no contradicting rules; declared type " + declaredType + " is consistent with the catalog";
        return result("CONSISTENCY", pass, evidence);
    }

    private static Map<String, Object> testability(List<AcceptanceCriterionParser.Parsed> criteria) {
        List<String> untestable = criteria.stream().filter(c -> !c.testable()).map(AcceptanceCriterionParser.Parsed::id).toList();
        boolean pass = !criteria.isEmpty() && untestable.isEmpty();
        return result("TESTABILITY", pass, criteria.isEmpty() ? "no acceptance criteria to test"
                : untestable.isEmpty() ? "every criterion states a precondition and an observable outcome"
                : "untestable criteria: " + untestable);
    }

    private static Map<String, Object> policy(String text) {
        var matcher = PERSONAL_DATA.matcher(text);
        boolean personalData = matcher.find();
        return result("POLICY", !personalData, personalData
                ? "the requirement would process personal data (\"" + matcher.group() + "\"), which PRV-001 forbids"
                : "no personal data, secrets, or policy-restricted processing requested");
    }

    private static Map<String, Object> boundary(List<CapabilityCatalog.Match> matches) {
        boolean pass = !matches.isEmpty();
        return result("ARCHITECTURE_BOUNDARY", pass, pass
                ? "within the URL shortener: matches " + matches.stream().map(m -> m.capability().id()).toList()
                : "no catalog capability matches; the request is outside the URL shortener's boundary");
    }

    private static String classificationBasis(String declaredType, List<CapabilityEntry> scenario) {
        if ("UNSPECIFIED".equals(declaredType)) {
            return "type not specified; to be decided by clarification (catalog candidates: "
                    + scenario.stream().map(CapabilityEntry::id).toList() + ")";
        }
        return "declared type " + declaredType + "; catalog capabilities " + scenario.stream()
                .map(c -> c.id() + " (" + c.changeType() + ")").toList();
    }

    private static String blockingSummary(List<Map<String, Object>> ambiguities) {
        List<String> blocking = ambiguities.stream().filter(a -> Boolean.TRUE.equals(a.get("blocking")))
                .map(a -> a.get("type") + " \"" + a.get("text") + "\"").toList();
        return blocking.size() + " blocking ambiguities must be resolved by an APPROVER before decomposition: " + blocking;
    }
}
