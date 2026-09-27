package com.agentic.urlshortener.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.agentic.urlshortener.common.util.CanonicalJson;
import com.agentic.urlshortener.support.GovernanceInvariants;
import com.agentic.urlshortener.support.GovernanceInvariants.AuditEntry;
import com.agentic.urlshortener.support.GovernanceInvariants.DecisionEntry;
import com.agentic.urlshortener.support.GovernanceInvariants.Evaluation;
import com.agentic.urlshortener.support.GovernanceInvariants.RunEvidence;
import com.agentic.urlshortener.support.GovernanceInvariants.Stage;

/**
 * T132 (SC-004, FR-GOV-02, FR-GOV-03, FR-POL-03): the governance-invariant checker is proven
 * against deliberately violating event sequences before it is trusted on real runs. Each negative
 * fixture changes one fact of a valid baseline and must be reported. Each tolerated case (skipped
 * clarification, exception-covered release, a gate inserted later, the safe-stop summary) must not
 * be reported.
 */
@Tag("SC-004")
@Tag("FR-GOV-02")
@Tag("FR-GOV-03")
@Tag("FR-POL-03")
class GovernanceInvariantsTest {

    private static final Instant T0 = Instant.parse("2026-09-27T10:00:00Z");

    private static Instant t(int seconds) {
        return T0.plusSeconds(seconds);
    }

    private static AuditEntry attempt(long seq, String stage) {
        return new AuditEntry(seq, t((int) seq), "ATTEMPT_STARTED", stage, null, null);
    }

    private static AuditEntry transition(long seq, String stage, String from, String to) {
        return new AuditEntry(seq, t((int) seq), "STAGE_TRANSITION", stage, from, to);
    }

    private static DecisionEntry gate(String stage, String actor, int at) {
        return new DecisionEntry("GATE", "APPROVED", stage, "HUMAN", actor, true, t(at), null);
    }

    private static List<Stage> stages(String releaseApprovalStatus) {
        return List.of(
                new Stage("CLARIFICATION", List.of(), "SKIPPED"),
                new Stage("DESIGN", List.of("CLARIFICATION"), "SUCCEEDED"),
                new Stage("ARCHITECTURE_APPROVAL", List.of("DESIGN"), "SUCCEEDED"),
                new Stage("IMPLEMENTATION", List.of("ARCHITECTURE_APPROVAL"), "SUCCEEDED"),
                new Stage("COMPLIANCE_EVALUATION", List.of("IMPLEMENTATION"), "SUCCEEDED"),
                new Stage("RELEASE_APPROVAL", List.of("COMPLIANCE_EVALUATION"), releaseApprovalStatus),
                new Stage("RELEASE", List.of("RELEASE_APPROVAL"), "SUCCEEDED"),
                new Stage("FINAL_SUMMARY", List.of("RELEASE"), "SUCCEEDED"));
    }

    private static List<AuditEntry> validAudit() {
        return List.of(
                transition(1, "CLARIFICATION", "READY", "SKIPPED"),
                attempt(2, "DESIGN"),
                transition(3, "ARCHITECTURE_APPROVAL", "READY", "AWAITING_DECISION"),
                transition(4, "ARCHITECTURE_APPROVAL", "AWAITING_DECISION", "SUCCEEDED"),
                attempt(5, "IMPLEMENTATION"),
                attempt(6, "COMPLIANCE_EVALUATION"),
                transition(7, "RELEASE_APPROVAL", "READY", "AWAITING_DECISION"),
                transition(8, "RELEASE_APPROVAL", "AWAITING_DECISION", "SUCCEEDED"),
                attempt(9, "RELEASE"),
                attempt(10, "FINAL_SUMMARY"));
    }

    private static RunEvidence valid() {
        return new RunEvidence("alice", stages("SUCCEEDED"), validAudit(),
                List.of(gate("ARCHITECTURE_APPROVAL", "bob", 4), gate("RELEASE_APPROVAL", "carol", 8)),
                List.of(new Evaluation("SEC-001", "MANDATORY", "PASS", null, t(6)),
                        new Evaluation("DOC-002", "ADVISORY", "FAIL", null, t(6))));
    }

    private static RunEvidence withAudit(RunEvidence run, UnaryOperator<List<AuditEntry>> change) {
        return new RunEvidence(run.requestedBy(), run.stages(), change.apply(new ArrayList<>(run.audit())), run.decisions(),
                run.evaluations());
    }

    private static RunEvidence withDecisions(RunEvidence run, List<DecisionEntry> decisions) {
        return new RunEvidence(run.requestedBy(), run.stages(), run.audit(), decisions, run.evaluations());
    }

