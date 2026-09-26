package com.agentic.urlshortener.orchestration.engine;

import static com.agentic.urlshortener.orchestration.domain.StageType.ARCHITECTURE_APPROVAL;
import static com.agentic.urlshortener.orchestration.domain.StageType.CLARIFICATION;
import static com.agentic.urlshortener.orchestration.domain.StageType.DECOMPOSITION;
import static com.agentic.urlshortener.orchestration.domain.StageType.DESIGN;
import static com.agentic.urlshortener.orchestration.domain.StageType.IMPACT_ANALYSIS;
import static com.agentic.urlshortener.orchestration.domain.StageType.RELEASE_APPROVAL;
import static com.agentic.urlshortener.orchestration.domain.StageType.THREAT_ASSESSMENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import com.agentic.urlshortener.common.security.ApiPrincipal;
import com.agentic.urlshortener.common.security.Role;
import com.agentic.urlshortener.common.util.Fingerprints;
import com.agentic.urlshortener.orchestration.agent.StageResult;
import com.agentic.urlshortener.orchestration.audit.AuditService;
import com.agentic.urlshortener.orchestration.domain.Artifact;
import com.agentic.urlshortener.orchestration.domain.AuditEvent;
import com.agentic.urlshortener.orchestration.domain.FailureClass;
import com.agentic.urlshortener.orchestration.domain.RunStatus;
import com.agentic.urlshortener.orchestration.domain.StageAttempt;
import com.agentic.urlshortener.orchestration.domain.StageNode;
import com.agentic.urlshortener.orchestration.domain.StageStatus;
import com.agentic.urlshortener.orchestration.domain.StageType;
import com.agentic.urlshortener.orchestration.dto.GateDecisionRequest;
import com.agentic.urlshortener.orchestration.dto.RequirementSubmission;
import com.agentic.urlshortener.orchestration.governance.GateService;
import com.agentic.urlshortener.orchestration.repository.ArtifactRepository;
import com.agentic.urlshortener.orchestration.repository.StageAttemptRepository;
import com.agentic.urlshortener.orchestration.repository.StageNodeRepository;
import com.agentic.urlshortener.orchestration.repository.WorkflowRunRepository;
import com.agentic.urlshortener.orchestration.service.WorkflowService;
import com.agentic.urlshortener.support.IntegrationTest;
import com.agentic.urlshortener.support.ScriptedAgent;

/** T040: the coordinator's scheduling behavior, driven by scripted agents. */
@IntegrationTest
@Import(ScriptedAgent.Config.class)
@Tag("FR-ORC-04")
@Tag("FR-ORC-05")
@Tag("FR-ORC-06")
@Tag("FR-ORC-07")
@Tag("FR-ORC-08")
@Tag("FR-ORC-10")
@Tag("FR-AUD-06")
class RunCoordinatorTest {

    private static final ApiPrincipal ALICE = new ApiPrincipal("alice", "Alice", Set.of(Role.REQUESTER));
    private static final ApiPrincipal CAROL = new ApiPrincipal("carol", "Carol", Set.of(Role.RELEASE_OWNER));

    @Autowired private WorkflowService workflows;
    @Autowired private GateService gates;
    @Autowired private ScriptedAgent.Scripts scripts;
    @Autowired private WorkflowRunRepository runs;
    @Autowired private StageNodeRepository nodes;
    @Autowired private StageAttemptRepository attempts;
    @Autowired private ArtifactRepository artifacts;
    @Autowired private AuditService audit;

    @BeforeEach
    void resetScripts() {
        scripts.reset();
    }

    private UUID submit(String type) {
        return workflows.submit(new RequirementSubmission("ENG-1", "Engine test", "Scripted engine run", type,
                List.of("Given x, when y, then z"), List.of(), null), ALICE);
    }

