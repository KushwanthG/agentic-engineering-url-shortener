package com.agentic.urlshortener.orchestration.policy;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.agentic.urlshortener.common.util.CanonicalJson;
import com.agentic.urlshortener.orchestration.audit.AuditService;
import com.agentic.urlshortener.orchestration.audit.AuditVerification;
import com.agentic.urlshortener.orchestration.config.OrchestrationProperties;
import com.agentic.urlshortener.orchestration.domain.Decision;
import com.agentic.urlshortener.orchestration.domain.FaultPlan;
import com.agentic.urlshortener.orchestration.domain.PolicyExceptionRecord;
import com.agentic.urlshortener.orchestration.domain.WorkflowRun;
import com.agentic.urlshortener.orchestration.repository.DecisionRepository;
import com.agentic.urlshortener.orchestration.repository.PolicyExceptionRepository;
import com.agentic.urlshortener.orchestration.repository.WorkflowRunRepository;

/** {@link RunFacts} read from the database, the audit chain, the configuration, and the build's SBOM. */
@Component
public class RepositoryRunFacts implements RunFacts {

    private final WorkflowRunRepository runs;
    private final DecisionRepository decisions;
    private final PolicyExceptionRepository exceptions;
    private final AuditService audit;
    private final OrchestrationProperties properties;

    public RepositoryRunFacts(WorkflowRunRepository runs, DecisionRepository decisions, PolicyExceptionRepository exceptions,
            AuditService audit, OrchestrationProperties properties) {
        this.runs = runs;
        this.decisions = decisions;
        this.exceptions = exceptions;
        this.audit = audit;
        this.properties = properties;
    }

    @Override
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public PolicyContext.Builder forRun(UUID runId) {
        WorkflowRun run = runs.findById(runId).orElseThrow();
        AuditVerification verification = audit.verify(runId.toString());
        PolicyContext.Builder builder = PolicyContext.builder(runId)
                .classification(run.getClassification().name())
                .audit(verification.valid(), verification.message())
                .auditRetentionDays(properties.auditRetentionDays())
                .attempts(run.getAttemptsUsed(), properties.autonomy().maxAttempts())
                .sbom(SbomReader.read().orElse(null));
        FaultPlan.parse(run.getFaultPlan()).simulatedPolicyFailures().forEach(builder::simulatedPolicyFailure);
        for (Decision decision : decisions.findByRunIdOrderByCreatedAtAsc(runId)) {
            if (decision.getStageKey() != null) {
                Map<String, String> bound = decision.getBoundFingerprints() == null ? Map.of()
                        : CanonicalJson.read(decision.getBoundFingerprints(), Map.class);
                builder.decision(decision.getStageKey().name(), decision.getOutcome(), bound, decision.isValid());
            }
        }
        return builder;
    }

    @Override
    @Transactional(readOnly = true)
    public List<DecisionSummary> decisions(UUID runId) {
        return decisions.findByRunIdOrderByCreatedAtAsc(runId).stream().filter(Decision::isValid)
                .map(d -> new DecisionSummary(d.getStageKey() == null ? null : d.getStageKey().name(), d.getDecisionType().name(),
                        d.getOutcome(), d.getActorId(), d.getActorRole(), d.getRationale(), d.getCreatedAt()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PolicyExceptionRecord> exceptions(UUID runId) {
        return exceptions.findByRunIdOrderByRequestedAtAsc(runId);
    }
}
