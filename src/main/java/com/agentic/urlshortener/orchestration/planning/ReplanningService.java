package com.agentic.urlshortener.orchestration.planning;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.agentic.urlshortener.common.exception.ApiException;
import com.agentic.urlshortener.common.exception.ErrorCode;
import com.agentic.urlshortener.orchestration.domain.Classification;
import com.agentic.urlshortener.orchestration.domain.PlanVersion;
import com.agentic.urlshortener.orchestration.domain.StageNode;
import com.agentic.urlshortener.orchestration.domain.StageStatus;
import com.agentic.urlshortener.orchestration.domain.StageType;
import com.agentic.urlshortener.orchestration.domain.WorkflowRun;
import com.agentic.urlshortener.orchestration.engine.ArtifactStore;
import com.agentic.urlshortener.orchestration.engine.PlanValidator;
import com.agentic.urlshortener.orchestration.engine.RunAudit;
import com.agentic.urlshortener.orchestration.reliability.FailureEventRecorder;
import com.agentic.urlshortener.orchestration.repository.DecisionRepository;
import com.agentic.urlshortener.orchestration.repository.PlanVersionRepository;
import com.agentic.urlshortener.orchestration.repository.StageNodeRepository;
import com.agentic.urlshortener.orchestration.repository.WorkflowRunRepository;

/**
 * Content-addressed re-planning (FR-RPL-01, FR-RPL-02, FR-RPL-05, ADR-011). A new plan version is
 * derived from the run's classification and diffed against the current plan. Stages are added or
 * rewired, and the stage where changed input enters, together with everything downstream of it, is
 * re-opened as a new generation: results of earlier attempts become stale and the stages' artifacts
 * are superseded. When a re-opened agent stage's inputs turn out unchanged, the coordinator reuses its
 * earlier result instead of executing it again. A re-opened gate carries its approval over only if
 * the artifacts the approval is bound to are unchanged. Called inside the caller's transaction under
 * the run lock.
 */
@Service
public class ReplanningService {

    private final WorkflowRunRepository runs;
    private final StageNodeRepository nodes;
    private final PlanVersionRepository plans;
    private final PlanFactory factory;
    private final PlanValidator validator;
    private final ArtifactStore artifacts;
    private final DecisionRepository decisions;
    private final FailureEventRecorder failureEvents;
    private final RunAudit audit;

    public ReplanningService(WorkflowRunRepository runs, StageNodeRepository nodes, PlanVersionRepository plans, PlanFactory factory,
            PlanValidator validator, ArtifactStore artifacts, DecisionRepository decisions, FailureEventRecorder failureEvents,
            RunAudit audit) {
        this.runs = runs;
        this.nodes = nodes;
        this.plans = plans;
        this.factory = factory;
        this.validator = validator;
        this.artifacts = artifacts;
        this.decisions = decisions;
        this.failureEvents = failureEvents;
        this.audit = audit;
    }

    /**
     * Creates the next plan version for {@code classification} and re-opens {@code reopenFrom} with its
     * downstream closure; returns the new plan version number.
     */
    public int replan(UUID runId, Classification classification, StageType reopenFrom, String trigger, String reason, String createdBy,
            Instant now) {
        WorkflowRun run = runs.findById(runId).orElseThrow();
        if (run.getStatus().isTerminal()) {
            throw new ApiException(ErrorCode.RUN_TERMINAL, "The run is " + run.getStatus() + "; it cannot be re-planned.");
        }
        List<StageNode> current = nodes.findByRunIdOrderByStageKeyAsc(runId);
        PlanGraph before = graphOf(current);
        PlanGraph after = factory.create(classification);
        validator.validate(after);
        PlanDiff diff = PlanDiff.between(before, after);

        Map<StageType, StageNode> byKey = new EnumMap<>(StageType.class);
        current.forEach(n -> byKey.put(n.getStageKey(), n));
        List<StageNode> plan = new ArrayList<>(current);
        for (StageSpec spec : after.stages()) {
            StageNode node = byKey.get(spec.key());
            if (node == null) {
                StageNode added = nodes.save(StageNode.create(runId, spec.key(), spec.dependsOn()));
                plan.add(added);
                audit.system(runId, "STAGE_ADDED", spec.key().name(), "OK", reason, Map.of("dependsOn",
                        spec.dependsOn().stream().map(Enum::name).toList()));
            } else if (!node.getDependsOn().equals(spec.dependsOn())) {
                node.redependOn(spec.dependsOn());
            }
        }
        List<String> invalidated = reopenFrom == null ? List.of()
                : reopen(runId, byKey.get(reopenFrom), plan, reason, false);

        int version = plans.findByRunIdOrderByVersionAsc(runId).stream().mapToInt(PlanVersion::getVersion).max().orElse(0) + 1;
        plans.save(PlanVersion.create(runId, version, after.toJson(), diff.withInvalidated(invalidated).toJson(), trigger, reason,
                createdBy, now));
        run.planVersion(version);
        run.classify(classification);
        audit.system(runId, "PLAN_VERSION_CREATED", "PLAN", "OK", reason, Map.of("version", version, "trigger", trigger,
                "added", diff.added(), "invalidated", invalidated));
        return version;
    }

