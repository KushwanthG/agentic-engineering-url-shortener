package com.agentic.urlshortener.orchestration.agent.probes;

import java.util.ArrayList;
import java.util.List;

import com.agentic.urlshortener.orchestration.port.ProbeResponse;

/** Security probes referenced from threat models: the malicious-URL catalog and alias route-shadowing checks. */
final class SecurityProbes {

    /** Representative subset of the URL security catalog (the full catalog runs in the build, UrlSecurityCatalogIT). */
    private static final List<String> MALICIOUS_URLS = List.of(
            "javascript:alert(1)", "file:///etc/passwd", "https://user:secret@example.com/", "http://127.0.0.1/",
            "http://2130706433/", "http://169.254.169.254/latest/meta-data/", "http://10.0.0.1/", "http://[::1]/",
            "http://localhost:8080/admin");

    private SecurityProbes() {
    }

    static List<AcceptanceProbe> all() {
        return List.of(
                new AcceptanceProbe("SEC-URL-CATALOG", null, "malicious and internal target URLs are rejected", null,
                        SecurityProbes::maliciousUrlsRejected),
                new AcceptanceProbe("CA-SEC-RESERVED", "custom-alias", "reserved words cannot be used as aliases", null,
                        c -> allRejected(c, List.of("api", "actuator", "admin", "health"), "RESERVED_ALIAS")),
                new AcceptanceProbe("CA-SEC-ROUTE", "custom-alias", "aliases cannot shadow system routes in any letter case", null,
                        SecurityProbes::routesCannotBeShadowed));
    }

    private static ProbeOutcome maliciousUrlsRejected(ProbeContext context) {
        List<String> accepted = new ArrayList<>();
        for (String url : MALICIOUS_URLS) {
            ProbeResponse response = context.create(url, null);
            if (response.outcome() != ProbeResponse.Outcome.REJECTED || response.errorCode() == null
                    || !response.errorCode().startsWith("URL_")) {
                accepted.add(url + " -> " + response.outcome());
            }
        }
        return ProbeOutcome.check(accepted.isEmpty(), accepted.isEmpty()
                ? MALICIOUS_URLS.size() + " malicious or internal URLs rejected with URL_* codes" : "not rejected: " + accepted);
    }

    private static ProbeOutcome routesCannotBeShadowed(ProbeContext context) {
        ProbeOutcome caseVariants = allRejected(context, List.of("API", "Actuator", "error"), "RESERVED_ALIAS");
        ProbeOutcome pathLike = CustomAliasProbes.rejected(context, "api/links", "INVALID_ALIAS");
        return ProbeOutcome.check(caseVariants.passed() && pathLike.passed(), caseVariants.evidence() + "; " + pathLike.evidence());
    }

    private static ProbeOutcome allRejected(ProbeContext context, List<String> aliases, String expectedCode) {
        List<String> wrong = new ArrayList<>();
        for (String alias : aliases) {
            ProbeOutcome outcome = CustomAliasProbes.rejected(context, alias, expectedCode);
            if (!outcome.passed()) {
                wrong.add(outcome.evidence());
            }
        }
        return ProbeOutcome.check(wrong.isEmpty(), wrong.isEmpty() ? aliases + " rejected with " + expectedCode : String.join("; ", wrong));
    }
}
