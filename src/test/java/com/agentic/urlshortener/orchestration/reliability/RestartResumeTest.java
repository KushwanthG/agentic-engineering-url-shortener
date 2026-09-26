package com.agentic.urlshortener.orchestration.reliability;

import static com.agentic.urlshortener.orchestration.domain.StageType.DESIGN;
import static com.agentic.urlshortener.orchestration.domain.StageType.RELEASE_APPROVAL;
import static com.agentic.urlshortener.orchestration.domain.StageType.REQUIREMENT_INGESTION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import com.agentic.urlshortener.UrlShortenerApplication;
import com.agentic.urlshortener.common.security.ApiPrincipal;
import com.agentic.urlshortener.common.security.Role;
import com.agentic.urlshortener.orchestration.domain.AttemptOutcome;
import com.agentic.urlshortener.orchestration.domain.FailureEvent;
import com.agentic.urlshortener.orchestration.domain.RunStatus;
import com.agentic.urlshortener.orchestration.domain.StageAttempt;
import com.agentic.urlshortener.orchestration.domain.StageStatus;
import com.agentic.urlshortener.orchestration.dto.GateDecisionRequest;
import com.agentic.urlshortener.orchestration.dto.RequirementSubmission;
import com.agentic.urlshortener.orchestration.governance.GateService;
import com.agentic.urlshortener.orchestration.repository.FailureEventRepository;
import com.agentic.urlshortener.orchestration.repository.StageAttemptRepository;
import com.agentic.urlshortener.orchestration.repository.StageNodeRepository;
import com.agentic.urlshortener.orchestration.repository.WorkflowRunRepository;
import com.agentic.urlshortener.orchestration.service.WorkflowService;
import com.agentic.urlshortener.support.EvidenceExporter;
import com.agentic.urlshortener.support.ScriptedAgent;

/**
 * T073 (FR-REL-08, FR-ORC-08, SC-007, NFR-RCV-01, RDR-05): a run survives a restart. Context 1 runs
 * with a simulated 20 s DELAY on DESIGN and is closed mid-attempt (evidence label: graceful context
 * stop; the manual process-kill variant is in the runbook). Context 2, on the same H2 file database,
 * marks the attempt INTERRUPTED, re-dispatches DESIGN, and completes the run without repeating
 * completed stages. The release approval is simulated human input.
 */
@Tag("FR-REL-08")
@Tag("FR-ORC-08")
@Tag("SC-007")
@Tag("NFR-RCV-01")
@Tag("RDR-05")
class RestartResumeTest {

    private static final ApiPrincipal ALICE = new ApiPrincipal("alice", "Alice", Set.of(Role.REQUESTER));
    private static final ApiPrincipal CAROL = new ApiPrincipal("carol", "Carol", Set.of(Role.RELEASE_OWNER));

    @TempDir
    Path directory;

    private ConfigurableApplicationContext start(String url) {
        // Command-line arguments outrank the test profile's in-memory datasource (builder properties would not).
        return new SpringApplicationBuilder(UrlShortenerApplication.class, ScriptedAgent.Config.class)
                .profiles("test")
                .run("--spring.datasource.url=" + url, "--server.port=0",
                        "--app.orchestration.stages.defaults.initial-backoff=PT0.05S");
    }

    private static List<StageAttempt> attempts(ConfigurableApplicationContext context, UUID runId) {
        return context.getBean(StageAttemptRepository.class).findByRunIdOrderByStartedAtAsc(runId);
    }

