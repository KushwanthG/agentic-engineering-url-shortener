package com.agentic.urlshortener.orchestration.reliability;

import static com.agentic.urlshortener.orchestration.domain.StageType.DESIGN;
import static com.agentic.urlshortener.orchestration.domain.StageType.DOCUMENTATION;
import static com.agentic.urlshortener.orchestration.domain.StageType.RELEASE_APPROVAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.agentic.urlshortener.orchestration.domain.AttemptOutcome;
import com.agentic.urlshortener.orchestration.domain.FailureEvent;
import com.agentic.urlshortener.orchestration.domain.StageAttempt;
import com.agentic.urlshortener.orchestration.domain.StageType;
import com.agentic.urlshortener.orchestration.dto.RequirementSubmission;
import com.agentic.urlshortener.orchestration.dto.RequirementSubmission.FaultSpec;
import com.agentic.urlshortener.orchestration.repository.FailureEventRepository;
import com.agentic.urlshortener.orchestration.repository.StageAttemptRepository;
import com.agentic.urlshortener.orchestration.repository.StageNodeRepository;
import com.agentic.urlshortener.orchestration.repository.WorkflowRunRepository;
import com.agentic.urlshortener.orchestration.service.WorkflowService;
import com.agentic.urlshortener.support.GovernanceHarness;
import com.agentic.urlshortener.support.IntegrationTest;
import com.agentic.urlshortener.support.ScriptedAgent;
import com.agentic.urlshortener.support.Tokens;

/**
 * T075 (FR-REL-11, Constitution X): faults are accepted only when fault injection is enabled, apply
 * per stage and occurrence, and every attempt and failure event they cause is flagged simulated.
 */
@IntegrationTest
@Import(ScriptedAgent.Config.class)
@TestPropertySource(properties = "app.orchestration.stages.defaults.initial-backoff=PT0.05S")
@Tag("FR-REL-11")
class FaultInjectionTest {

    @Autowired private MockMvc mvc;
    @Autowired private WorkflowService workflows;
    @Autowired private WorkflowRunRepository runs;
    @Autowired private StageNodeRepository nodes;
    @Autowired private StageAttemptRepository attempts;
    @Autowired private FailureEventRepository failures;
    @Autowired private ScriptedAgent.Scripts scripts;

    private GovernanceHarness harness;

    @BeforeEach
    void setUp() {
        harness = new GovernanceHarness(mvc, workflows, runs, nodes);
        scripts.reset();
    }

    private UUID submitWith(FaultSpec... faults) {
        return harness.submit(GovernanceHarness.ALICE, new RequirementSubmission.SimulationOptions(null, List.of(faults)));
    }

    private List<StageAttempt> attemptsOf(UUID runId, StageType stage) {
        return attempts.findByRunIdOrderByStartedAtAsc(runId).stream().filter(a -> a.getStageKey() == stage).toList();
    }

    @Test
    void aTransientFaultAppliesToTheConfiguredNumberOfAttemptsOnly() {
        AtomicInteger agentCalls = new AtomicInteger();
        scripts.set(DESIGN, context -> {
            agentCalls.incrementAndGet();
            return ScriptedAgent.defaultSuccess(DESIGN);
        });
        UUID runId = submitWith(new FaultSpec("DESIGN", "TRANSIENT_ERROR", 2, null, null));
        harness.awaitGate(runId, RELEASE_APPROVAL);

        List<StageAttempt> design = attemptsOf(runId, DESIGN);
        assertThat(design).extracting(StageAttempt::getOutcome)
                .containsExactly(AttemptOutcome.FAILED_TRANSIENT, AttemptOutcome.FAILED_TRANSIENT, AttemptOutcome.SUCCEEDED);
        assertThat(design).extracting(StageAttempt::getSimulatedFault).containsExactly("TRANSIENT_ERROR", "TRANSIENT_ERROR", null);
        assertThat(design.get(0).getError()).contains("simulated");
        assertThat(agentCalls).as("the injected fault replaces the agent call").hasValue(1);
        assertThat(failures.findByRunId(runId)).singleElement().satisfies(event -> {
            assertThat(event.isSimulated()).isTrue();
            assertThat(event.getStatus()).isEqualTo(FailureEvent.RECOVERED);
        });
    }

