package com.agentic.urlshortener.orchestration.dto;

import java.time.Instant;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Request for a time-bound policy exception (contract schema {@code PolicyExceptionRequest}, FR-POL-04). */
public record PolicyExceptionRequest(
        @NotBlank(message = "policyId is required") String policyId,
        @NotBlank(message = "reason is required") @Size(min = 3, max = 1000) String reason,
        @NotBlank(message = "scope is required") @Size(min = 3, max = 500) String scope,
        @NotBlank(message = "compensatingControl is required") @Size(min = 3, max = 1000) String compensatingControl,
        @NotNull(message = "expiresAt is required") @Future(message = "expiresAt must be in the future") Instant expiresAt) {
}
