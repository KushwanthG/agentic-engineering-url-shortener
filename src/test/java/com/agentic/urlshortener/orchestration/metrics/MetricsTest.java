package com.agentic.urlshortener.orchestration.metrics;

import static com.agentic.urlshortener.orchestration.domain.StageType.DESIGN;
import static com.agentic.urlshortener.orchestration.domain.StageType.RELEASE_APPROVAL;
import static com.agentic.urlshortener.orchestration.domain.StageType.TESTING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.agentic.urlshortener.orchestration.agent.TransientStageException;
import com.agentic.urlshortener.orchestration.domain.RunStatus;
import com.agentic.urlshortener.orchestration.engine.StageDispatcher;
import com.agentic.urlshortener.orchestration.repository.StageNodeRepository;
import com.agentic.urlshortener.orchestration.repository.WorkflowRunRepository;
import com.agentic.urlshortener.orchestration.service.WorkflowService;
import com.agentic.urlshortener.support.GovernanceHarness;
import com.agentic.urlshortener.support.IntegrationTest;
import com.agentic.urlshortener.support.ScriptedAgent;
import com.agentic.urlshortener.support.Tokens;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.search.Search;

/**
 * T106 (NFR-OBS-02, FR-AUD-05): the meters of plan.md §7 are registered and move with real work:
 * - runs started and terminated by outcome;
 * - stage attempts by stage and outcome;
 * - retries and the stage-duration timer;
 * - the waiting-gates gauge and the executor gauge (review RC-6);
 * - the shortener's creation and redirect counters.
 * Each attempt is observed as {@code sdlc.stage}. No meter carries a run id as a tag (cardinality
 * guardrail). Agent logs carry {@code runId}, {@code stage}, and {@code attempt} in the MDC.
 */
@IntegrationTest
@Import(ScriptedAgent.Config.class)
@Tag("NFR-OBS-02")
@Tag("FR-AUD-05")
class MetricsTest {

    @Autowired private MockMvc mvc;
    @Autowired private MeterRegistry meters;
    @Autowired private WorkflowService workflows;
    @Autowired private WorkflowRunRepository runs;
    @Autowired private StageNodeRepository nodes;
    @Autowired private ScriptedAgent.Scripts scripts;

    private GovernanceHarness harness;
    private final List<ILoggingEvent> logged = new CopyOnWriteArrayList<>();
    private final AppenderBase<ILoggingEvent> capture = new AppenderBase<>() {
        @Override
        protected void append(ILoggingEvent event) {
            logged.add(event);
        }
    };

    @BeforeEach
    void setUp() {
        harness = new GovernanceHarness(mvc, workflows, runs, nodes);
        scripts.reset();
        capture.start();
        ((Logger) LoggerFactory.getLogger(StageDispatcher.class)).addAppender(capture);
    }

    @AfterEach
    void tearDown() {
        ((Logger) LoggerFactory.getLogger(StageDispatcher.class)).detachAppender(capture);
    }

    private double count(String name, String... tags) {
        Search search = meters.find(name).tags(tags);
        return search.counters().stream().mapToDouble(c -> c.count()).sum();
    }

    @Test
    void runStageAndGateMetersFollowTheRun() {
        AtomicInteger testingCalls = new AtomicInteger();
        scripts.set(TESTING, context -> {
            if (testingCalls.incrementAndGet() == 1) {
                throw new TransientStageException("flaky test runner (simulated)");
            }
            return ScriptedAgent.defaultSuccess(TESTING);
        });
        double started = count("sdlc.runs.started");
        double designSucceeded = count("sdlc.stage.attempts", "stage", "DESIGN", "outcome", "SUCCEEDED");
        double testingRetries = count("sdlc.stage.retries", "stage", "TESTING");
        double safeStopped = count("sdlc.runs.terminated", "outcome", "SAFE_STOPPED");

        UUID runId = harness.submit(GovernanceHarness.ALICE);
        harness.awaitGate(runId, RELEASE_APPROVAL);

        assertThat(count("sdlc.runs.started")).isEqualTo(started + 1);
        assertThat(count("sdlc.stage.attempts", "stage", "DESIGN", "outcome", "SUCCEEDED")).isEqualTo(designSucceeded + 1);
        assertThat(count("sdlc.stage.retries", "stage", "TESTING")).isEqualTo(testingRetries + 1);
        assertThat(count("sdlc.stage.attempts", "stage", "TESTING", "outcome", "FAILED_TRANSIENT")).isPositive();
        assertThat(meters.find("sdlc.stage.duration").tag("stage", "DESIGN").timer()).isNotNull()
                .satisfies(t -> assertThat(t.count()).isPositive());
        assertThat(meters.find("sdlc.gates.waiting").gauge()).isNotNull().satisfies(g -> assertThat(g.value()).isGreaterThanOrEqualTo(1));
        assertThat(meters.find("sdlc.executor.active").gauge()).isNotNull();
        // each attempt is observed as sdlc.stage, tagged by stage only
        assertThat(meters.find("sdlc.stage").tag("stage", "DESIGN").timer()).isNotNull();

        harness.awaitStatus(runId, RunStatus.AWAITING_HUMAN);
        mvcSafeStop(runId);
        await().atMost(Duration.ofSeconds(10))
                .until(() -> count("sdlc.runs.terminated", "outcome", "SAFE_STOPPED") == safeStopped + 1);
    }

