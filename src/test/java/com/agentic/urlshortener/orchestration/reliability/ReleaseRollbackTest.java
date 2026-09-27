package com.agentic.urlshortener.orchestration.reliability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.agentic.urlshortener.common.security.ApiPrincipal;
import com.agentic.urlshortener.common.security.Role;
import com.agentic.urlshortener.orchestration.audit.AuditService;
import com.agentic.urlshortener.orchestration.domain.RunStatus;
import com.agentic.urlshortener.orchestration.domain.StageStatus;
import com.agentic.urlshortener.orchestration.domain.StageType;
import com.agentic.urlshortener.orchestration.domain.WorkflowRun;
import com.agentic.urlshortener.orchestration.dto.GateDecisionRequest;
import com.agentic.urlshortener.orchestration.dto.RequirementSubmission;
import com.agentic.urlshortener.orchestration.governance.GateService;
import com.agentic.urlshortener.orchestration.port.ApplicationPlanePort;
import com.agentic.urlshortener.orchestration.repository.StageNodeRepository;
import com.agentic.urlshortener.orchestration.repository.WorkflowRunRepository;
import com.agentic.urlshortener.orchestration.service.WorkflowService;
import com.agentic.urlshortener.support.IntegrationTest;
import com.agentic.urlshortener.support.RequirementFixtures;

/**
 * T070 (FR-REL-05, FR-RDY-03, RDR-03): with the real agents, a simulated post-release verification
 * failure on RELEASE rolls the release state back inside the stage, and the run safe-stops with the
 * capability unreleased. Gate decisions are simulated human input.
 */
@IntegrationTest
@Tag("FR-REL-05")
@Tag("FR-RDY-03")
@Tag("RDR-03")
class ReleaseRollbackTest {

    private static final ApiPrincipal ALICE = new ApiPrincipal("alice", "Alice", Set.of(Role.REQUESTER));
    private static final ApiPrincipal BOB = new ApiPrincipal("bob", "Bob", Set.of(Role.APPROVER));
    private static final ApiPrincipal CAROL = new ApiPrincipal("carol", "Carol", Set.of(Role.RELEASE_OWNER));

    @Autowired private WorkflowService workflows;
    @Autowired private GateService gates;
    @Autowired private WorkflowRunRepository runs;
    @Autowired private StageNodeRepository nodes;
    @Autowired private ApplicationPlanePort port;
    @Autowired private AuditService audit;

    private void awaitGate(UUID runId, StageType gate) {
        await().atMost(Duration.ofSeconds(60)).pollInterval(Duration.ofMillis(50)).until(() -> {
            assertThat(runs.findById(runId).orElseThrow().getStatus().isTerminal()).as("run ended early").isFalse();
            return nodes.findByRunIdAndStageKey(runId, gate).orElseThrow().getStatus() == StageStatus.AWAITING_DECISION;
        });
    }

    @Test
    void aFailedPostReleaseVerificationRollsTheReleaseBackAndSafeStops() {
        UUID runId = workflows.submit(new RequirementSubmission("GF-001", "Custom aliases for short links.",
                "As an API consumer, I want to optionally choose a custom alias when I create a short link so that I can share "
                        + "memorable links.", "NEW_CAPABILITY", RequirementFixtures.GF_001_CRITERIA,
                List.of("Aliases are case-sensitive, like generated codes, and share the code namespace."),
                new RequirementSubmission.SimulationOptions(null,
                        List.of(new RequirementSubmission.FaultSpec("RELEASE", "VERIFICATION_FAILURE", 1, null, null)))), ALICE);

        awaitGate(runId, StageType.ARCHITECTURE_APPROVAL);
        gates.decide(runId, StageType.ARCHITECTURE_APPROVAL, new GateDecisionRequest("APPROVE", "RDR-03 drill (simulated human input)"), BOB);
        awaitGate(runId, StageType.RELEASE_APPROVAL);
        gates.decide(runId, StageType.RELEASE_APPROVAL, new GateDecisionRequest("APPROVE", "RDR-03 drill (simulated human input)"), CAROL);
        await().atMost(Duration.ofSeconds(60)).until(() -> runs.findById(runId).orElseThrow().getStatus().isTerminal());

        WorkflowRun run = runs.findById(runId).orElseThrow();
        assertThat(run.getStatus()).isEqualTo(RunStatus.SAFE_STOPPED);
        assertThat(run.getTerminalReason()).contains("RELEASE").contains("rolled back");
        assertThat(port.capability("custom-alias").released()).isFalse();
        assertThat(audit.chain(runId.toString())).filteredOn(e -> e.getAction().equals("CAPABILITY_CHANGED")).isEmpty();
        assertThat(audit.chain(AuditService.GLOBAL_CHAIN)).filteredOn(e -> e.getAction().equals("CAPABILITY_CHANGED")
                && e.getDetails() != null && e.getDetails().contains(runId.toString()))
                .extracting(e -> e.getToState()).containsExactly("true", "false");
    }
}
