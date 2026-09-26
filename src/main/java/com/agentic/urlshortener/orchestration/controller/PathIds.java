package com.agentic.urlshortener.orchestration.controller;

import java.util.UUID;

import com.agentic.urlshortener.common.exception.ApiException;
import com.agentic.urlshortener.common.exception.ErrorCode;
import com.agentic.urlshortener.orchestration.domain.StageType;

/** Parses path identifiers; malformed ids are reported like unknown ones. */
final class PathIds {

    private PathIds() {
    }

    static UUID runId(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.RUN_NOT_FOUND, "No workflow run " + value + ".");
        }
    }

    static StageType stage(String value) {
        try {
            return StageType.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Unknown stage " + value + ".");
        }
    }
}
