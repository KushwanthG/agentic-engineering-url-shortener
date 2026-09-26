package com.agentic.urlshortener.orchestration.policy.rules;

import org.springframework.stereotype.Component;

import com.agentic.urlshortener.orchestration.agent.DocumentationAgent;
import com.agentic.urlshortener.orchestration.policy.PolicyContext;
import com.agentic.urlshortener.orchestration.policy.PolicyRule;
import com.agentic.urlshortener.orchestration.policy.RuleOutcome;

/** DOC-001: for an API or behavior change, documentation is generated and the repository docs mention the capability. */
@Component
public class DocumentationUpdatedRule implements PolicyRule {

    @Override
    public String policyId() {
        return "DOC-001";
    }

    @Override
    public RuleOutcome evaluate(PolicyContext context) {
        boolean apiChange = context.json("DESIGN").map(d -> !d.path("apiChanges").isEmpty()).orElse(false);
        if (!apiChange) {
            return RuleOutcome.notApplicable("the design declares no API or behavior change");
        }
        String documentation = context.artifact("DOCUMENTATION").orElse("");
        if (documentation.isBlank()) {
            return RuleOutcome.fail("no DOCUMENTATION artifact");
        }
        boolean repository = DocumentationAgent.repositoryDocsUpdated(documentation);
        return RuleOutcome.check(repository, repository ? "generated documentation present; repository docs mention the capability"
                : "generated documentation present, but the repository docs do not mention the capability");
    }
}
