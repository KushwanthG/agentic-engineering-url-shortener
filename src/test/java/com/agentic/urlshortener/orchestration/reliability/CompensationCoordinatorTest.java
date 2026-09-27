package com.agentic.urlshortener.orchestration.reliability;

import static com.agentic.urlshortener.orchestration.domain.StageType.RELEASE_APPROVAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.agentic.urlshortener.orchestration.agent.ArtifactDraft;
import com.agentic.urlshortener.orchestration.audit.AuditService;
import com.agentic.urlshortener.orchestration.domain.ActorType;
import com.agentic.urlshortener.orchestration.domain.AuditEvent;
import com.agentic.urlshortener.orchestration.domain.RunStatus;
import com.agentic.urlshortener.orchestration.domain.StageType;
import com.agentic.urlshortener.orchestration.domain.WorkflowRun;
import com.agentic.urlshortener.orchestration.engine.ArtifactStore;
import com.agentic.urlshortener.orchestration.port.ApplicationPlanePort;
import com.agentic.urlshortener.orchestration.port.ProbeResponse;
import com.agentic.urlshortener.orchestration.port.SyntheticLinkSpec;
import com.agentic.urlshortener.orchestration.repository.StageNodeRepository;
import com.agentic.urlshortener.orchestration.repository.WorkflowRunRepository;
import com.agentic.urlshortener.orchestration.service.WorkflowService;
import com.agentic.urlshortener.shortener.dto.CreateLinkCommand;
import com.agentic.urlshortener.shortener.service.LinkCreationService;
import com.agentic.urlshortener.support.GovernanceHarness;
import com.agentic.urlshortener.support.IntegrationTest;
import com.agentic.urlshortener.support.ScriptedAgent;

/**
 * T070 (FR-REL-04, FR-REL-05, FR-REL-10, ADR-010): compensation runs in reverse completion order
 * (release, then synthetic data), deletes only the run's synthetic rows, leaves a flag another run
 * changed as it is (recorded conflict), retries each action three times, and reports a failure so
 * the run is flagged for manual intervention.
 */
@IntegrationTest
@Import(ScriptedAgent.Config.class)
@Tag("FR-REL-04")
@Tag("FR-REL-05")
@Tag("FR-REL-10")
@Tag("RDR-03")
class CompensationCoordinatorTest {

    private static final String CAPABILITY = "click-limit";

    @Autowired private MockMvc mvc;
    @Autowired private WorkflowService workflows;
    @Autowired private WorkflowRunRepository runs;
    @Autowired private StageNodeRepository nodes;
    @Autowired private CompensationCoordinator compensation;
    @Autowired private SafeStopService safeStops;
    @Autowired private ArtifactStore artifacts;
    @Autowired private LinkCreationService links;
    @Autowired private AuditService audit;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private ScriptedAgent.Scripts scripts;
    @MockitoSpyBean private ApplicationPlanePort port;

    private GovernanceHarness harness;

    @BeforeEach
    void setUp() {
        harness = new GovernanceHarness(mvc, workflows, runs, nodes);
        scripts.reset();
    }

    @AfterEach
    void restoreFlag() {
        reset(port);
        port.setRelease(CAPABILITY, false, Map.of(), null, "test cleanup");
    }