    /**
     * Re-opens {@code origin} and every stage that transitively depends on it as a new generation;
     * their current artifacts are superseded. With {@code invalidateDownstreamApprovals}, approvals of
     * re-opened downstream gates are invalidated too (used when an upstream approval was invalidated).
     * Returns the names of the re-opened stages.
     */
    public List<String> reopen(UUID runId, StageNode origin, List<StageNode> plan, String reason, boolean invalidateDownstreamApprovals) {
        Set<StageType> affected = EnumSet.of(origin.getStageKey());
        boolean grew;
        do {
            grew = false;
            for (StageNode node : plan) {
                if (!affected.contains(node.getStageKey()) && node.getDependsOn().stream().anyMatch(affected::contains)) {
                    affected.add(node.getStageKey());
                    grew = true;
                }
            }
        } while (grew);
        List<String> reopened = new ArrayList<>();
        for (StageNode node : plan) {
            if (!affected.contains(node.getStageKey()) || node.getStatus() == StageStatus.REMOVED) {
                continue;
            }
            reopened.add(node.getStageKey().name());
            if (node.getStatus() == StageStatus.PENDING) {
                continue;
            }
            StageStatus from = node.getStatus();
            if (invalidateDownstreamApprovals && node != origin && node.getStageType().isGate()) {
                decisions.findByRunIdAndStageKeyAndValidTrueOrderByCreatedAtDesc(runId, node.getStageKey())
                        .forEach(d -> d.invalidate("upstream approval " + origin.getStageKey() + " was invalidated"));
            }
            failureEvents.generationInvalidated(runId, node.getStageKey(), node.getGeneration(), reason);
            artifacts.supersedeStage(runId, node.getStageKey());
            node.reopen();
            audit.transition(runId, node.getStageKey().name(), from, StageStatus.PENDING,
                    node == origin ? reason : "re-opened: upstream " + origin.getStageKey() + " re-opened");
        }
        return reopened;
    }

    /**
     * Change control (FR-RPL-04): inserts the CHANGE_APPROVAL gate as a dependency of every stage that
     * has not started, so nothing new starts until an approver decides; running stages continue.
     * Returns the new plan version number.
     */
    public int insertChangeGate(UUID runId, String reason, String createdBy, Instant now) {
        List<StageNode> current = nodes.findByRunIdOrderByStageKeyAsc(runId);
        PlanGraph before = graphOf(current);
        StageNode gate = current.stream().filter(n -> n.getStageKey() == StageType.CHANGE_APPROVAL).findFirst().orElse(null);
        if (gate != null && gate.getStatus() == StageStatus.REMOVED) {
            throw new ApiException(ErrorCode.ILLEGAL_STATE,
                    "A rejected change request removed this run's change gate; a further material change needs a new run.");
        }
        if (gate == null) {
            gate = nodes.save(StageNode.create(runId, StageType.CHANGE_APPROVAL, List.of()));
            current = new ArrayList<>(current);
            current.add(gate);
        } else if (gate.getStatus() == StageStatus.SUCCEEDED) {
            gate.reopen();
            gate.redependOn(List.of());
        }
        for (StageNode node : current) {
            boolean notStarted = node.getStatus() == StageStatus.PENDING || node.getStatus() == StageStatus.READY;
            if (node != gate && notStarted && !node.getDependsOn().contains(StageType.CHANGE_APPROVAL)) {
                List<StageType> dependencies = new ArrayList<>(node.getDependsOn());
                dependencies.add(StageType.CHANGE_APPROVAL);
                node.redependOn(dependencies);
            }
        }
        return recordPlan(runId, before, graphOf(current), List.of(), "CHANGE_REQUEST", reason, createdBy, now);
    }

