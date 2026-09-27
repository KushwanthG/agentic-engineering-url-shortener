package com.agentic.urlshortener.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.agentic.urlshortener.common.util.CanonicalJson;
import com.agentic.urlshortener.orchestration.domain.StageType;

import tools.jackson.databind.JsonNode;

/**
 * Independent checker of the governance invariants (T132, SC-004). It uses only a run's recorded
 * evidence as the API returns it: stages, audit trail, decisions, and policy evaluations.
 * <ol>
 * <li><b>Gate order</b>: no attempt of a stage downstream of a gate starts while that gate is not
 * passed. The latest transition of the gate before the attempt must be SUCCEEDED. SKIPPED is allowed
 * only for the conditional gates (CLARIFICATION, ARCHITECTURE_APPROVAL), and only directly from
 * PENDING (condition false); a gate skipped after it awaited a decision is a bypass. REMOVED is
 * allowed only for a rejected CHANGE_APPROVAL gate.
 * A gate constrains every attempt from the start of the run, except CHANGE_APPROVAL, which only
 * re-planning inserts: it constrains attempts after it first appears in the trail. The
 * FINAL_SUMMARY of a run being safe-stopped is part of the safe-stop procedure and is exempt.</li>
 * <li><b>Human decision</b>: every transition of a gate to SUCCEEDED is preceded by an approving
 * HUMAN decision for that gate. For the architecture, change, and release gates, the decider must
 * not be the run's requester. A gate that ends SUCCEEDED has at least one such decision still
 * valid.</li>
 * <li><b>Release after a mandatory failure</b>: when RELEASE starts, the latest evaluation of every
 * mandatory policy is PASS or NOT_APPLICABLE, or EXCEPTION_REQUESTED with an exception that a HUMAN
 * approved before the release and that has not expired.</li>
 * </ol>
 * The checker is proven against deliberately violating sequences in {@code GovernanceInvariantsTest}
 * and runs at the end of every scenario and drill end-to-end test.
 */
public final class GovernanceInvariants {

    private static final Set<String> SEPARATED_GATES = Set.of("ARCHITECTURE_APPROVAL", "CHANGE_APPROVAL", "RELEASE_APPROVAL");
    /** Gates that only re-planning inserts; every other gate is in the plan from the start. */
    private static final Set<String> INSERTED_GATES = Set.of("CHANGE_APPROVAL");
    /** Conditional gates (PlanFactory): skipped by the engine, PENDING to SKIPPED, when their condition is false. */
    private static final Set<String> CONDITIONAL_GATES = Set.of("CLARIFICATION", "ARCHITECTURE_APPROVAL");

    private GovernanceInvariants() {
    }

    public record Stage(String key, List<String> dependsOn, String status) {
    }

    public record AuditEntry(long seq, Instant occurredAt, String action, String target, String fromState, String toState) {
    }

    public record DecisionEntry(String type, String outcome, String stageKey, String actorType, String actorId, boolean valid,
            Instant createdAt, JsonNode payload) {
    }

    public record Evaluation(String policyId, String severity, String outcome, String exceptionId, Instant evaluatedAt) {
    }

    public record RunEvidence(String requestedBy, List<Stage> stages, List<AuditEntry> audit, List<DecisionEntry> decisions,
            List<Evaluation> evaluations) {
    }

