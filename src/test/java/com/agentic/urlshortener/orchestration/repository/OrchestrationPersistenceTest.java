package com.agentic.urlshortener.orchestration.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.agentic.urlshortener.orchestration.domain.ActorType;
import com.agentic.urlshortener.orchestration.domain.Artifact;
import com.agentic.urlshortener.orchestration.domain.ChangeRequest;
import com.agentic.urlshortener.orchestration.domain.Classification;
import com.agentic.urlshortener.orchestration.domain.Decision;
import com.agentic.urlshortener.orchestration.domain.DecisionType;
import com.agentic.urlshortener.orchestration.domain.FailureClass;
import com.agentic.urlshortener.orchestration.domain.FailureEvent;
import com.agentic.urlshortener.orchestration.domain.PlanVersion;
import com.agentic.urlshortener.orchestration.domain.PolicyEvaluation;
import com.agentic.urlshortener.orchestration.domain.PolicyExceptionRecord;
import com.agentic.urlshortener.orchestration.domain.RequirementVersion;
import com.agentic.urlshortener.orchestration.domain.RunStatus;
import com.agentic.urlshortener.orchestration.domain.StageAttempt;
import com.agentic.urlshortener.orchestration.domain.StageNode;
import com.agentic.urlshortener.orchestration.domain.StageStatus;
import com.agentic.urlshortener.orchestration.domain.StageType;
import com.agentic.urlshortener.orchestration.domain.WorkflowRun;

/** T036: every control-plane entity round-trips through the Flyway schema; stage nodes are optimistically locked. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Tag("FR-ORC-08")
@Tag("FR-ORC-10")
class OrchestrationPersistenceTest {

    private static final Instant NOW = Instant.parse("2026-09-26T10:00:00Z");

    @Autowired private WorkflowRunRepository runs;
    @Autowired private RequirementVersionRepository requirements;
    @Autowired private PlanVersionRepository plans;
    @Autowired private StageNodeRepository nodes;
    @Autowired private StageAttemptRepository attempts;
    @Autowired private ArtifactRepository artifacts;
    @Autowired private DecisionRepository decisions;
    @Autowired private ChangeRequestRepository changeRequests;
    @Autowired private PolicyEvaluationRepository evaluations;
    @Autowired private PolicyExceptionRepository exceptions;
    @Autowired private FailureEventRepository failures;
    @Autowired private PlatformTransactionManager transactionManager;

    private WorkflowRun newRun() {
        return runs.saveAndFlush(WorkflowRun.create(UUID.randomUUID(), "GF-001", "Custom aliases", "alice",
                Classification.NEW_CAPABILITY, "1.0.0", NOW));
    }

    @Test
    void workflowRunRoundTrips() {
        WorkflowRun run = newRun();
        WorkflowRun loaded = runs.findById(run.getId()).orElseThrow();
        assertThat(loaded.getStatus()).isEqualTo(RunStatus.CREATED);
        assertThat(loaded.getRequestedBy()).isEqualTo("alice");
        assertThat(loaded.getPolicySetVersion()).isEqualTo("1.0.0");
        assertThat(loaded.getCurrentPlanVersion()).isEqualTo(1);
        assertThat(loaded.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void everyDependentEntityRoundTrips() {
        UUID runId = newRun().getId();
        requirements.saveAndFlush(RequirementVersion.create(runId, 1, "SUBMITTED", "{\"title\":\"x\"}", "f".repeat(64), "alice", NOW, null));
        plans.saveAndFlush(PlanVersion.create(runId, 1, "{\"stages\":[]}", null, "INITIAL", "initial plan", "system", NOW));
        StageNode node = nodes.saveAndFlush(StageNode.create(runId, StageType.DESIGN, List.of(StageType.DECOMPOSITION, StageType.THREAT_ASSESSMENT)));
        attempts.saveAndFlush(StageAttempt.start(runId, StageType.DESIGN, 1, 1, "designer@1.0", false, null, 7L, NOW, "a".repeat(64)));
        artifacts.saveAndFlush(Artifact.create(runId, StageType.DESIGN, 1, 1, "DESIGN", 1, "application/json", "{}", "b".repeat(64),
                "designer@1.0", "[]", NOW));
        decisions.saveAndFlush(Decision.create(runId, StageType.ARCHITECTURE_APPROVAL, DecisionType.GATE, "APPROVED", ActorType.HUMAN,
                "bob", "APPROVER", "looks good", null, "{\"DESIGN\":\"" + "b".repeat(64) + "\"}", NOW));
        changeRequests.saveAndFlush(ChangeRequest.create(runId, "alice", NOW, "scope change", "{}", true, "{}"));
        evaluations.saveAndFlush(PolicyEvaluation.create(runId, StageType.COMPLIANCE_EVALUATION, 1, "SEC-001", "1.0.0", "MANDATORY", "PASS",
                "all probes passed", null, false, NOW));
        exceptions.saveAndFlush(PolicyExceptionRecord.create(runId, "LIC-001", "reason", "scope", "control", "alice", NOW, NOW.plusSeconds(3600)));
        failures.saveAndFlush(FailureEvent.open(runId, StageType.TESTING, 1, FailureClass.TRANSIENT, "AGENT_ERROR", true, NOW));

        StageNode loadedNode = nodes.findByRunIdAndStageKey(runId, StageType.DESIGN).orElseThrow();
        assertThat(loadedNode.getDependsOn()).containsExactly(StageType.DECOMPOSITION, StageType.THREAT_ASSESSMENT);
        assertThat(loadedNode.getStatus()).isEqualTo(StageStatus.PENDING);
        assertThat(loadedNode.getGeneration()).isEqualTo(1);
        assertThat(node.getId()).isNotNull();
        assertThat(requirements.findByRunIdOrderByVersionAsc(runId)).hasSize(1);
        assertThat(plans.findByRunIdOrderByVersionAsc(runId)).hasSize(1);
        assertThat(attempts.findByRunIdOrderByStartedAtAsc(runId)).singleElement()
                .satisfies(a -> assertThat(a.getSchedulingCycle()).isEqualTo(7L));
        assertThat(artifacts.findByRunIdOrderByCreatedAtAsc(runId)).singleElement()
                .satisfies(a -> assertThat(a.getFingerprint()).isEqualTo("b".repeat(64)));
        assertThat(decisions.findByRunIdOrderByCreatedAtAsc(runId)).singleElement()
                .satisfies(d -> assertThat(d.isValid()).isTrue());
        assertThat(changeRequests.findByRunIdOrderByRequestedAtAsc(runId)).hasSize(1);
        assertThat(evaluations.findByRunIdOrderByEvaluatedAtAsc(runId)).hasSize(1);
        assertThat(exceptions.findByRunIdOrderByRequestedAtAsc(runId)).hasSize(1);
        assertThat(failures.findByRunId(runId)).singleElement()
                .satisfies(f -> assertThat(f.isSimulated()).isTrue());
    }

    @Test
    void stageNodeUsesOptimisticLocking() {
        UUID runId = newRun().getId();
        StageNode created = nodes.saveAndFlush(StageNode.create(runId, StageType.ARCHITECTURE_APPROVAL, List.of(StageType.DESIGN)));
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        StageNode first = tx.execute(s -> nodes.findById(created.getId()).orElseThrow());
        StageNode second = tx.execute(s -> nodes.findById(created.getId()).orElseThrow());
        first.transitionTo(StageStatus.READY);
        tx.executeWithoutResult(s -> nodes.saveAndFlush(first));

        second.transitionTo(StageStatus.READY);
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> nodes.saveAndFlush(second)))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }
}
