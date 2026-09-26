package com.agentic.urlshortener.orchestration.policy.rules;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.agentic.urlshortener.orchestration.policy.PolicyContext;
import com.agentic.urlshortener.orchestration.policy.PolicyRule;
import com.agentic.urlshortener.orchestration.policy.RuleOutcome;
import com.agentic.urlshortener.orchestration.policy.SbomReader;

/**
 * LIC-001: every SBOM component offers at least one license from the allow-list (Apache-2.0, MIT,
 * BSD-2/3-Clause, EPL-1.0/2.0, EDL-1.0, MPL-2.0, CDDL, public domain). A component that is only
 * available under LGPL or GPL-with-classpath-exception fails; dual-licensed components pass through
 * their allowed alternative. A missing SBOM fails.
 */
@Component
public class ApprovedLicensesRule implements PolicyRule {

    private static final Set<String> ALLOWED = Set.of("apache-2.0", "mit", "bsd-2-clause", "bsd-3-clause", "epl-1.0", "epl-2.0",
            "edl-1.0", "mpl-2.0", "cddl-1.0", "cddl-1.1", "cc0-1.0", "unlicense", "public domain");

    @Override
    public String policyId() {
        return "LIC-001";
    }

    @Override
    public RuleOutcome evaluate(PolicyContext context) {
        if (context.sbom() == null) {
            return RuleOutcome.fail("SBOM missing (META-INF/sbom/application.cdx.json); licenses cannot be verified");
        }
        List<String> rejected = new ArrayList<>();
        for (SbomReader.Component component : context.sbom()) {
            if (component.licenses().stream().noneMatch(ApprovedLicensesRule::allowed)) {
                rejected.add(component.name() + " " + component.licenses());
            }
        }
        return RuleOutcome.check(rejected.isEmpty(), rejected.isEmpty()
                ? context.sbom().size() + " SBOM components each offer an allowed license"
                : "components without an allowed license: " + rejected);
    }

    static boolean allowed(String license) {
        String normalized = license.toLowerCase(Locale.ROOT).strip();
        if (ALLOWED.contains(normalized)) {
            return true;
        }
        return switch (normalized) {
            case "epl 1.0", "eclipse public license 1.0", "eclipse public license - v 1.0" -> true;
            case "epl 2.0", "eclipse public license 2.0", "eclipse public license - v 2.0" -> true;
            case "apache license, version 2.0", "the apache software license, version 2.0", "apache 2.0" -> true;
            case "mit license", "the mit license" -> true;
            case "eclipse distribution license - v 1.0", "edl 1.0" -> true;
            default -> false;
        };
    }
}
