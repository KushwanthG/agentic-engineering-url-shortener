package com.agentic.urlshortener.orchestration.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** An amended requirement for an active run (contract schema {@code ChangeRequestSubmission}, FR-RPL-03). */
public record ChangeRequestSubmission(
        @NotNull(message = "amendment is required") @Valid RequirementSubmission amendment,
        @NotBlank(message = "reason is required") @Size(min = 3, max = 1000) String reason) {
}