    /** A rejected change: the CHANGE_APPROVAL gate and its dependencies are removed; the run continues. */
    public int removeChangeGate(UUID runId, String reason, String createdBy, Instant now) {
        List<StageNode> current = nodes.findByRunIdOrderByStageKeyAsc(runId);
        PlanGraph before = graphOf(current);
        for (StageNode node : current) {
            if (node.getStageKey() == StageType.CHANGE_APPROVAL) {
                StageStatus from = node.getStatus();
                if (from == StageStatus.AWAITING_DECISION || from == StageStatus.READY) {
                    node.transitionTo(StageStatus.PENDING);
                }
                node.transitionTo(StageStatus.REMOVED);
                audit.transition(runId, node.getStageKey().name(), from, StageStatus.REMOVED, reason);
            } else if (node.getDependsOn().contains(StageType.CHANGE_APPROVAL)) {
                node.redependOn(node.getDependsOn().stream().filter(d -> d != StageType.CHANGE_APPROVAL).toList());
            }
        }
        return recordPlan(runId, before, graphOf(current), List.of(), "CHANGE_DECISION", reason, createdBy, now);
    }

    /** Stages {@code origin} re-opens and the gates among them that hold a valid approval. */
    public record Closure(List<String> stages, List<String> approvedGates) {
    }

    public Closure closureOf(UUID runId, StageType origin) {
        List<StageNode> current = nodes.findByRunIdOrderByStageKeyAsc(runId);
        Set<StageType> affected = EnumSet.of(origin);
        boolean grew;
        do {
            grew = false;
            for (StageNode node : current) {
                if (!affected.contains(node.getStageKey()) && node.getStatus() != StageStatus.REMOVED
                        && node.getDependsOn().stream().anyMatch(affected::contains)) {
                    affected.add(node.getStageKey());
                    grew = true;
                }
            }
        } while (grew);
        List<String> approved = affected.stream().filter(StageType::isGate)
                .filter(g -> decisions.findByRunIdAndStageKeyAndValidTrueOrderByCreatedAtDesc(runId, g).stream()
                        .anyMatch(d -> "APPROVED".equals(d.getOutcome())))
                .map(Enum::name).toList();
        return new Closure(affected.stream().map(Enum::name).toList(), approved);
    }

    private int recordPlan(UUID runId, PlanGraph before, PlanGraph after, List<String> invalidated, String trigger, String reason,
            String createdBy, Instant now) {
        WorkflowRun run = runs.findById(runId).orElseThrow();
        PlanDiff diff = PlanDiff.between(before, after).withInvalidated(invalidated);
        int version = plans.findByRunIdOrderByVersionAsc(runId).stream().mapToInt(PlanVersion::getVersion).max().orElse(0) + 1;
        plans.save(PlanVersion.create(runId, version, after.toJson(), diff.toJson(), trigger, reason, createdBy, now));
        run.planVersion(version);
        audit.system(runId, "PLAN_VERSION_CREATED", "PLAN", "OK", reason, Map.of("version", version, "trigger", trigger,
                "added", diff.added(), "removed", diff.removed()));
        return version;
    }

    private static PlanGraph graphOf(List<StageNode> current) {
        return new PlanGraph(current.stream().filter(n -> n.getStatus() != StageStatus.REMOVED)
                .map(n -> new StageSpec(n.getStageKey(), n.getDependsOn(), n.getStageType().isGate(), null)).toList());
    }
}
