package com.agentic.urlshortener.orchestration.policy.rules;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.agentic.urlshortener.orchestration.policy.PolicyContext;
import com.agentic.urlshortener.orchestration.policy.PolicyRule;
import com.agentic.urlshortener.orchestration.policy.RuleOutcome;

/** SEC-002: no run artifact contains a private key, cloud access key, bearer token, or password value. */
@Component
public class NoSecretsRule implements PolicyRule {

    private static final List<Pattern> SECRETS = List.of(
            Pattern.compile("-----BEGIN [A-Z ]*PRIVATE KEY-----"),
            Pattern.compile("\\bAKIA[0-9A-Z]{16}\\b"),
            Pattern.compile("\\bBearer\\s+[A-Za-z0-9._~+/-]{20,}"),
            Pattern.compile("(?i)\\bpassword\\s*[=:]\\s*[^\\s\"',}]+"));

    @Override
    public String policyId() {
        return "SEC-002";
    }

    @Override
    public RuleOutcome evaluate(PolicyContext context) {
        List<String> findings = new ArrayList<>();
        context.artifacts().forEach((type, content) -> {
            for (Pattern secret : SECRETS) {
                if (secret.matcher(content).find()) {
                    findings.add(type + " matches " + secret.pattern());
                }
            }
        });
        return RuleOutcome.check(findings.isEmpty(), findings.isEmpty()
                ? context.artifacts().size() + " artifacts scanned; no secret patterns found" : "secret patterns found: " + findings);
    }
}
