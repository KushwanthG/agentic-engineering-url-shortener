package com.agentic.urlshortener.orchestration.service;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.agentic.urlshortener.common.exception.ApiException;
import com.agentic.urlshortener.common.exception.ErrorCode;
import com.agentic.urlshortener.orchestration.audit.AuditService;
import com.agentic.urlshortener.orchestration.audit.AuditVerification;
import com.agentic.urlshortener.orchestration.domain.Artifact;
import com.agentic.urlshortener.orchestration.dto.EvidenceViews;
import com.agentic.urlshortener.orchestration.policy.PolicySetLoader;
import com.agentic.urlshortener.orchestration.port.CapabilityReleaseReader;
import com.agentic.urlshortener.orchestration.repository.ArtifactRepository;
import com.agentic.urlshortener.orchestration.repository.WorkflowRunRepository;

/**
 * Read side of the evidence API (FR-AUD-02, FR-AUD-03, SC-006): a run's audit trail and its
 * recomputed verification, the final summary, the active policy set, and capability release states.
 * Everything is read from persisted records, so a reviewer can re-check it independently.
 */
@Service
@Transactional(readOnly = true)
public class EvidenceQueryService {

    private static final String FINAL_SUMMARY = "FINAL_SUMMARY";

    private final WorkflowRunRepository runs;
    private final ArtifactRepository artifacts;
    private final AuditService audit;
    private final PolicySetLoader policySets;
    private final CapabilityReleaseReader capabilities;

    public EvidenceQueryService(WorkflowRunRepository runs, ArtifactRepository artifacts, AuditService audit,
            PolicySetLoader policySets, CapabilityReleaseReader capabilities) {
        this.runs = runs;
        this.artifacts = artifacts;
        this.audit = audit;
        this.policySets = policySets;
        this.capabilities = capabilities;
    }

    public List<EvidenceViews.AuditEventView> auditTrail(UUID runId) {
        requireRun(runId);
        return audit.chain(runId.toString()).stream().map(EvidenceViews.AuditEventView::of).toList();
    }

    public AuditVerification verifyAuditTrail(UUID runId) {
        requireRun(runId);
        return audit.verify(runId.toString());
    }

    /** The current final summary (Markdown); a run that has not ended has none. */
    public String finalSummary(UUID runId) {
        requireRun(runId);
        return artifacts.findByRunIdAndSupersededFalseOrderByCreatedAtAsc(runId).stream()
                .filter(a -> FINAL_SUMMARY.equals(a.getArtifactType()))
                .max(Comparator.comparingInt(Artifact::getVersion))
                .map(Artifact::getContent)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND,
                        "Run " + runId + " has no final summary yet; it is produced when the run ends."));
    }

    public EvidenceViews.PolicySetView policySet() {
        return EvidenceViews.PolicySetView.of(policySets.current());
    }

    public List<EvidenceViews.CapabilityView> capabilities() {
        return capabilities.capabilities().stream().map(EvidenceViews.CapabilityView::of).toList();
    }

    private void requireRun(UUID runId) {
        if (!runs.existsById(runId)) {
            throw new ApiException(ErrorCode.RUN_NOT_FOUND, "No workflow run " + runId + ".");
        }
    }
}
