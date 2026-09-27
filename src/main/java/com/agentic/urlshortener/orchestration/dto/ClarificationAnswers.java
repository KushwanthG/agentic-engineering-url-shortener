package com.agentic.urlshortener.orchestration.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/** Answers to the open clarification questions (contract schema {@code ClarificationAnswers}, FR-GOV-08). */
public record ClarificationAnswers(
        @NotEmpty(message = "answers are required") List<@Valid Answer> answers,
        @NotBlank(message = "rationale is required") @Size(min = 3, max = 2000) String rationale) {

    /** One answer: a listed option, or free text where the question allows it. */
    public record Answer(
            @NotBlank(message = "questionId is required") String questionId,
            String optionId,
            @Size(max = 1000) String answer) {
    }
}
