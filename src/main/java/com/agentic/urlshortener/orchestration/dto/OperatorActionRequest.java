package com.agentic.urlshortener.orchestration.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of pause, resume, and safe-stop (contract schema {@code OperatorActionRequest}). */
public record OperatorActionRequest(
        @NotBlank(message = "reason is required") @Size(min = 3, max = 1000, message = "reason must be 3 to 1000 characters")
        String reason) {
}
