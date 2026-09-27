package com.agentic.urlshortener.orchestration.knowledge;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * The capability catalog: baseline capabilities of the running shortener and the scenario
 * capabilities a run can deliver (ADR-017). Matching is by keyword prefix at word boundaries, so
 * "alias" also matches "aliases"; the knowledge is data, not code.
 */
@Component
public class CapabilityCatalog {

    private static final String LOCATION = "orchestration/capability-catalog.yaml";

    /** A capability found in a requirement text, with the keywords that matched. */
    public record Match(CapabilityEntry capability, List<String> matchedTerms) {
    }

    record CatalogFile(String version, List<CapabilityEntry> capabilities) {
    }

    private final String version;
    private final List<CapabilityEntry> entries;

    public CapabilityCatalog() {
        CatalogFile file = YamlResources.read(LOCATION, CatalogFile.class);
        this.version = file.version();
        this.entries = List.copyOf(file.capabilities());
    }

    public static CapabilityCatalog load() {
        return new CapabilityCatalog();
    }

    public String version() {
        return version;
    }

    public List<CapabilityEntry> all() {
        return entries;
    }

    public Optional<CapabilityEntry> get(String id) {
        return entries.stream().filter(e -> e.id().equals(id)).findFirst();
    }

    /** Capabilities whose keywords occur in {@code text}, in catalog order. */
    public List<Match> match(String text) {
        String haystack = text.toLowerCase(Locale.ROOT);
        List<Match> matches = new ArrayList<>();
        for (CapabilityEntry entry : entries) {
            List<String> terms = entry.keywords().stream()
                    .filter(k -> Pattern.compile("\\b" + Pattern.quote(k.toLowerCase(Locale.ROOT))).matcher(haystack).find())
                    .toList();
            if (!terms.isEmpty()) {
                matches.add(new Match(entry, terms));
            }
        }
        return matches;
    }
}
