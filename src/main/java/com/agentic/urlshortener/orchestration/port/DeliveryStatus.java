package com.agentic.urlshortener.orchestration.port;

import java.util.List;

/** Live delivery checks of a capability (change-set schema: PROVIDER_REGISTERED, MIGRATION_APPLIED, ...). */
public record DeliveryStatus(List<Check> checks) {

    public DeliveryStatus {
        checks = List.copyOf(checks);
    }

    /** One check with result {@code PASS}, {@code FAIL}, or {@code NOT_APPLICABLE}. */
    public record Check(String check, String result, String evidence) {
    }

    public boolean delivered() {
        return checks.stream().noneMatch(c -> "FAIL".equals(c.result()));
    }
}