    private void awaitStatus(UUID runId, RunStatus status) {
        await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(25))
                .until(() -> runs.findById(runId).orElseThrow().getStatus() == status);
    }

    private StageNode node(UUID runId, StageType type) {
        return nodes.findByRunIdAndStageKey(runId, type).orElseThrow();
    }

    @Test
    void fanOutBranchesRunConcurrentlyInOneSchedulingCycle() {
        for (StageType branch : List.of(DECOMPOSITION, THREAT_ASSESSMENT, IMPACT_ANALYSIS)) {
            scripts.sleepThenSucceed(branch, 300);
        }
        UUID runId = submit("CHANGE_TO_EXISTING");
        awaitStatus(runId, RunStatus.AWAITING_HUMAN);

        List<StageAttempt> branches = attempts.findByRunIdOrderByStartedAtAsc(runId).stream()
                .filter(a -> Set.of(DECOMPOSITION, THREAT_ASSESSMENT, IMPACT_ANALYSIS).contains(a.getStageKey()))
                .toList();
        assertThat(branches).hasSize(3);
        Instant firstStart = branches.stream().map(StageAttempt::getStartedAt).min(Instant::compareTo).orElseThrow();
        Instant lastFinish = branches.stream().map(StageAttempt::getFinishedAt).max(Instant::compareTo).orElseThrow();
        assertThat(Duration.between(firstStart, lastFinish)).isLessThan(Duration.ofMillis(750));
        assertThat(branches.stream().map(StageAttempt::getSchedulingCycle).collect(Collectors.toSet())).hasSize(1);

        StageAttempt design = attempts.findByRunIdOrderByStartedAtAsc(runId).stream()
                .filter(a -> a.getStageKey() == DESIGN).findFirst().orElseThrow();
        assertThat(design.getStartedAt()).isAfterOrEqualTo(lastFinish);
        assertThat(design.getSchedulingCycle()).isGreaterThan(branches.getFirst().getSchedulingCycle());
    }

    @Test
    void joinNeverStartsAfterAPredecessorFails() {
        scripts.set(THREAT_ASSESSMENT, context -> new StageResult.Failed(FailureClass.PERMANENT, "scripted permanent failure"));
        UUID runId = submit("NEW_CAPABILITY");
        awaitStatus(runId, RunStatus.SAFE_STOPPED);

        assertThat(node(runId, THREAT_ASSESSMENT).getStatus()).isEqualTo(StageStatus.FAILED);
        assertThat(attempts.findByRunIdOrderByStartedAtAsc(runId)).noneMatch(a -> a.getStageKey() == DESIGN);
        assertThat(node(runId, DESIGN).getStatus()).isIn(StageStatus.PENDING, StageStatus.CANCELLED);
        assertThat(runs.findById(runId).orElseThrow().getTerminalReason()).contains("THREAT_ASSESSMENT");
    }

    @Test
    void conditionalStagesAreSkippedWithAReason() {
        UUID runId = submit("NEW_CAPABILITY");
        awaitStatus(runId, RunStatus.AWAITING_HUMAN);

        assertThat(node(runId, CLARIFICATION).getStatus()).isEqualTo(StageStatus.SKIPPED);
        assertThat(node(runId, CLARIFICATION).getSkipReason()).contains("scripted: no blocking ambiguity");
        assertThat(node(runId, ARCHITECTURE_APPROVAL).getStatus()).isEqualTo(StageStatus.SKIPPED);
        assertThat(node(runId, ARCHITECTURE_APPROVAL).getSkipReason()).isNotBlank();
        assertThat(node(runId, RELEASE_APPROVAL).getStatus()).isEqualTo(StageStatus.AWAITING_DECISION);
    }

    @Test
    void artifactsRecordInputsAndFingerprints() {
        UUID runId = submit("CHANGE_TO_EXISTING");
        awaitStatus(runId, RunStatus.AWAITING_HUMAN);

        List<Artifact> all = artifacts.findByRunIdOrderByCreatedAtAsc(runId);
        Artifact design = all.stream().filter(a -> a.getArtifactType().equals("DESIGN")).findFirst().orElseThrow();
        assertThat(design.getFingerprint()).isEqualTo(Fingerprints.sha256(design.getContent()));
        assertThat(design.getProducedBy()).isEqualTo("scripted-design@test");
        assertThat(design.getInputRefs()).contains("TASK_GRAPH", "THREAT_MODEL", "IMPACT_ANALYSIS");
        Artifact taskGraph = all.stream().filter(a -> a.getArtifactType().equals("TASK_GRAPH")).findFirst().orElseThrow();
        assertThat(design.getInputRefs()).contains(taskGraph.getId().toString()).contains(taskGraph.getFingerprint());
    }

    @Test
    void stateIsPersistedBeforeTheAgentRuns() {
        Map<String, String> observed = new ConcurrentHashMap<>();
        scripts.set(DESIGN, context -> {
            StageNode persisted = nodes.findByRunIdAndStageKey(context.runId(), DESIGN).orElseThrow();
            observed.put("status", persisted.getStatus().name());
            observed.put("attempts", String.valueOf(attempts.findByRunIdOrderByStartedAtAsc(context.runId()).stream()
                    .filter(a -> a.getStageKey() == DESIGN).count()));
            return ScriptedAgent.defaultSuccess(DESIGN);
        });
        UUID runId = submit("NEW_CAPABILITY");
        awaitStatus(runId, RunStatus.AWAITING_HUMAN);

        assertThat(observed).containsEntry("status", "RUNNING").containsEntry("attempts", "1");
    }

    @Test
    void runCompletesAfterTheReleaseDecisionAndEveryTransitionIsAudited() {
        UUID runId = submit("NEW_CAPABILITY");
        awaitStatus(runId, RunStatus.AWAITING_HUMAN);
        gates.decide(runId, RELEASE_APPROVAL, new GateDecisionRequest("APPROVE", "scripted release approval"), CAROL);
        awaitStatus(runId, RunStatus.COMPLETED);

        var run = runs.findById(runId).orElseThrow();
        assertThat(run.getTerminalOutcome()).isEqualTo("COMPLETED");
        assertThat(run.getCompletedAt()).isNotNull();
        assertThat(nodes.findByRunIdOrderByStageKeyAsc(runId))
                .allSatisfy(n -> assertThat(n.getStatus()).isIn(StageStatus.SUCCEEDED, StageStatus.SKIPPED));

        List<AuditEvent> events = audit.chain(runId.toString());
        assertThat(events).extracting(AuditEvent::getAction)
                .contains("RUN_CREATED", "PLAN_VERSION_CREATED", "STAGE_TRANSITION", "ATTEMPT_STARTED", "ARTIFACT_RECORDED",
                        "GATE_DECIDED", "RUN_TERMINATED");
        assertThat(events).filteredOn(e -> e.getAction().equals("ATTEMPT_STARTED"))
                .allSatisfy(e -> assertThat(e.getDetails()).contains("schedulingCycle"));
        assertThat(audit.verify(runId.toString()).valid()).isTrue();
    }
}
