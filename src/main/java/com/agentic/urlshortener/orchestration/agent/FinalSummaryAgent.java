package com.agentic.urlshortener.orchestration.agent;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.agentic.urlshortener.orchestration.domain.StageType;
import com.agentic.urlshortener.orchestration.policy.PolicyContext;
import com.agentic.urlshortener.orchestration.policy.RunFacts;

import tools.jackson.databind.JsonNode;

/**
 * FINAL_SUMMARY (FR-ORC-17): the run's engineering summary, built only from recorded artifacts and
 * decisions: plan and rationale, artifacts with fingerprints, decisions and approvals, policy
 * outcomes, validation, risks, assumptions, limitations, metrics, and the outcome.
 */
@Component
public class FinalSummaryAgent implements StageAgent {

    private final RunFacts facts;

    public FinalSummaryAgent(RunFacts facts) {
        this.facts = facts;
    }

    @Override
    public StageType stageType() {
        return StageType.FINAL_SUMMARY;
    }

    @Override
    public String agentId() {
        return "summarizer@1.0";
    }

    @Override
    public Set<AgentPermission> permissions() {
        return Set.of();
    }

    @Override
    public StageResult execute(StageContext context) {
        StringBuilder md = new StringBuilder("# Final engineering summary\n\n");
        md.append("Run `").append(context.runId()).append("`, requirement version ").append(context.requirementVersion())
                .append(", policy set ").append(context.policySetVersion()).append(".\n\n");

        md.append("## Plan and rationale\n\n");
        json(context, "NORMALIZED_REQUIREMENT").ifPresent(n -> md.append("- Classification: ").append(n.path("classification").asString())
                .append(" (").append(n.path("classificationBasis").asString()).append(")\n- Clarification: ")
                .append(n.path("clarificationRationale").asString()).append('\n'));
        json(context, "TASK_GRAPH").ifPresent(t -> md.append("- Decomposition: ").append(t.path("tasks").size())
                .append(" tasks; critical path ").append(t.path("criticalPath")).append('\n'));
        json(context, "DESIGN").ifPresent(d -> md.append("- Design: capabilities ").append(d.path("capabilities"))
                .append("; material change ").append(d.path("materialChange").asBoolean()).append(" ").append(d.path("materialReasons"))
                .append("\n- Release plan: ").append(d.path("releasePlan").path("capability").asString())
                .append(" with parameters ").append(d.path("releasePlan").path("parameters")).append("\n- Rollback plan: ")
                .append(d.path("rollbackPlan").asString()).append('\n'));

        md.append("\n## Artifacts\n\n| Type | Version | Fingerprint |\n|---|---|---|\n");
        context.inputs().values().forEach(a -> md.append("| ").append(a.type()).append(" | ").append(a.version()).append(" | `")
                .append(a.fingerprint(), 0, 12).append("` |\n"));

        md.append("\n## Decisions and approvals\n\n");
        List<RunFacts.DecisionSummary> decisions = facts.decisions(context.runId());
        if (decisions.isEmpty()) {
            md.append("No human decisions were required.\n");
        }
        decisions.forEach(d -> md.append("- ").append(d.stageKey()).append(" ").append(d.type()).append(": **").append(d.outcome())
                .append("** by ").append(d.actorId()).append(" (").append(d.actorRole()).append(") at ").append(d.createdAt())
                .append(" — ").append(d.rationale()).append('\n'));

        md.append("\n## Policy outcomes\n\n");
        json(context, "COMPLIANCE_REPORT").ifPresentOrElse(c -> {
            md.append("Policy set ").append(c.path("policySetVersion").asString()).append("; blocked: ").append(c.path("blocked").asBoolean())
                    .append("\n\n| Policy | Severity | Outcome | Evidence |\n|---|---|---|---|\n");
            c.path("evaluations").forEach(e -> md.append("| ").append(e.path("policyId").asString()).append(" | ")
                    .append(e.path("severity").asString()).append(" | ").append(e.path("outcome").asString()).append(" | ")
                    .append(e.path("evidence").asString().replace("|", "/")).append(" |\n"));
        }, () -> md.append("No compliance report.\n"));

        md.append("\n## Validation\n\n");
        json(context, "VALIDATION_REPORT").ifPresent(v -> {
            md.append("Passed: ").append(v.path("passed").asBoolean()).append("; degraded stages: ").append(v.path("degradedStages")).append('\n');
            v.path("criteria").forEach(c -> md.append("- ").append(c.path("id").asString()).append(": ")
                    .append(c.path("passed").asBoolean() ? "verified by " : "NOT verified ").append(c.path("verifiedBy")).append('\n'));
        });
        json(context, "READINESS_REPORT").ifPresent(r -> md.append("\nReadiness: **").append(r.path("outcome").asString())
                .append("** — ").append(r.path("reasons")).append('\n'));

        md.append("\n## Risks\n\n");
        json(context, "DESIGN").ifPresent(d -> d.path("risks").forEach(r -> md.append("- ").append(r.asString()).append('\n')));

        md.append("\n## Assumptions\n\n- Agents are deterministic and knowledge-driven (catalog, lexicon); no external AI service is called.\n")
                .append("- Approvals in automated runs are simulated human input and are labeled as such in their rationale.\n");

        md.append("\n## Limitations\n\n");
        json(context, "READINESS_REPORT").ifPresent(r -> {
            if (r.path("limitations").isEmpty()) {
                md.append("- None recorded for this run.\n");
            }
            r.path("limitations").forEach(l -> md.append("- ").append(l.asString()).append('\n'));
        });

        PolicyContext counts = facts.forRun(context.runId()).build();
        md.append("\n## Metrics\n\n- Stage attempts: ").append(counts.attemptsUsed()).append(" of ").append(counts.maxAttempts())
                .append(" allowed by the autonomy budget\n");

        md.append("\n## Outcome\n\n");
        json(context, "RELEASE_RECORD").ifPresentOrElse(r -> md.append("Capability `").append(r.path("capability").asString())
                        .append("` released and verified; the run completes when this summary is recorded.\n"),
                () -> md.append("No release was recorded.\n"));

        return new StageResult.Succeeded(List.of(ArtifactDraft.markdown("FINAL_SUMMARY", md.toString())), "final summary recorded");
    }

    private static Optional<JsonNode> json(StageContext context, String type) {
        return context.input(type).map(StageContext.ArtifactInput::json);
    }
}