    @Test
    void agentLogsCarryTheRunStageAndAttemptInTheMdc() {
        scripts.set(DESIGN, context -> {
            throw new IllegalStateException("design generator crashed (simulated)");
        });
        double failed = count("sdlc.stage.attempts", "stage", "DESIGN", "outcome", "FAILED_PERMANENT");
        UUID runId = harness.submit(GovernanceHarness.ALICE);
        harness.awaitStatus(runId, RunStatus.SAFE_STOPPED);

        assertThat(count("sdlc.stage.attempts", "stage", "DESIGN", "outcome", "FAILED_PERMANENT")).isEqualTo(failed + 1);
        assertThat(logged).filteredOn(e -> e.getFormattedMessage().contains("failed") && runId.toString().equals(e.getMDCPropertyMap().get("runId")))
                .isNotEmpty()
                .allSatisfy(e -> {
                    assertThat(e.getMDCPropertyMap()).containsEntry("stage", "DESIGN").containsEntry("attempt", "1");
                });
    }

    @Test
    void shortenerCountersFollowCreationAndRedirects() throws Exception {
        double created = count("shortener.links.created");
        double redirected = count("shortener.redirects", "outcome", "REDIRECT");
        double notFound = count("shortener.redirects", "outcome", "NOT_FOUND");

        String body = mvc.perform(post("/api/v1/links").header(HttpHeaders.AUTHORIZATION, Tokens.bearer(Tokens.CONSUMER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"url\":\"https://www.example.com/metrics\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String code = body.replaceAll(".*\"code\":\"([^\"]+)\".*", "$1");
        mvc.perform(get("/" + code)).andExpect(status().isFound());
        mvc.perform(get("/zzzz-unknown-code")).andExpect(status().isNotFound());

        assertThat(count("shortener.links.created")).isEqualTo(created + 1);
        assertThat(count("shortener.redirects", "outcome", "REDIRECT")).isEqualTo(redirected + 1);
        assertThat(count("shortener.redirects", "outcome", "NOT_FOUND")).isEqualTo(notFound + 1);
        assertThat(meters.find("shortener.analytics.failures").counter()).isNotNull();
        assertThat(meters.find("shortener.ratelimit.rejected").tag("limiter", "creation").counter()).isNotNull();
    }

    @Test
    void noMeterIsTaggedWithARunId() {
        scripts.set(DESIGN, context -> ScriptedAgent.defaultSuccess(DESIGN));
        UUID runId = harness.submit(GovernanceHarness.ALICE);
        harness.awaitGate(runId, RELEASE_APPROVAL);
        List<String> offending = meters.getMeters().stream()
                .filter(m -> m.getId().getName().startsWith("sdlc") || m.getId().getName().startsWith("shortener"))
                .filter(m -> m.getId().getTags().stream().anyMatch(t -> t.getKey().toLowerCase().contains("run")
                        || t.getValue().equals(runId.toString())))
                .map(Meter::getId).map(Object::toString).toList();
        assertThat(offending).isEmpty();
    }

    private void mvcSafeStop(UUID runId) {
        try {
            mvc.perform(post("/api/v1/workflows/" + runId + "/safe-stop").header(HttpHeaders.AUTHORIZATION, Tokens.bearer(Tokens.RELEASE_OWNER))
                    .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"metrics test stop" + GovernanceHarness.SIMULATED + "\"}"))
                    .andExpect(status().isOk());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
