package com.agentic.urlshortener.orchestration.reliability;

import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import com.agentic.urlshortener.common.exception.ApiException;
import com.agentic.urlshortener.common.exception.ErrorCode;
import com.agentic.urlshortener.orchestration.agent.AgentPermission;
import com.agentic.urlshortener.orchestration.agent.StageAgent;
import com.agentic.urlshortener.orchestration.agent.StageContext;
import com.agentic.urlshortener.orchestration.agent.StageResult;
import com.agentic.urlshortener.orchestration.domain.FailureClass;
import com.agentic.urlshortener.orchestration.domain.FaultPlan;
import com.agentic.urlshortener.orchestration.domain.FaultPlan.Fault;
import com.agentic.urlshortener.orchestration.domain.StageAttempt;
import com.agentic.urlshortener.orchestration.domain.StageType;
import com.agentic.urlshortener.orchestration.dto.RequirementSubmission;
import com.agentic.urlshortener.orchestration.port.ApplicationPlanePort;
import com.agentic.urlshortener.orchestration.port.CapabilityState;
import com.agentic.urlshortener.orchestration.port.DeliveryStatus;
import com.agentic.urlshortener.orchestration.port.LinkSnapshot;
import com.agentic.urlshortener.orchestration.port.ProbeResponse;
import com.agentic.urlshortener.orchestration.port.SyntheticLinkSpec;
import com.agentic.urlshortener.orchestration.repository.WorkflowRunRepository;

/**
 * Applies the simulated faults of a demonstration run (FR-REL-11, ADR-009). Only runs submitted with
 * simulation options while fault injection is enabled carry faults. A fault affects the first
 * {@code occurrences} attempts of its stage (counted over the run's attempts flagged with that fault):
 * error faults replace the agent call, {@code DELAY} and {@code TIMEOUT} slow it down, and
 * {@code VERIFICATION_FAILURE} makes every redirect the agent's probes observe fail. {@code POLICY_FAILURE}
 * forces a policy to FAIL in every evaluation of the run (read by the policy facts), and
 * {@code COMPENSATION_FAILURE} makes the synthetic-data compensation fail.
 */
@Component
public class FaultInjector {

    private static final Set<StageType> PROBE_STAGES = EnumSet.of(StageType.TESTING, StageType.REGRESSION_TESTING,
            StageType.SECURITY_VERIFICATION, StageType.RELEASE);
    private static final Set<String> DISPATCH_FAULTS = Set.of(FaultPlan.TRANSIENT_ERROR, FaultPlan.PERMANENT_ERROR, FaultPlan.TIMEOUT,
            FaultPlan.DELAY, FaultPlan.VERIFICATION_FAILURE);

    private final WorkflowRunRepository runs;
    private final Map<UUID, AtomicInteger> compensationFaultsUsed = new ConcurrentHashMap<>();

    public FaultInjector(WorkflowRunRepository runs) {
        this.runs = runs;
    }

    /** Rejects simulation options that name unknown stages, gates, or incomplete faults (400 VALIDATION_FAILED). */
    public static void validate(RequirementSubmission.SimulationOptions simulation, Set<String> policyIds) {
        for (RequirementSubmission.FaultSpec fault : simulation.faults()) {
            StageType stage;
            try {
                stage = StageType.valueOf(fault.stage());
            } catch (IllegalArgumentException e) {
                throw invalid("unknown stage " + fault.stage());
            }
            if (stage.isGate()) {
                throw invalid(stage + " is a human gate; faults apply to agent stages only");
            }
            switch (fault.type()) {
                case FaultPlan.DELAY -> {
                    if (fault.delayMillis() == null) {
                        throw invalid("a DELAY fault needs delayMillis");
                    }
                }
                case FaultPlan.POLICY_FAILURE -> {
                    if (stage != StageType.COMPLIANCE_EVALUATION || fault.policyId() == null || !policyIds.contains(fault.policyId())) {
                        throw invalid("a POLICY_FAILURE fault needs stage COMPLIANCE_EVALUATION and a policyId of the policy set");
                    }
                }
                case FaultPlan.VERIFICATION_FAILURE -> {
                    if (!PROBE_STAGES.contains(stage)) {
                        throw invalid("a VERIFICATION_FAILURE fault applies to probe stages only: " + PROBE_STAGES);
                    }
                }
                default -> {
                }
            }
        }
    }

