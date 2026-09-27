package com.agentic.urlshortener.orchestration.governance;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.agentic.urlshortener.common.exception.ApiException;
import com.agentic.urlshortener.common.exception.ErrorCode;
import com.agentic.urlshortener.common.security.ApiPrincipal;
import com.agentic.urlshortener.common.security.Role;
import com.agentic.urlshortener.common.util.CanonicalJson;
import com.agentic.urlshortener.common.util.Fingerprints;
import com.agentic.urlshortener.orchestration.agent.ArtifactDraft;
import com.agentic.urlshortener.orchestration.domain.ActorType;
import com.agentic.urlshortener.orchestration.domain.Artifact;
import com.agentic.urlshortener.orchestration.domain.AwaitingType;
import com.agentic.urlshortener.orchestration.domain.Classification;
import com.agentic.urlshortener.orchestration.domain.Decision;
import com.agentic.urlshortener.orchestration.domain.DecisionType;
import com.agentic.urlshortener.orchestration.domain.RequirementVersion;
import com.agentic.urlshortener.orchestration.domain.StageNode;
import com.agentic.urlshortener.orchestration.domain.StageStatus;
import com.agentic.urlshortener.orchestration.domain.StageType;
import com.agentic.urlshortener.orchestration.domain.WorkflowRun;
import com.agentic.urlshortener.orchestration.dto.ClarificationAnswers;
import com.agentic.urlshortener.orchestration.engine.ArtifactStore;
import com.agentic.urlshortener.orchestration.engine.RunAudit;
import com.agentic.urlshortener.orchestration.engine.RunCoordinator;
import com.agentic.urlshortener.orchestration.engine.RunLocks;
import com.agentic.urlshortener.orchestration.knowledge.CapabilityCatalog;
import com.agentic.urlshortener.orchestration.knowledge.CapabilityEntry;
import com.agentic.urlshortener.orchestration.planning.ReplanningService;
import com.agentic.urlshortener.orchestration.repository.DecisionRepository;
import com.agentic.urlshortener.orchestration.repository.RequirementVersionRepository;
import com.agentic.urlshortener.orchestration.repository.StageNodeRepository;
import com.agentic.urlshortener.orchestration.repository.WorkflowRunRepository;

import tools.jackson.databind.JsonNode;

/**
 * Answers to a clarification gate (FR-GOV-08, ADR-011). An approver answers every open question with
 * a listed option or, where allowed, free text. Each answer is recorded as a
 * {@code CLARIFICATION_ANSWER} decision. The answers produce the next requirement version
 * ({@link ClarificationDerivation}) and a new plan version that re-opens requirement analysis and
 * everything after it; requirement ingestion is not repeated. Answers that resolve nothing lead to
 * another round, and the coordinator safe-stops a run that needs more rounds than allowed.
 */
@Service
public class ClarificationService {

    /** Outcome of one clarification round. */
    public record Answered(int requirementVersion, int planVersion, List<Decision> decisions) {
    }

    private final WorkflowRunRepository runs;
    private final StageNodeRepository nodes;
    private final RequirementVersionRepository requirements;
    private final DecisionRepository decisions;
    private final ArtifactStore artifacts;
    private final ReplanningService replanning;
    private final CapabilityCatalog catalog;
    private final RunCoordinator coordinator;
    private final RunLocks locks;
    private final RunAudit audit;
    private final TransactionTemplate tx;
    private final Clock clock;

