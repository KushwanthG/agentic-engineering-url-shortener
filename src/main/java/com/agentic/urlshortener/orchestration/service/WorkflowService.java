package com.agentic.urlshortener.orchestration.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.agentic.urlshortener.common.exception.ApiException;
import com.agentic.urlshortener.common.exception.ErrorCode;
import com.agentic.urlshortener.common.security.ApiPrincipal;
import com.agentic.urlshortener.common.util.CanonicalJson;
import com.agentic.urlshortener.common.util.Fingerprints;
import com.agentic.urlshortener.orchestration.config.OrchestrationProperties;
import com.agentic.urlshortener.orchestration.domain.ActorType;
import com.agentic.urlshortener.orchestration.domain.Classification;
import com.agentic.urlshortener.orchestration.domain.PlanVersion;
import com.agentic.urlshortener.orchestration.domain.RequirementVersion;
import com.agentic.urlshortener.orchestration.domain.RunStatus;
import com.agentic.urlshortener.orchestration.domain.StageNode;
import com.agentic.urlshortener.orchestration.domain.WorkflowRun;
import com.agentic.urlshortener.orchestration.dto.RequirementSubmission;
import com.agentic.urlshortener.orchestration.engine.PlanValidator;
import com.agentic.urlshortener.orchestration.engine.RunAudit;
import com.agentic.urlshortener.orchestration.engine.RunCoordinator;
import com.agentic.urlshortener.orchestration.planning.PlanFactory;
import com.agentic.urlshortener.orchestration.planning.PlanGraph;
import com.agentic.urlshortener.orchestration.planning.StageSpec;
import com.agentic.urlshortener.orchestration.policy.PolicySet;
import com.agentic.urlshortener.orchestration.policy.PolicySetLoader;
import com.agentic.urlshortener.orchestration.reliability.FaultInjector;
import com.agentic.urlshortener.orchestration.repository.PlanVersionRepository;
import com.agentic.urlshortener.orchestration.repository.RequirementVersionRepository;
import com.agentic.urlshortener.orchestration.repository.StageNodeRepository;
import com.agentic.urlshortener.orchestration.repository.WorkflowRunRepository;

/**
 * Starts runs (FR-ORC-01): requirement version 1, a validated plan version 1 for the declared type,
 * the pinned policy-set version, and the stage nodes are persisted and audited in one transaction;
 * scheduling starts after the commit.
 */
@Service
public class WorkflowService {

    private static final Pattern AC_PREFIX = Pattern.compile("^\\s*(AC-\\d+)\\s*:\\s*(.*)$", Pattern.DOTALL);

    private final WorkflowRunRepository runs;
    private final RequirementVersionRepository requirements;
    private final PlanVersionRepository plans;
    private final StageNodeRepository nodes;
    private final PlanFactory planFactory;
    private final PlanValidator planValidator;
    private final PolicySetLoader policySets;
    private final RunCoordinator coordinator;
    private final RunAudit audit;
    private final OrchestrationProperties properties;
    private final TransactionTemplate tx;
    private final Clock clock;

