package com.agentic.urlshortener.orchestration.agent.probes;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.agentic.urlshortener.orchestration.port.ApplicationPlanePort;
import com.agentic.urlshortener.orchestration.port.LinkSnapshot;
import com.agentic.urlshortener.orchestration.port.ProbeResponse;
import com.agentic.urlshortener.orchestration.port.SyntheticLinkSpec;

/**
 * What a probe works with: the run's permission-scoped port and a counter of synthetic links created.
 * Aliases are suffixed with the run id, so concurrent runs and consumers' own aliases never collide
 * with probe data (for example {@code spring-sale-1a2b3c4d} for the alias {@code spring-sale}).
 */
public final class ProbeContext {

    private final UUID runId;
    private final ApplicationPlanePort port;
    private final AtomicInteger created = new AtomicInteger();

    public ProbeContext(UUID runId, ApplicationPlanePort port) {
        this.runId = runId;
        this.port = port;
    }

    public ProbeResponse create(String url, String alias) {
        return create(new SyntheticLinkSpec(url, alias, null, null));
    }

    public ProbeResponse create(SyntheticLinkSpec spec) {
        ProbeResponse response = port.createSyntheticLink(runId, spec);
        if (response.outcome() == ProbeResponse.Outcome.CREATED) {
            created.incrementAndGet();
        }
        return response;
    }

    public Optional<LinkSnapshot> find(String code) {
        return port.findLink(code);
    }

    public ProbeResponse resolve(String code) {
        return port.resolve(code);
    }

    /** A run-scoped variant of {@code base}. */
    public String alias(String base) {
        return base + "-" + runId.toString().substring(0, 8);
    }

    public int created() {
        return created.get();
    }
}