    @Test
    void aPermanentFaultOnDocumentationTriggersTheFallback() {
        UUID runId = submitWith(new FaultSpec("DOCUMENTATION", "PERMANENT_ERROR", 1, null, null));
        harness.awaitGate(runId, RELEASE_APPROVAL);

        assertThat(attemptsOf(runId, DOCUMENTATION)).extracting(StageAttempt::getOutcome)
                .containsExactly(AttemptOutcome.FAILED_PERMANENT, AttemptOutcome.SUCCEEDED);
        assertThat(harness.node(runId, DOCUMENTATION).isDegraded()).isTrue();
    }

    @Test
    void aDelayFaultSlowsTheAttemptDown() {
        UUID runId = submitWith(new FaultSpec("DESIGN", "DELAY", 1, 400, null));
        harness.awaitGate(runId, RELEASE_APPROVAL);

        StageAttempt design = attemptsOf(runId, DESIGN).get(0);
        assertThat(design.getOutcome()).isEqualTo(AttemptOutcome.SUCCEEDED);
        assertThat(design.getSimulatedFault()).isEqualTo("DELAY");
        assertThat(Duration.between(design.getStartedAt(), design.getFinishedAt())).isGreaterThanOrEqualTo(Duration.ofMillis(400));
    }

    @Test
    void theRunIsMarkedSimulatedAndInvalidFaultsAreRejected() throws Exception {
        UUID runId = submitWith(new FaultSpec("DESIGN", "DELAY", 1, 10, null));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/workflows/" + runId)
                .header(HttpHeaders.AUTHORIZATION, Tokens.bearer(Tokens.AUDITOR)))
                .andExpect(jsonPath("$.simulated").value(true));

        for (String fault : List.of("{\"stage\":\"NOT_A_STAGE\",\"type\":\"TRANSIENT_ERROR\"}",
                "{\"stage\":\"DESIGN\",\"type\":\"DELAY\"}",
                "{\"stage\":\"DESIGN\",\"type\":\"POLICY_FAILURE\",\"policyId\":\"DOC-001\"}",
                "{\"stage\":\"COMPLIANCE_EVALUATION\",\"type\":\"POLICY_FAILURE\"}",
                "{\"stage\":\"ARCHITECTURE_APPROVAL\",\"type\":\"TRANSIENT_ERROR\"}")) {
            mvc.perform(post("/api/v1/workflows").header(HttpHeaders.AUTHORIZATION, Tokens.bearer(Tokens.REQUESTER))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"title\":\"Fault test\",\"narrative\":\"Invalid fault\",\"type\":\"NEW_CAPABILITY\","
                            + "\"acceptanceCriteria\":[\"Given x, when y, then z\"],\"simulation\":{\"faults\":[" + fault + "]}}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
    }

    /** The default profile keeps fault injection off: simulation options are refused. */
    @Nested
    @TestPropertySource(properties = "app.orchestration.fault-injection.enabled=false")
    class Disabled {

        @Test
        void simulationOptionsAreRefusedWhenFaultInjectionIsOff() throws Exception {
            mvc.perform(post("/api/v1/workflows").header(HttpHeaders.AUTHORIZATION, Tokens.bearer(Tokens.REQUESTER))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"title\":\"Fault test\",\"narrative\":\"Faults off\",\"type\":\"NEW_CAPABILITY\","
                            + "\"simulation\":{\"faults\":[{\"stage\":\"DESIGN\",\"type\":\"TRANSIENT_ERROR\"}]}}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("FAULT_INJECTION_DISABLED"));
        }
    }
}
