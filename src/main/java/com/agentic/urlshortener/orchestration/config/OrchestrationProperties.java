package com.agentic.urlshortener.orchestration.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Control-plane settings ({@code app.orchestration.*}); defaults are the PVT values of the spec. */
@ConfigurationProperties("app.orchestration")
public record OrchestrationProperties(
        String codebaseRoot,
        int auditRetentionDays,
        Duration gateDeadline,
        int maxClarificationRounds,
        int stageExecutorThreads,
        Autonomy autonomy,
        FaultInjection faultInjection) {

    public OrchestrationProperties {
        codebaseRoot = codebaseRoot == null ? "." : codebaseRoot;
        gateDeadline = gateDeadline == null ? Duration.ofHours(24) : gateDeadline;
        stageExecutorThreads = stageExecutorThreads <= 0 ? 8 : stageExecutorThreads;
        autonomy = autonomy == null ? new Autonomy(60, Duration.ofMinutes(10)) : autonomy;
        faultInjection = faultInjection == null ? new FaultInjection(false) : faultInjection;
    }

    /** PVT-17: maximum stage attempts and processing time per run. */
    public record Autonomy(int maxAttempts, Duration maxProcessingTime) {
    }

    /** Fault injection for demonstrations and tests only; disabled by default (FR-REL-11). */
    public record FaultInjection(boolean enabled) {
    }
}