    /** Reads a run's evidence over the API (auditor role). */
    public static RunEvidence fromHttp(HttpDriver http, String runPath) {
        JsonNode run = http.run(runPath);
        List<Stage> stages = new ArrayList<>();
        run.path("stages").forEach(s -> {
            List<String> deps = new ArrayList<>();
            s.path("dependsOn").forEach(d -> deps.add(d.asString()));
            stages.add(new Stage(s.path("key").asString(), deps, s.path("status").asString()));
        });
        List<AuditEntry> audit = new ArrayList<>();
        http.get(runPath + "/audit", Tokens.AUDITOR).forEach(e -> audit.add(new AuditEntry(e.path("seq").asLong(),
                Instant.parse(e.path("occurredAt").asString()), e.path("action").asString(), e.path("target").asString(null),
                e.path("fromState").asString(null), e.path("toState").asString(null))));
        List<DecisionEntry> decisions = new ArrayList<>();
        http.get(runPath + "/decisions", Tokens.AUDITOR).forEach(d -> decisions.add(new DecisionEntry(d.path("type").asString(),
                d.path("outcome").asString(), d.path("stageKey").asString(null), d.path("actorType").asString(),
                d.path("actorId").asString(), d.path("valid").asBoolean(), Instant.parse(d.path("createdAt").asString()),
                d.path("payload").isString() ? CanonicalJson.parse(d.path("payload").asString()) : null)));
        List<Evaluation> evaluations = new ArrayList<>();
        http.get(runPath + "/policy-evaluations", Tokens.AUDITOR).forEach(p -> evaluations.add(new Evaluation(p.path("policyId").asString(),
                p.path("severity").asString(), p.path("outcome").asString(), p.path("exceptionId").asString(null),
                Instant.parse(p.path("evaluatedAt").asString()))));
        return new RunEvidence(run.path("requestedBy").asString(), stages, audit, decisions, evaluations);
    }

    /** Asserts that the run's evidence satisfies every invariant; the failure lists each violation. */
    public static void assertHold(HttpDriver http, String runPath) {
        List<String> violations = violations(fromHttp(http, runPath));
        assertThat(violations).as("governance invariants of " + runPath).isEmpty();
    }

    public static List<String> violations(RunEvidence run) {
        List<String> violations = new ArrayList<>();
        gateOrder(run, violations);
        humanDecisions(run, violations);
        releaseAfterMandatoryFailure(run, violations);
        return violations;
    }