    @Test
    void anInterruptedAttemptIsResumedAfterRestartAndTheRunCompletes() {
        String url = "jdbc:h2:file:" + directory.resolve("restart").toAbsolutePath()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;LOCK_TIMEOUT=10000;TIME ZONE=UTC";
        UUID runId;
        try (ConfigurableApplicationContext first = start(url)) {
            runId = first.getBean(WorkflowService.class).submit(new RequirementSubmission("RDR-05", "Restart drill",
                    "Scripted run interrupted by a context restart", "NEW_CAPABILITY", List.of("Given x, when y, then z"), List.of(),
                    new RequirementSubmission.SimulationOptions(null,
                            List.of(new RequirementSubmission.FaultSpec("DESIGN", "DELAY", 1, 20_000, null)))), ALICE);
            UUID id = runId;
            await().atMost(Duration.ofSeconds(20)).until(() -> attempts(first, id).stream().anyMatch(a -> a.getStageKey() == DESIGN));
        }

        Instant restartedAt = Instant.now();
        try (ConfigurableApplicationContext second = start(url)) {
            UUID id = runId;
            StageNodeRepository nodes = second.getBean(StageNodeRepository.class);
            await().atMost(Duration.ofSeconds(30)).until(() -> nodes.findByRunIdAndStageKey(id, DESIGN).orElseThrow().getStatus()
                    == StageStatus.SUCCEEDED);
            Instant resumedAt = attempts(second, id).stream().filter(a -> a.getStageKey() == DESIGN && a.getAttemptNo() == 2)
                    .findFirst().orElseThrow().getStartedAt();
            await().atMost(Duration.ofSeconds(20)).until(() -> nodes.findByRunIdAndStageKey(id, RELEASE_APPROVAL).orElseThrow().getStatus()
                    == StageStatus.AWAITING_DECISION);
            second.getBean(GateService.class).decide(id, RELEASE_APPROVAL,
                    new GateDecisionRequest("APPROVE", "restart drill release (simulated human input)"), CAROL);
            WorkflowRunRepository runs = second.getBean(WorkflowRunRepository.class);
            await().atMost(Duration.ofSeconds(20)).until(() -> runs.findById(id).orElseThrow().getStatus() == RunStatus.COMPLETED);

            List<StageAttempt> all = attempts(second, id);
            List<StageAttempt> design = all.stream().filter(a -> a.getStageKey() == DESIGN).toList();
            assertThat(design).extracting(StageAttempt::getOutcome).containsExactly(AttemptOutcome.INTERRUPTED, AttemptOutcome.SUCCEEDED);
            assertThat(all).filteredOn(a -> a.getStageKey() == REQUIREMENT_INGESTION).hasSize(1);
            assertThat(Duration.between(restartedAt, resumedAt)).as("resumed within PVT-22").isLessThan(Duration.ofSeconds(30));

            FailureEvent event = second.getBean(FailureEventRepository.class).findByRunId(id).stream()
                    .filter(f -> f.getStageKey() == DESIGN).findFirst().orElseThrow();
            assertThat(event.getCause()).isEqualTo("PROCESS_INTERRUPTION");
            assertThat(event.getMechanism()).isEqualTo("RESUME");
            assertThat(event.getStatus()).isEqualTo(FailureEvent.RECOVERED);

            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("drill", "RDR-05 restart and resume");
            evidence.put("interruption", "graceful context stop mid-attempt (the manual process-kill variant is in docs/operations/runbook.md)");
            evidence.put("runId", id.toString());
            evidence.put("designAttempts", design.stream().map(a -> Map.of("attemptNo", a.getAttemptNo(), "outcome",
                    a.getOutcome().name(), "startedAt", a.getStartedAt().toString())).toList());
            evidence.put("ingestionAttempts", 1);
            evidence.put("resumptionDelayMillis", Duration.between(restartedAt, resumedAt).toMillis());
            evidence.put("failureEvent", Map.of("cause", event.getCause(), "mechanism", event.getMechanism(), "status",
                    event.getStatus(), "detectedAt", event.getDetectedAt().toString(), "recoveredAt",
                    event.getRecoveryCompletedAt().toString()));
            evidence.put("terminalOutcome", runs.findById(id).orElseThrow().getStatus().name());
            new EvidenceExporter("drills", RestartResumeTest.class, true).json("RDR-05-restart-resume", evidence);
        }
    }
}
