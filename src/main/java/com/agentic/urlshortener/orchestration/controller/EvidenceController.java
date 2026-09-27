package com.agentic.urlshortener.orchestration.controller;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.agentic.urlshortener.orchestration.audit.AuditVerification;
import com.agentic.urlshortener.orchestration.dto.EvidenceViews;
import com.agentic.urlshortener.orchestration.metrics.ReliabilityCalculator;
import com.agentic.urlshortener.orchestration.metrics.ReliabilityReportService;
import com.agentic.urlshortener.orchestration.service.EvidenceQueryService;

/**
 * Evidence API (FR-AUD-02, FR-AUD-03, SC-006; contract tag {@code evidence}). Read roles are enforced
 * by SecurityConfig. The summary sets its media type on the response rather than through
 * {@code produces}, so error responses keep the problem-details format.
 */
@RestController
@RequestMapping("/api/v1")
public class EvidenceController {

    private static final MediaType MARKDOWN = MediaType.parseMediaType("text/markdown;charset=UTF-8");

    private final EvidenceQueryService evidence;
    private final ReliabilityReportService reliability;

    public EvidenceController(EvidenceQueryService evidence, ReliabilityReportService reliability) {
        this.evidence = evidence;
        this.reliability = reliability;
    }

    @GetMapping(path = "/workflows/{runId}/audit", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<EvidenceViews.AuditEventView> auditTrail(@PathVariable String runId) {
        return evidence.auditTrail(PathIds.runId(runId));
    }

    @GetMapping(path = "/workflows/{runId}/audit/verification", produces = MediaType.APPLICATION_JSON_VALUE)
    public AuditVerification verifyAuditTrail(@PathVariable String runId) {
        return evidence.verifyAuditTrail(PathIds.runId(runId));
    }

    @GetMapping("/workflows/{runId}/summary")
    public ResponseEntity<String> finalSummary(@PathVariable String runId) {
        return ResponseEntity.ok().contentType(MARKDOWN).body(evidence.finalSummary(PathIds.runId(runId)));
    }

    @GetMapping(path = "/policies", produces = MediaType.APPLICATION_JSON_VALUE)
    public EvidenceViews.PolicySetView policySet() {
        return evidence.policySet();
    }

    @GetMapping(path = "/capabilities", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<EvidenceViews.CapabilityView> capabilities() {
        return evidence.capabilities();
    }

    @GetMapping(path = "/reliability/report", produces = MediaType.APPLICATION_JSON_VALUE)
    public ReliabilityCalculator.Report reliabilityReport() {
        return reliability.report();
    }
}
