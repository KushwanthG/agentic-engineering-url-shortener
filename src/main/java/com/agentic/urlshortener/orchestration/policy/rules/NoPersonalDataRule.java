package com.agentic.urlshortener.orchestration.policy.rules;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.agentic.urlshortener.orchestration.policy.PolicyContext;
import com.agentic.urlshortener.orchestration.policy.PolicyRule;
import com.agentic.urlshortener.orchestration.policy.RuleOutcome;

import tools.jackson.databind.JsonNode;

/** PRV-001: no schema change adds a personal-data column (IP address, e-mail, user agent, name). */
@Component
public class NoPersonalDataRule implements PolicyRule {

    private static final Pattern PERSONAL = Pattern.compile(
            "(?i)\\b\\w*(_ip|ip_address|client_ip|remote_addr|e_?mail|user_agent|full_name|first_name|last_name|phone)\\w*\\b");

    @Override
    public String policyId() {
        return "PRV-001";
    }

    @Override
    public RuleOutcome evaluate(PolicyContext context) {
        JsonNode changes = context.json("DESIGN").map(d -> d.path("schemaChanges")).orElse(null);
        if (changes == null || changes.isEmpty()) {
            return RuleOutcome.notApplicable("the design declares no schema change");
        }
        List<String> personal = new ArrayList<>();
        for (JsonNode change : changes) {
            var matcher = PERSONAL.matcher(change.path("change").asString());
            if (matcher.find()) {
                personal.add(change.path("migration").asString() + ": " + matcher.group());
            }
        }
        return RuleOutcome.check(personal.isEmpty(), personal.isEmpty()
                ? changes.size() + " schema changes add no personal-data column" : "personal-data columns: " + personal);
    }
}