    private static RunEvidence withEvaluations(RunEvidence run, List<Evaluation> evaluations, List<DecisionEntry> extraDecisions) {
        List<DecisionEntry> decisions = new ArrayList<>(run.decisions());
        decisions.addAll(extraDecisions);
        return new RunEvidence(run.requestedBy(), run.stages(), run.audit(), decisions, evaluations);
    }

    private static DecisionEntry exceptionDecision(String outcome, int at, Instant expiresAt) {
        return new DecisionEntry("EXCEPTION_DECISION", outcome, "COMPLIANCE_EVALUATION", "HUMAN", "bob", true, t(at),
                CanonicalJson.parse("{\"exceptionId\":\"ex-1\",\"expiresAt\":\"" + expiresAt + "\"}"));
    }

    @Test
    void aValidRunHasNoViolations() {
        assertThat(GovernanceInvariants.violations(valid())).isEmpty();
    }

    @Test
    void aStageStartedBeforeItsUpstreamGateWasApprovedIsReported() {
        RunEvidence early = withAudit(valid(), audit -> {
            audit.add(2, attempt(3, "IMPLEMENTATION"));
            return audit;
        });
        // before the gate has any transition: it is in the plan from the start, so it still constrains
        assertThat(GovernanceInvariants.violations(early))
                .anyMatch(v -> v.startsWith("gate order: IMPLEMENTATION") && v.contains("ARCHITECTURE_APPROVAL was NONE"));

        RunEvidence awaiting = withAudit(valid(), audit -> {
            audit.add(3, attempt(3, "IMPLEMENTATION"));
            return audit;
        });
        assertThat(GovernanceInvariants.violations(awaiting))
                .anyMatch(v -> v.startsWith("gate order: IMPLEMENTATION") && v.contains("ARCHITECTURE_APPROVAL was AWAITING_DECISION"));

        RunEvidence whileWaiting = withAudit(valid(), audit -> {
            audit.add(3, attempt(35, "RELEASE"));
            audit.add(3, transition(3, "ARCHITECTURE_APPROVAL", "READY", "AWAITING_DECISION"));
            return audit;
        });
        assertThat(GovernanceInvariants.violations(whileWaiting))
                .anyMatch(v -> v.startsWith("gate order: RELEASE") && v.contains("AWAITING_DECISION"));
    }

    @Test
    void onlyTheConditionalClarificationGateMayBeSkipped() {
        RunEvidence skippedRelease = withAudit(valid(), audit -> {
            audit.set(7, transition(8, "RELEASE_APPROVAL", "AWAITING_DECISION", "SKIPPED"));
            return audit;
        });
        assertThat(GovernanceInvariants.violations(skippedRelease))
                .anyMatch(v -> v.startsWith("gate order: RELEASE") && v.contains("RELEASE_APPROVAL was SKIPPED"));
    }

    @Test
    void aGateThatSucceededWithoutAHumanDecisionIsReported() {
        assertThat(GovernanceInvariants.violations(withDecisions(valid(), List.of(gate("RELEASE_APPROVAL", "carol", 8)))))
                .anyMatch(v -> v.contains("gate ARCHITECTURE_APPROVAL SUCCEEDED at seq 4 without"));

        DecisionEntry agentApproval = new DecisionEntry("GATE", "APPROVED", "ARCHITECTURE_APPROVAL", "AGENT", "design-agent", true, t(4),
                null);
        assertThat(GovernanceInvariants.violations(withDecisions(valid(), List.of(agentApproval, gate("RELEASE_APPROVAL", "carol", 8)))))
                .anyMatch(v -> v.contains("ARCHITECTURE_APPROVAL SUCCEEDED"));

        DecisionEntry rejection = new DecisionEntry("GATE", "REJECTED", "ARCHITECTURE_APPROVAL", "HUMAN", "bob", true, t(4), null);
        assertThat(GovernanceInvariants.violations(withDecisions(valid(), List.of(rejection, gate("RELEASE_APPROVAL", "carol", 8)))))
                .anyMatch(v -> v.contains("ARCHITECTURE_APPROVAL SUCCEEDED"));
    }

    @Test
    void anApprovalByTheRequesterOrAfterTheTransitionDoesNotCount() {
        assertThat(GovernanceInvariants.violations(withDecisions(valid(),
                List.of(gate("ARCHITECTURE_APPROVAL", "alice", 4), gate("RELEASE_APPROVAL", "carol", 8)))))
                .anyMatch(v -> v.contains("ARCHITECTURE_APPROVAL SUCCEEDED at seq 4"));
        assertThat(GovernanceInvariants.violations(withDecisions(valid(),
                List.of(gate("ARCHITECTURE_APPROVAL", "bob", 5), gate("RELEASE_APPROVAL", "carol", 8)))))
                .anyMatch(v -> v.contains("ARCHITECTURE_APPROVAL SUCCEEDED at seq 4"));
    }