    public ClarificationService(WorkflowRunRepository runs, StageNodeRepository nodes, RequirementVersionRepository requirements,
            DecisionRepository decisions, ArtifactStore artifacts, ReplanningService replanning, CapabilityCatalog catalog,
            RunCoordinator coordinator, RunLocks locks, RunAudit audit, PlatformTransactionManager transactionManager, Clock clock) {
        this.runs = runs;
        this.nodes = nodes;
        this.requirements = requirements;
        this.decisions = decisions;
        this.artifacts = artifacts;
        this.replanning = replanning;
        this.catalog = catalog;
        this.coordinator = coordinator;
        this.locks = locks;
        this.audit = audit;
        this.tx = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    public Answered answer(UUID runId, ClarificationAnswers request, ApiPrincipal approver) {
        Answered answered = locks.withLock(runId, () -> tx.execute(status -> record(runId, request, approver)));
        coordinator.advance(runId);
        return answered;
    }

    private Answered record(UUID runId, ClarificationAnswers request, ApiPrincipal approver) {
        WorkflowRun run = runs.findById(runId)
                .orElseThrow(() -> new ApiException(ErrorCode.RUN_NOT_FOUND, "No workflow run " + runId + "."));
        if (run.getStatus().isTerminal()) {
            throw new ApiException(ErrorCode.RUN_TERMINAL, "The run is " + run.getStatus() + ".");
        }
        StageNode gate = nodes.findByRunIdAndStageKey(runId, StageType.CLARIFICATION)
                .orElseThrow(() -> new ApiException(ErrorCode.ILLEGAL_STATE, "The run has no clarification stage."));
        if (gate.getStatus() != StageStatus.AWAITING_DECISION || gate.getAwaiting() != AwaitingType.CLARIFICATION) {
            throw new ApiException(ErrorCode.ILLEGAL_STATE, "No clarification is open (status " + gate.getStatus() + ").");
        }
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        if (gate.getDecisionDeadline() != null && now.isAfter(gate.getDecisionDeadline())) {
            throw new ApiException(ErrorCode.DEADLINE_PASSED, "The clarification deadline " + gate.getDecisionDeadline() + " has passed.");
        }
        Map<String, Artifact> current = artifacts.current(runId);
        JsonNode questionnaire = CanonicalJson.parse(current.get("CLARIFICATION_REQUEST").getContent());
        JsonNode normalized = CanonicalJson.parse(current.get("NORMALIZED_REQUIREMENT").getContent());
        Map<String, String> typeByAmbiguity = new HashMap<>();
        normalized.path("ambiguities").forEach(a -> typeByAmbiguity.put(a.path("id").asString(), a.path("type").asString()));
        Map<String, JsonNode> questions = new LinkedHashMap<>();
        questionnaire.path("questions").forEach(q -> questions.put(q.path("questionId").asString(), q));
        Map<String, ClarificationAnswers.Answer> byQuestion = validate(questions, request.answers());

        List<Decision> recorded = new ArrayList<>();
        List<ClarificationDerivation.Answer> answers = new ArrayList<>();
        for (Map.Entry<String, JsonNode> entry : questions.entrySet()) {
            JsonNode question = entry.getValue();
            ClarificationAnswers.Answer answer = byQuestion.get(entry.getKey());
            JsonNode option = option(question, answer.optionId());
            String ambiguityId = question.path("ambiguityId").asString();
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("questionId", entry.getKey());
            payload.put("ambiguityId", ambiguityId);
            payload.put("question", question.path("question").asString());
            payload.put("optionId", answer.optionId());
            payload.put("label", option == null ? null : option.path("label").asString());
            payload.put("impact", option == null ? null : option.path("impact").asString());
            payload.put("answer", answer.answer());
            Decision decision = decisions.save(Decision.create(runId, StageType.CLARIFICATION, DecisionType.CLARIFICATION_ANSWER,
                    answer.optionId() != null ? "OPTION_" + answer.optionId() : "FREE_TEXT", ActorType.HUMAN, approver.id(),
                    Role.APPROVER.name(), request.rationale(), CanonicalJson.write(payload), null, now));
            recorded.add(decision);
            answers.add(new ClarificationDerivation.Answer(entry.getKey(), typeByAmbiguity.getOrDefault(ambiguityId, ambiguityId),
                    question.path("question").asString(), answer.optionId(), option == null ? null : option.path("label").asString(),
                    answer.optionId() == null ? answer.answer() : null, decision.getId().toString()));
        }

        List<RequirementVersion> versions = requirements.findByRunIdOrderByVersionAsc(runId);
        RequirementVersion latest = versions.get(versions.size() - 1);
        List<CapabilityEntry> capabilities = new ArrayList<>();
        normalized.path("capabilities").forEach(c -> {
            if (!c.path("baseline").asBoolean(true)) {
                catalog.get(c.path("id").asString()).ifPresent(capabilities::add);
            }
        });
        ClarificationDerivation.Result derived = ClarificationDerivation.derive(CanonicalJson.parse(latest.getContent()),
                run.getClarificationRounds() + 1, answers, capabilities);
        String document = CanonicalJson.canonicalize(derived.document());
        int nextVersion = latest.getVersion() + 1;
        requirements.save(RequirementVersion.create(runId, nextVersion, "CLARIFIED", document, Fingerprints.sha256(document), approver.id(),
                now, recorded.get(0).getId()));
        run.requirementVersion(nextVersion);
        run.countClarificationRound();
        audit.record(runId, ActorType.HUMAN, approver.id(), "CLARIFICATION_ANSWERED", StageType.CLARIFICATION.name(), null,
                "REQUIREMENT_V" + nextVersion, "OK", request.rationale(), Map.of("answers", recorded.size(), "resolved",
                        derived.resolvedQuestionIds(), "requirementVersion", nextVersion));

        int planVersion = replanning.replan(runId, classification(derived.declaredType(), run), StageType.REQUIREMENT_ANALYSIS,
                "CLARIFICATION", "clarification round " + run.getClarificationRounds() + " answered by " + approver.id()
                        + "; requirement version " + nextVersion, approver.id(), now);
        // The clarified requirement enters at requirement analysis; ingestion is not repeated.
        int generation = nodes.findByRunIdAndStageKey(runId, StageType.CLARIFICATION).orElseThrow().getGeneration();
        artifacts.store(runId, StageType.CLARIFICATION, generation, 0, "clarification:" + approver.id(),
                List.of(ArtifactDraft.json("REQUIREMENT", document)), Map.of(), now);
        return new Answered(nextVersion, planVersion, recorded);
    }

    private static Map<String, ClarificationAnswers.Answer> validate(Map<String, JsonNode> questions,
            List<ClarificationAnswers.Answer> answers) {
        Map<String, ClarificationAnswers.Answer> byQuestion = new LinkedHashMap<>();
        for (ClarificationAnswers.Answer answer : answers) {
            JsonNode question = questions.get(answer.questionId());
            if (question == null) {
                throw invalid("unknown question " + answer.questionId());
            }
            if (byQuestion.put(answer.questionId(), answer) != null) {
                throw invalid("question " + answer.questionId() + " is answered twice");
            }
            if (answer.optionId() != null) {
                if (option(question, answer.optionId()) == null) {
                    throw invalid("question " + answer.questionId() + " has no option " + answer.optionId());
                }
            } else if (answer.answer() == null || answer.answer().isBlank() || !question.path("allowFreeText").asBoolean()) {
                throw invalid("question " + answer.questionId() + " needs one of its options"
                        + (question.path("allowFreeText").asBoolean() ? " or a free-text answer" : ""));
            }
        }
        Set<String> missing = new HashSet<>(questions.keySet());
        missing.removeAll(byQuestion.keySet());
        if (!missing.isEmpty()) {
            throw invalid("every open question must be answered; missing " + missing.stream().sorted().toList());
        }
        return byQuestion;
    }

    private static JsonNode option(JsonNode question, String optionId) {
        if (optionId == null) {
            return null;
        }
        for (JsonNode option : question.path("options")) {
            if (option.path("optionId").asString().equals(optionId)) {
                return option;
            }
        }
        return null;
    }

    private static Classification classification(String declaredType, WorkflowRun run) {
        return switch (declaredType) {
            case "CHANGE_TO_EXISTING" -> Classification.CHANGE_TO_EXISTING;
            case "NEW_CAPABILITY" -> Classification.NEW_CAPABILITY;
            default -> run.getClassification();
        };
    }

    private static ApiException invalid(String message) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, "Invalid clarification answers: " + message + ".");
    }
}