    public WorkflowService(WorkflowRunRepository runs, RequirementVersionRepository requirements, PlanVersionRepository plans,
            StageNodeRepository nodes, PlanFactory planFactory, PlanValidator planValidator, PolicySetLoader policySets,
            RunCoordinator coordinator, RunAudit audit, OrchestrationProperties properties,
            PlatformTransactionManager transactionManager, Clock clock) {
        this.runs = runs;
        this.requirements = requirements;
        this.plans = plans;
        this.nodes = nodes;
        this.planFactory = planFactory;
        this.planValidator = planValidator;
        this.policySets = policySets;
        this.coordinator = coordinator;
        this.audit = audit;
        this.properties = properties;
        this.tx = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    public UUID submit(RequirementSubmission submission, ApiPrincipal requester) {
        if (submission.simulation() != null && !properties.faultInjection().enabled()) {
            throw new ApiException(ErrorCode.FAULT_INJECTION_DISABLED,
                    "Simulation options are accepted only when fault injection is enabled (demo and test profiles).");
        }
        if (submission.simulation() != null) {
            FaultInjector.validate(submission.simulation(), policySets.current().policies().stream()
                    .map(PolicySet.PolicyDefinition::id).collect(Collectors.toSet()));
        }
        UUID runId = UUID.randomUUID();
        tx.executeWithoutResult(status -> create(runId, submission, requester));
        coordinator.advance(runId);
        return runId;
    }

    private void create(UUID runId, RequirementSubmission submission, ApiPrincipal requester) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        Classification classification = classification(submission.type());
        String policySetVersion = policySets.current().version();
        WorkflowRun run = WorkflowRun.create(runId, submission.ref(), submission.title(), requester.id(), classification,
                policySetVersion, now);
        if (submission.simulation() != null) {
            run.simulation(CanonicalJson.write(submission.simulation().faults()), submission.simulation().gateDeadlineSeconds() == null
                    ? null : submission.simulation().gateDeadlineSeconds().longValue());
        }
        runs.save(run);

        String content = CanonicalJson.write(requirementDocument(submission));
        requirements.save(RequirementVersion.create(runId, 1, "SUBMITTED", content, Fingerprints.sha256(content), requester.id(), now,
                null));

        PlanGraph plan = planFactory.create(classification);
        planValidator.validate(plan);
        plans.save(PlanVersion.create(runId, 1, plan.toJson(), null, "INITIAL", "initial plan for classification " + classification,
                RunAudit.SYSTEM, now));
        for (StageSpec stage : plan.stages()) {
            nodes.save(StageNode.create(runId, stage.key(), stage.dependsOn()));
        }

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("requirementRef", String.valueOf(submission.ref()));
        details.put("classification", classification.name());
        details.put("policySetVersion", policySetVersion);
        details.put("simulated", submission.simulation() != null);
        audit.record(runId, ActorType.HUMAN, requester.id(), "RUN_CREATED", "RUN", null, RunStatus.CREATED.name(), "OK",
                submission.title(), details);
        audit.system(runId, "PLAN_VERSION_CREATED", "PLAN", "OK", "initial plan", Map.of("version", 1, "trigger", "INITIAL",
                "stages", plan.keys().stream().map(Enum::name).toList()));
        run.transitionTo(RunStatus.RUNNING, now);
        audit.runTransition(runId, RunStatus.CREATED, RunStatus.RUNNING, "plan validated; policy set " + policySetVersion + " pinned");
    }

    static Classification classification(String type) {
        if ("NEW_CAPABILITY".equals(type)) {
            return Classification.NEW_CAPABILITY;
        }
        if ("CHANGE_TO_EXISTING".equals(type)) {
            return Classification.CHANGE_TO_EXISTING;
        }
        return Classification.UNDETERMINED;
    }

    /** The submitted requirement as a {@code requirement-document} (acceptance criteria numbered AC-1..n). */
    static Map<String, Object> requirementDocument(RequirementSubmission submission) {
        List<Map<String, Object>> criteria = new ArrayList<>();
        int index = 1;
        for (String text : submission.acceptanceCriteria()) {
            Matcher matcher = AC_PREFIX.matcher(text);
            String id = matcher.matches() ? matcher.group(1) : "AC-" + index;
            criteria.add(Map.of("id", id, "text", matcher.matches() ? matcher.group(2).strip() : text.strip(), "origin", "SUBMITTED"));
            index++;
        }
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("ref", submission.ref());
        document.put("title", submission.title());
        document.put("type", submission.type() == null ? "UNSPECIFIED" : submission.type());
        document.put("narrative", submission.narrative());
        document.put("acceptanceCriteria", criteria);
        document.put("constraints", submission.constraints());
        document.put("clarifications", List.of());
        document.put("scopeExclusions", List.of());
        document.put("parameters", Map.of());
        return document;
    }
}
