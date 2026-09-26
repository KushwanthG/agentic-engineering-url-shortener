package com.agentic.urlshortener.orchestration.agent;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.agentic.urlshortener.orchestration.domain.StageType;

/**
 * Fallback of FINAL_SUMMARY (FR-REL-03, FR-ORC-17): lists the run's artifacts with their fingerprints,
 * so every run still ends with a summary, and states that it is degraded.
 */
@Component
public class MinimalSummaryAgent implements StageAgent {

    @Override
    public StageType stageType() {
        return StageType.FINAL_SUMMARY;
    }

    @Override
    public String agentId() {
        return "minimal-summarizer@1.0";
    }

    @Override
    public boolean fallback() {
        return true;
    }

    @Override
    public Set<AgentPermission> permissions() {
        return Set.of();
    }

    @Override
    public StageResult execute(StageContext context) {
        StringBuilder md = new StringBuilder("# Final engineering summary (minimal, degraded)\n\n");
        md.append("Run `").append(context.runId()).append("`, requirement version ").append(context.requirementVersion())
                .append(", policy set ").append(context.policySetVersion()).append(".\n\n");
        md.append("The primary summary agent failed; this degraded summary lists the artifacts only. Decisions, risks, and ")
                .append("limitations are available from the run's decisions, policy evaluations, and audit trail.\n\n");
        md.append("| Type | Version | Fingerprint |\n|---|---|---|\n");
        context.inputs().values().forEach(a -> md.append("| ").append(a.type()).append(" | ").append(a.version()).append(" | `")
                .append(a.fingerprint(), 0, Math.min(12, a.fingerprint().length())).append("` |\n"));
        return new StageResult.Succeeded(List.of(ArtifactDraft.markdown("FINAL_SUMMARY", md.toString())), "minimal summary (degraded)");
    }
}