    private static boolean isGate(String stage) {
        try {
            return StageType.valueOf(stage).isGate();
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static void gateOrder(RunEvidence run, List<String> violations) {
        Map<String, List<String>> dependsOn = new HashMap<>();
        run.stages().forEach(s -> dependsOn.put(s.key(), s.dependsOn()));
        Map<String, AuditEntry> lastGateTransition = new HashMap<>();
        Set<String> gatesSeen = new HashSet<>();
        boolean stopping = false;
        for (AuditEntry e : run.audit()) {
            if (e.target() != null && isGate(e.target())) {
                gatesSeen.add(e.target());
            }
            if ("RUN_TRANSITION".equals(e.action()) && ("COMPENSATING".equals(e.toState()) || "SAFE_STOPPED".equals(e.toState()))) {
                stopping = true;
            }
            if ("STAGE_TRANSITION".equals(e.action()) && isGate(e.target())) {
                lastGateTransition.put(e.target(), e);
            }
            if (!"ATTEMPT_STARTED".equals(e.action()) || e.target() == null) {
                continue;
            }
            if (stopping && "FINAL_SUMMARY".equals(e.target())) {
                continue;
            }
            for (String gate : upstreamGates(e.target(), dependsOn)) {
                if (INSERTED_GATES.contains(gate) && !gatesSeen.contains(gate)) {
                    continue;
                }
                AuditEntry last = lastGateTransition.get(gate);
                String state = last == null ? "NONE" : last.toState();
                boolean passed = "SUCCEEDED".equals(state)
                        || ("SKIPPED".equals(state) && CONDITIONAL_GATES.contains(gate) && "PENDING".equals(last.fromState()))
                        || ("REMOVED".equals(state) && "CHANGE_APPROVAL".equals(gate));
                if (!passed) {
                    violations.add("gate order: " + e.target() + " started at seq " + e.seq() + " while gate " + gate + " was " + state);
                }
            }
        }
    }

    private static Set<String> upstreamGates(String stage, Map<String, List<String>> dependsOn) {
        Set<String> gates = new HashSet<>();
        Set<String> seen = new HashSet<>();
        Deque<String> todo = new ArrayDeque<>(dependsOn.getOrDefault(stage, List.of()));
        while (!todo.isEmpty()) {
            String upstream = todo.poll();
            if (seen.add(upstream)) {
                if (isGate(upstream)) {
                    gates.add(upstream);
                }
                todo.addAll(dependsOn.getOrDefault(upstream, List.of()));
            }
        }
        return gates;
    }

    private static boolean approves(DecisionEntry d, String gate, String requestedBy) {
        if (!"HUMAN".equals(d.actorType()) || !gate.equals(d.stageKey())) {
            return false;
        }
        if (SEPARATED_GATES.contains(gate) && d.actorId().equals(requestedBy)) {
            return false;
        }
        return switch (gate) {
            case "CLARIFICATION" -> "CLARIFICATION_ANSWER".equals(d.type());
            case "CHANGE_APPROVAL" -> "CHANGE_DECISION".equals(d.type()) && "APPROVED".equals(d.outcome());
            default -> "GATE".equals(d.type()) && "APPROVED".equals(d.outcome());
        };
    }

    private static void humanDecisions(RunEvidence run, List<String> violations) {
        for (AuditEntry e : run.audit()) {
            if ("STAGE_TRANSITION".equals(e.action()) && isGate(e.target()) && "SUCCEEDED".equals(e.toState())) {
                boolean decided = run.decisions().stream()
                        .anyMatch(d -> approves(d, e.target(), run.requestedBy()) && !d.createdAt().isAfter(e.occurredAt()));
                if (!decided) {
                    violations.add("human decision: gate " + e.target() + " SUCCEEDED at seq " + e.seq()
                            + " without a prior approving human decision by someone other than the requester");
                }
            }
        }
        for (Stage stage : run.stages()) {
            if (isGate(stage.key()) && "SUCCEEDED".equals(stage.status())
                    && run.decisions().stream().noneMatch(d -> d.valid() && approves(d, stage.key(), run.requestedBy()))) {
                violations.add("human decision: gate " + stage.key() + " ended SUCCEEDED without a valid approving human decision");
            }
        }
    }

    private static void releaseAfterMandatoryFailure(RunEvidence run, List<String> violations) {
        for (AuditEntry e : run.audit()) {
            if (!"ATTEMPT_STARTED".equals(e.action()) || !"RELEASE".equals(e.target())) {
                continue;
            }
            Map<String, Evaluation> latest = new LinkedHashMap<>();
            run.evaluations().stream().filter(p -> !p.evaluatedAt().isAfter(e.occurredAt()))
                    .forEach(p -> latest.merge(p.policyId(), p, (a, b) -> b.evaluatedAt().isBefore(a.evaluatedAt()) ? a : b));
            for (Evaluation p : latest.values()) {
                if (!"MANDATORY".equals(p.severity()) || "PASS".equals(p.outcome()) || "NOT_APPLICABLE".equals(p.outcome())) {
                    continue;
                }
                if ("EXCEPTION_REQUESTED".equals(p.outcome()) && coveredByApprovedException(run, p.exceptionId(), e.occurredAt())) {
                    continue;
                }
                violations.add("release after mandatory failure: RELEASE started at seq " + e.seq() + " with " + p.policyId() + " "
                        + p.outcome() + (p.exceptionId() == null ? "" : " (exception " + p.exceptionId() + " not approved or expired)"));
            }
        }
    }

    private static boolean coveredByApprovedException(RunEvidence run, String exceptionId, Instant releaseAt) {
        if (exceptionId == null) {
            return false;
        }
        return run.decisions().stream().anyMatch(d -> "EXCEPTION_DECISION".equals(d.type()) && "HUMAN".equals(d.actorType())
                && "APPROVED".equals(d.outcome()) && !d.createdAt().isAfter(releaseAt) && d.payload() != null
                && exceptionId.equals(d.payload().path("exceptionId").asString(null))
                && Instant.parse(d.payload().path("expiresAt").asString()).isAfter(releaseAt));
    }
}