    private static ApiException invalid(String message) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, "Invalid simulation option: " + message + ".");
    }

    /** The fault the next attempt of {@code stage} carries, given the run's attempts so far. */
    public Optional<Fault> faultFor(String faultPlan, StageType stage, List<StageAttempt> previousAttempts) {
        FaultPlan plan = FaultPlan.parse(faultPlan);
        for (Fault fault : plan.faults()) {
            if (fault.stage() != stage) {
                continue;
            }
            if (fault.type().equals(FaultPlan.POLICY_FAILURE)) {
                return Optional.of(fault);
            }
            long used = previousAttempts.stream()
                    .filter(a -> a.getStageKey() == stage && fault.type().equals(a.getSimulatedFault())).count();
            if (DISPATCH_FAULTS.contains(fault.type()) && used < fault.occurrences()) {
                return Optional.of(fault);
            }
        }
        return Optional.empty();
    }

    /** The agent as this attempt runs it: unchanged, or replaced or wrapped by the fault. */
    public StageAgent inject(StageAgent agent, Fault fault) {
        return switch (fault.type()) {
            case FaultPlan.TRANSIENT_ERROR -> new Faulty(agent, context ->
                    new StageResult.Failed(FailureClass.TRANSIENT, "simulated transient error (fault injection)"));
            case FaultPlan.PERMANENT_ERROR -> new Faulty(agent, context ->
                    new StageResult.Failed(FailureClass.PERMANENT, "simulated permanent error (fault injection)"));
            case FaultPlan.DELAY -> new Faulty(agent, context -> {
                sleep(Duration.ofMillis(fault.delayMillis()));
                return agent.execute(context);
            });
            case FaultPlan.TIMEOUT -> new Faulty(agent, context -> {
                sleep(Duration.ofMinutes(10));
                return new StageResult.Failed(FailureClass.TRANSIENT, "simulated hang interrupted");
            });
            case FaultPlan.VERIFICATION_FAILURE -> new Faulty(agent, context -> agent.execute(new StageContext(context.runId(),
                    context.stageType(), context.generation(), context.attemptNo(), context.requirementVersion(), context.requirement(),
                    context.inputs(), context.policySetVersion(), new BrokenRedirects(context.port()), context::isCancelled)));
            default -> agent;
        };
    }

    /** Whether the next synthetic-data compensation of the run fails (consumes one occurrence). */
    public boolean compensationFails(UUID runId) {
        Optional<Fault> fault = runs.findById(runId).map(r -> FaultPlan.parse(r.getFaultPlan()))
                .flatMap(plan -> plan.first(FaultPlan.COMPENSATION_FAILURE));
        if (fault.isEmpty()) {
            return false;
        }
        return compensationFaultsUsed.computeIfAbsent(runId, id -> new AtomicInteger()).getAndIncrement() < fault.get().occurrences();
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** The agent's identity and permissions with a simulated behavior. */
    private record Faulty(StageAgent agent, Function<StageContext, StageResult> behavior) implements StageAgent {
        @Override public StageType stageType() { return agent.stageType(); }
        @Override public String agentId() { return agent.agentId(); }
        @Override public boolean fallback() { return agent.fallback(); }
        @Override public Set<AgentPermission> permissions() { return agent.permissions(); }
        @Override public StageResult execute(StageContext context) { return behavior.apply(context); }
    }

    /** The agent's own port, except that every redirect is observed as not found (simulated verification failure). */
    private record BrokenRedirects(ApplicationPlanePort port) implements ApplicationPlanePort {
        @Override public Optional<LinkSnapshot> findLink(String code) { return port.findLink(code); }
        @Override public ProbeResponse resolve(String code) { return ProbeResponse.notFound(); }
        @Override public ProbeResponse createSyntheticLink(UUID runId, SyntheticLinkSpec spec) { return port.createSyntheticLink(runId, spec); }
        @Override public int deleteSyntheticLinks(UUID runId) { return port.deleteSyntheticLinks(runId); }
        @Override public CapabilityState capability(String capabilityId) { return port.capability(capabilityId); }
        @Override public DeliveryStatus deliveryStatus(String capabilityId, String migration, List<String> fields) {
            return port.deliveryStatus(capabilityId, migration, fields);
        }
        @Override public <T> T withPreview(String capabilityId, Map<String, Object> parameters, Supplier<T> action) {
            return port.withPreview(capabilityId, parameters, action);
        }
        @Override public <T> T withSyntheticClickOutage(Supplier<T> action) { return port.withSyntheticClickOutage(action); }
        @Override public CapabilityState setRelease(String capabilityId, boolean released, Map<String, Object> parameters, UUID runId,
                String reason) {
            return port.setRelease(capabilityId, released, parameters, runId, reason);
        }
    }
}
