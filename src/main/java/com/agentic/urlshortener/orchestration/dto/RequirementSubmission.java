package com.agentic.urlshortener.orchestration.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Body of {@code POST /api/v1/workflows} ({@code openapi.yaml#/components/schemas/RequirementSubmission}, PVT-26). */
public record RequirementSubmission(
        @Pattern(regexp = "^[A-Za-z0-9-]{1,64}$", message = "ref must be 1 to 64 letters, digits, or '-'") String ref,
        @NotBlank(message = "title is required") @Size(max = 200) String title,
        @NotBlank(message = "narrative is required") @Size(max = 10_000) String narrative,
        @Pattern(regexp = "NEW_CAPABILITY|CHANGE_TO_EXISTING|UNSPECIFIED", message = "type must be NEW_CAPABILITY, CHANGE_TO_EXISTING, or UNSPECIFIED")
        String type,
        @Size(max = 30) List<@NotBlank @Size(max = 1000) String> acceptanceCriteria,
        @Size(max = 30) List<@NotBlank @Size(max = 1000) String> constraints,
        @Valid SimulationOptions simulation) {

    public RequirementSubmission {
        acceptanceCriteria = acceptanceCriteria == null ? List.of() : List.copyOf(acceptanceCriteria);
        constraints = constraints == null ? List.of() : List.copyOf(constraints);
    }

    /** Demonstration only: rejected unless fault injection is enabled; every fault is labeled simulated. */
    public record SimulationOptions(
            @Min(1) @Max(86_400) Integer gateDeadlineSeconds,
            @Size(max = 10) List<@Valid FaultSpec> faults) {

        public SimulationOptions {
            faults = faults == null ? List.of() : List.copyOf(faults);
        }
    }

    /** One simulated fault on one stage. */
    public record FaultSpec(
            @NotNull String stage,
            @NotNull @Pattern(regexp = "TRANSIENT_ERROR|PERMANENT_ERROR|TIMEOUT|DELAY|VERIFICATION_FAILURE|COMPENSATION_FAILURE|POLICY_FAILURE")
            String type,
            @Min(1) @Max(10) Integer occurrences,
            @Min(0) @Max(120_000) Integer delayMillis,
            String policyId) {
    }
}
