package com.agentic.urlshortener.support;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import com.agentic.urlshortener.common.util.CanonicalJson;
import com.agentic.urlshortener.common.util.Fingerprints;
import com.agentic.urlshortener.orchestration.agent.StageContext;
import com.agentic.urlshortener.orchestration.agent.StageContext.ArtifactInput;
import com.agentic.urlshortener.orchestration.domain.StageType;
import com.agentic.urlshortener.orchestration.port.ApplicationPlanePort;

/** The fixed scenario inputs of spec.md (GF-001, BF-001, AMB-001) as requirement documents, and context builders. */
public final class RequirementFixtures {

    public static final List<String> GF_001_CRITERIA = List.of(
            "Given the capability is released, when a consumer creates a link for a valid URL with the alias `spring-sale`, "
                    + "then the link is created and its short code is `spring-sale`.",
            "Given the alias `spring-sale` is in use, when another link is created with the alias `spring-sale`, "
                    + "then creation is rejected with error code `ALIAS_CONFLICT`.",
            "Given an alias containing characters other than letters, digits, hyphen, or underscore, when a link is created, "
                    + "then creation is rejected with error code `INVALID_ALIAS`.",
            "Given an alias shorter than 3 or longer than 32 characters, when a link is created, "
                    + "then creation is rejected with error code `INVALID_ALIAS`.",
            "Given a reserved alias such as `api`, when a link is created, then creation is rejected with error code `RESERVED_ALIAS`.",
            "Given a link created with an alias, when the alias is resolved, then the client is redirected to the target URL.");

    public static final List<String> BF_001_CRITERIA = List.of(
            "Given the capability is released, when a link is created with a maximum of 2 clicks, then the link is created and "
                    + "reports its click limit.",
            "Given a link with a maximum of 2 clicks, when it is resolved 3 times, then the first 2 resolutions redirect and the "
                    + "third returns the expired outcome.",
            "Given a link with a maximum of N clicks resolved concurrently by more than N clients, then exactly N resolutions redirect.",
            "Given a link created before the capability was released, when it is resolved, then it continues to redirect without a limit.",
            "Given a click limit outside 1 to 1,000,000, when a link is created, then creation is rejected with error code "
                    + "`INVALID_CLICK_LIMIT`.",
            "Given a click-limited link whose click cannot be recorded, when it is resolved, then the redirect is refused with a "
                    + "temporarily-unavailable outcome.");

    /** A small valid submission for API tests. */
    public static final String API_SUBMISSION = """
            {"ref":"API-1","title":"Workflow API test","narrative":"Scripted run for the API","type":"NEW_CAPABILITY",
             "acceptanceCriteria":["Given x, when y, then z"],"constraints":[]}""";

    private RequirementFixtures() {
    }

    public static String gf001() {
        return document("GF-001", "Custom aliases for short links.", "NEW_CAPABILITY",
                "As an API consumer, I want to optionally choose a custom alias when I create a short link so that I can share "
                        + "memorable links.",
                GF_001_CRITERIA, List.of("Aliases are case-sensitive, like generated codes, and share the code namespace."));
    }

    public static String bf001() {
        return document("BF-001", "Click-limited short links.", "CHANGE_TO_EXISTING",
                "As an API consumer, I want to limit how many times a short link can be used so that I can share one-time or "
                        + "limited-use links.",
                BF_001_CRITERIA, List.of("Existing links and consumers that do not use the limit must be unaffected; the change "
                        + "must be reversible without data loss."));
    }

    public static String amb001() {
        return document("AMB-001", "Better link expiry.", "UNSPECIFIED",
                "Links should expire after a while so old links don't pile up, but premium users' links should never expire. "
                        + "Also make the analytics better.",
                List.of(), List.of());
    }

    public static String document(String ref, String title, String type, String narrative, List<String> criteria,
            List<String> constraints) {
        List<Map<String, Object>> acs = new ArrayList<>();
        for (int i = 0; i < criteria.size(); i++) {
            acs.add(Map.of("id", "AC-" + (i + 1), "text", criteria.get(i), "origin", "SUBMITTED"));
        }
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("ref", ref);
        document.put("title", title);
        document.put("type", type);
        document.put("narrative", narrative);
        document.put("acceptanceCriteria", acs);
        document.put("constraints", constraints);
        document.put("clarifications", List.of());
        document.put("scopeExclusions", List.of());
        document.put("parameters", Map.of());
        return CanonicalJson.write(document);
    }

    /** A context for {@code stage} with the given current artifacts (type to JSON or Markdown content). */
    public static StageContext context(StageType stage, String requirement, Map<String, String> artifacts, ApplicationPlanePort port) {
        return context(UUID.randomUUID(), stage, requirement, artifacts, port, () -> false);
    }

    public static StageContext context(UUID runId, StageType stage, String requirement, Map<String, String> artifacts,
            ApplicationPlanePort port, BooleanSupplier cancellation) {
        Map<String, ArtifactInput> inputs = new LinkedHashMap<>();
        artifacts.forEach((type, content) -> inputs.put(type, new ArtifactInput(UUID.randomUUID(), type, 1,
                Fingerprints.sha256(content), content.startsWith("{") ? "application/json" : "text/markdown", content)));
        return new StageContext(runId, stage, 1, 1, 1, requirement, inputs, "1.0.0", port, cancellation);
    }
}
