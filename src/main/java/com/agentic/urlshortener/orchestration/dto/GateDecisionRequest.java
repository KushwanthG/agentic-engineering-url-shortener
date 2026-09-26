package com.agentic.urlshortener.orchestration.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Body of a gate decision ({@code openapi.yaml#/components/schemas/GateDecisionRequest}). */
public record GateDecisionRequest(
        @NotNull(message = "decision is required") @Pattern(regexp = "APPROVE|REJECT", message = "decision must be APPROVE or REJECT")
        String decision,
        @NotBlank(message = "rationale is required") @Size(min = 3, max = 2000) String rationale) {

    public boolean approve() {
        return "APPROVE".equals(decision);
    }
}
