package com.agentic.urlshortener.orchestration.governance;

import static com.agentic.urlshortener.orchestration.domain.StageType.ARCHITECTURE_APPROVAL;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.agentic.urlshortener.orchestration.agent.ArtifactDraft;
import com.agentic.urlshortener.orchestration.agent.StageResult;
import com.agentic.urlshortener.orchestration.domain.StageType;
import com.agentic.urlshortener.orchestration.repository.DecisionRepository;
import com.agentic.urlshortener.orchestration.repository.StageNodeRepository;
import com.agentic.urlshortener.orchestration.repository.WorkflowRunRepository;
import com.agentic.urlshortener.orchestration.service.WorkflowService;
import com.agentic.urlshortener.support.GovernanceHarness;
import com.agentic.urlshortener.support.IntegrationTest;
import com.agentic.urlshortener.support.ScriptedAgent;
import com.agentic.urlshortener.support.Tokens;

/** T062 (FR-GOV-06, FR-GOV-09): two approvers race on one gate; exactly one decision is recorded. */
@IntegrationTest
@Import(ScriptedAgent.Config.class)
@Tag("FR-GOV-06")
@Tag("FR-GOV-09")
class ConcurrentGateDecisionTest {

    @Autowired private MockMvc mvc;
    @Autowired private WorkflowService workflows;
    @Autowired private WorkflowRunRepository runs;
    @Autowired private StageNodeRepository nodes;
    @Autowired private DecisionRepository decisions;
    @Autowired private ScriptedAgent.Scripts scripts;

    private GovernanceHarness harness;

    @BeforeEach
    void materialDesign() {
        harness = new GovernanceHarness(mvc, workflows, runs, nodes);
        scripts.reset();
        scripts.set(StageType.DESIGN, context -> new StageResult.Succeeded(List.of(ArtifactDraft.json("DESIGN",
                "{\"materialChange\":true,\"materialReasons\":[\"public API change\"]}")), "scripted material design"));
    }

    @Test
    void racingApproversProduceExactlyOneDecision() throws Exception {
        UUID runId = harness.submit(GovernanceHarness.ALICE);
        harness.awaitGate(runId, ARCHITECTURE_APPROVAL);

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<MvcResult>> results = new ArrayList<>();
        try {
            for (String token : List.of(Tokens.APPROVER, Tokens.DUAL_ROLE)) {
                results.add(pool.submit(() -> {
                    start.await();
                    return harness.decide(runId, ARCHITECTURE_APPROVAL, token, "APPROVE").andReturn();
                }));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            List<String> bodies = new ArrayList<>();
            for (Future<MvcResult> result : results) {
                MvcResult mvcResult = result.get(20, TimeUnit.SECONDS);
                statuses.add(mvcResult.getResponse().getStatus());
                bodies.add(mvcResult.getResponse().getContentAsString());
            }
            assertThat(statuses).containsExactlyInAnyOrder(200, 409);
            assertThat(bodies).anyMatch(body -> body.contains("CONCURRENT_DECISION"));
        } finally {
            pool.shutdownNow();
        }
        assertThat(decisions.findByRunIdOrderByCreatedAtAsc(runId))
                .filteredOn(d -> d.getStageKey() == ARCHITECTURE_APPROVAL).hasSize(1);
    }
}