    @Test
    void aGateEndingSucceededNeedsAStillValidApproval() {
        DecisionEntry invalidated = new DecisionEntry("GATE", "APPROVED", "RELEASE_APPROVAL", "HUMAN", "carol", false, t(8), null);
        assertThat(GovernanceInvariants.violations(withDecisions(valid(), List.of(gate("ARCHITECTURE_APPROVAL", "bob", 4), invalidated))))
                .containsExactly("human decision: gate RELEASE_APPROVAL ended SUCCEEDED without a valid approving human decision");
    }

    @Test
    void aReleaseAfterAMandatoryFailureIsReportedUnlessAnApprovedUnexpiredExceptionCoversIt() {
        List<Evaluation> failed = List.of(new Evaluation("SEC-001", "MANDATORY", "FAIL", null, t(6)));
        assertThat(GovernanceInvariants.violations(withEvaluations(valid(), failed, List.of())))
                .containsExactly("release after mandatory failure: RELEASE started at seq 9 with SEC-001 FAIL");

        List<Evaluation> covered = List.of(new Evaluation("SEC-001", "MANDATORY", "FAIL", null, t(5)),
                new Evaluation("SEC-001", "MANDATORY", "EXCEPTION_REQUESTED", "ex-1", t(6)));
        assertThat(GovernanceInvariants.violations(withEvaluations(valid(), covered, List.of(exceptionDecision("APPROVED", 6, t(3600))))))
                .isEmpty();
        assertThat(GovernanceInvariants.violations(withEvaluations(valid(), covered, List.of(exceptionDecision("REJECTED", 6, t(3600))))))
                .anyMatch(v -> v.contains("SEC-001 EXCEPTION_REQUESTED (exception ex-1 not approved or expired)"));
        assertThat(GovernanceInvariants.violations(withEvaluations(valid(), covered, List.of(exceptionDecision("APPROVED", 6, t(9))))))
                .anyMatch(v -> v.contains("not approved or expired"));
        assertThat(GovernanceInvariants.violations(withEvaluations(valid(), covered, List.of())))
                .anyMatch(v -> v.contains("not approved or expired"));
    }

    @Test
    void aGateInsertedByReplanningAndTheSafeStopSummaryAreNotViolations() {
        List<Stage> withChangeGate = new ArrayList<>(stages("SUCCEEDED"));
        withChangeGate.set(3, new Stage("IMPLEMENTATION", List.of("ARCHITECTURE_APPROVAL", "CHANGE_APPROVAL"), "SUCCEEDED"));
        withChangeGate.add(new Stage("CHANGE_APPROVAL", List.of("DESIGN"), "REMOVED"));
        List<AuditEntry> audit = new ArrayList<>(validAudit());
        audit.add(new AuditEntry(11, t(11), "STAGE_TRANSITION", "CHANGE_APPROVAL", "AWAITING_DECISION", "REMOVED"));
        RunEvidence inserted = new RunEvidence("alice", withChangeGate, audit, valid().decisions(), valid().evaluations());
        assertThat(GovernanceInvariants.violations(inserted)).isEmpty();

        List<AuditEntry> stopped = new ArrayList<>(validAudit().subList(0, 7));
        stopped.add(new AuditEntry(8, t(8), "RUN_TRANSITION", "RUN", "AWAITING_HUMAN", "COMPENSATING"));
        stopped.add(attempt(9, "FINAL_SUMMARY"));
        RunEvidence safeStopped = new RunEvidence("alice", stages("CANCELLED"), stopped, List.of(gate("ARCHITECTURE_APPROVAL", "bob", 4)),
                valid().evaluations());
        assertThat(GovernanceInvariants.violations(safeStopped)).isEmpty();

        List<AuditEntry> notStopping = new ArrayList<>(validAudit().subList(0, 7));
        notStopping.add(attempt(9, "FINAL_SUMMARY"));
        assertThat(GovernanceInvariants.violations(new RunEvidence("alice", stages("AWAITING_DECISION"), notStopping,
                List.of(gate("ARCHITECTURE_APPROVAL", "bob", 4)), valid().evaluations())))
                .anyMatch(v -> v.startsWith("gate order: FINAL_SUMMARY"));
    }
}