    private UUID runThatReleased() {
        UUID runId = harness.submit(GovernanceHarness.ALICE);
        harness.awaitGate(runId, RELEASE_APPROVAL);
        port.setRelease(CAPABILITY, true, Map.of(), runId, "test: the run released the capability");
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> artifacts.store(runId, StageType.RELEASE, 1, 1, "test",
                List.of(ArtifactDraft.json("RELEASE_RECORD", "{\"capability\":\"" + CAPABILITY + "\",\"previouslyReleased\":false,"
                        + "\"released\":true,\"parameters\":{},\"verification\":{\"passed\":true,\"evidence\":\"test\"},"
                        + "\"rolledBack\":false,\"mechanism\":\"NONE\"}")),
                Map.of(), Instant.now()));
        return runId;
    }

    private List<AuditEvent> compensationEvents(UUID runId) {
        return audit.chain(runId.toString()).stream().filter(e -> e.getAction().equals("COMPENSATION_ACTION")).toList();
    }

    @Test
    void compensationRollsBackTheReleaseThenRemovesOnlyThisRunsSyntheticData() {
        UUID runId = runThatReleased();
        UUID otherRun = UUID.randomUUID();
        ProbeResponse own = port.createSyntheticLink(runId, new SyntheticLinkSpec("https://example.com/own", null, null, null));
        ProbeResponse others = port.createSyntheticLink(otherRun, new SyntheticLinkSpec("https://example.com/other", null, null, null));
        String consumerCode = links.create(new CreateLinkCommand("https://example.com/consumer", null, null, null, "demo-consumer", null))
                .view().code();

        CompensationCoordinator.Result result = new TransactionTemplate(transactionManager)
                .execute(status -> compensation.compensate(runId, "test compensation"));

        assertThat(result.succeeded()).isTrue();
        assertThat(result.actions()).extracting(CompensationCoordinator.Action::target).containsExactly("RELEASE", "SYNTHETIC_DATA");
        assertThat(port.capability(CAPABILITY).released()).isFalse();
        assertThat(port.findLink(own.code())).isEmpty();
        assertThat(port.findLink(others.code())).isPresent();
        assertThat(port.findLink(consumerCode)).isPresent();
        assertThat(compensationEvents(runId)).extracting(AuditEvent::getTarget).containsExactly("RELEASE", "SYNTHETIC_DATA");
        port.deleteSyntheticLinks(otherRun);
    }

    @Test
    void aFlagChangedByAnotherRunIsLeftAsItIsAndTheConflictIsRecorded() {
        UUID runId = runThatReleased();
        UUID laterRun = UUID.randomUUID();
        // A later run withdrew and re-released the capability (setting the same value again is a no-op).
        port.setRelease(CAPABILITY, false, Map.of(), laterRun, "test: a later run withdrew the capability");
        port.setRelease(CAPABILITY, true, Map.of(), laterRun, "test: the later run released it again");

        CompensationCoordinator.Result result = new TransactionTemplate(transactionManager)
                .execute(status -> compensation.compensate(runId, "test compensation"));

        assertThat(result.actions().get(0).result()).isEqualTo("CONFLICT");
        assertThat(result.succeeded()).isTrue();
        assertThat(port.capability(CAPABILITY).released()).isTrue();
        assertThat(port.capability(CAPABILITY).changedByRun()).isEqualTo(laterRun);
        assertThat(compensationEvents(runId).get(0).getResult()).isEqualTo("CONFLICT");
    }

    @Test
    void eachActionIsRetriedUpToThreeTimes() {
        UUID runId = runThatReleased();
        doThrow(new TransientDataAccessResourceException("store busy")).doThrow(new TransientDataAccessResourceException("store busy"))
                .doCallRealMethod().when(port).deleteSyntheticLinks(eq(runId));

        CompensationCoordinator.Result result = new TransactionTemplate(transactionManager)
                .execute(status -> compensation.compensate(runId, "test compensation"));

        assertThat(result.succeeded()).isTrue();
        assertThat(result.actions().get(1).tries()).isEqualTo(3);
    }

    @Test
    void aCompensationThatKeepsFailingSafeStopsTheRunForManualIntervention() {
        UUID runId = runThatReleased();
        doThrow(new TransientDataAccessResourceException("store down")).when(port).deleteSyntheticLinks(any());

        assertThat(safeStops.stop(runId, SafeStopService.Trigger.OPERATOR_REQUEST, "test: stop with failing compensation",
                ActorType.HUMAN, "carol")).isTrue();

        WorkflowRun run = runs.findById(runId).orElseThrow();
        assertThat(run.getStatus()).isEqualTo(RunStatus.SAFE_STOPPED);
        assertThat(run.isManualInterventionRequired()).isTrue();
        assertThat(port.capability(CAPABILITY).released()).isFalse();
        assertThat(compensationEvents(runId)).extracting(AuditEvent::getResult).containsExactly("OK", "FAILED");
        doCallRealMethod().when(port).deleteSyntheticLinks(any());
    }
}
