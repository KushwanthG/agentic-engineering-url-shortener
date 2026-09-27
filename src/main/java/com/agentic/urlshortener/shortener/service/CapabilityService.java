package com.agentic.urlshortener.shortener.service;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.agentic.urlshortener.common.exception.ApiException;
import com.agentic.urlshortener.common.exception.ErrorCode;
import com.agentic.urlshortener.common.util.CanonicalJson;
import com.agentic.urlshortener.shortener.domain.Capability;
import com.agentic.urlshortener.shortener.domain.CapabilityRelease;
import com.agentic.urlshortener.shortener.dto.CapabilityChange;
import com.agentic.urlshortener.shortener.repository.CapabilityReleaseRepository;

/**
 * Capability release flags (ADR-018). A capability without a row, or with an unreleased row, is not
 * available; inputs that need it are rejected, never silently ignored (FR-CAP-01). A preview enables
 * a capability for the calling thread only, inside one scoped call, so verification can exercise an
 * unreleased capability without exposing it to consumers.
 */
@Service
public class CapabilityService {

    private static final ThreadLocal<Map<Capability, Map<String, Object>>> PREVIEW = new ThreadLocal<>();

    private final CapabilityReleaseRepository releases;
    private final Clock clock;

    public CapabilityService(CapabilityReleaseRepository releases, Clock clock) {
        this.releases = releases;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public boolean isReleased(Capability capability) {
        Map<Capability, Map<String, Object>> preview = PREVIEW.get();
        if (preview != null && preview.containsKey(capability)) {
            return true;
        }
        return releases.findById(capability.id()).map(CapabilityRelease::isReleased).orElse(false);
    }

    /** The orchestration run that last changed the capability's release state, if any. */
    @Transactional(readOnly = true)
    public Optional<UUID> lastChangedByRun(Capability capability) {
        return releases.findById(capability.id()).map(CapabilityRelease::getChangedByRun);
    }

    /** Release parameters (for example {@code defaultExpiryDays}); empty when none are set. */
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public Map<String, Object> parameters(Capability capability) {
        Map<Capability, Map<String, Object>> preview = PREVIEW.get();
        if (preview != null && preview.containsKey(capability)) {
            return preview.get(capability);
        }
        return releases.findById(capability.id())
                .filter(CapabilityRelease::isReleased)
                .map(CapabilityRelease::getParameters)
                .filter(Objects::nonNull)
                .map(json -> (Map<String, Object>) CanonicalJson.read(json, Map.class))
                .orElse(Map.of());
    }

    public void requireReleased(Capability capability, String input) {
        if (!isReleased(capability)) {
            throw new ApiException(ErrorCode.CAPABILITY_NOT_AVAILABLE,
                    "'" + input + "' needs the '" + capability.id() + "' capability, which is not released.");
        }
    }

    /** Runs {@code action} with {@code capability} enabled for this thread only; the previous state is always restored. */
    public <T> T withPreview(Capability capability, Map<String, ?> parameters, Supplier<T> action) {
        Map<Capability, Map<String, Object>> previous = PREVIEW.get();
        Map<Capability, Map<String, Object>> next = previous == null ? new EnumMap<>(Capability.class) : new EnumMap<>(previous);
        next.put(capability, Map.copyOf(parameters));
        PREVIEW.set(next);
        try {
            return action.get();
        } finally {
            if (previous == null) {
                PREVIEW.remove();
            } else {
                PREVIEW.set(previous);
            }
        }
    }

    /** Sets (never toggles) the release state; calling it again with the same state changes nothing. */
    @Transactional
    @SuppressWarnings("unchecked")
    public CapabilityChange setRelease(Capability capability, boolean released, Map<String, ?> parameters, String changedBy,
            UUID runId, String reason) {
        CapabilityRelease row = releases.findById(capability.id())
                .orElseGet(() -> releases.save(CapabilityRelease.unreleased(capability.id())));
        boolean previouslyReleased = row.isReleased();
        String previousParameters = row.getParameters();
        String newParameters = parameters == null || parameters.isEmpty() ? null : CanonicalJson.write(parameters);
        boolean changed = previouslyReleased != released || !Objects.equals(previousParameters, newParameters);
        if (changed) {
            row.change(released, newParameters, changedBy, runId, clock.instant().truncatedTo(ChronoUnit.MICROS), reason);
        }
        Map<String, Object> before = previousParameters == null ? Map.of() : CanonicalJson.read(previousParameters, Map.class);
        return new CapabilityChange(capability.id(), previouslyReleased, before, released,
                parameters == null ? Map.of() : Map.copyOf(parameters), changed);
    }

    /** The release rows of every capability, in declaration order (read-only; for evidence). */
    @Transactional(readOnly = true)
    public List<CapabilityRelease> releases() {
        return Arrays.stream(Capability.values())
                .map(c -> releases.findById(c.id()).orElseGet(() -> CapabilityRelease.unreleased(c.id())))
                .toList();
    }

    /** Whether the capability has a release row (it is registered at startup). */
    @Transactional(readOnly = true)
    public boolean isRegistered(Capability capability) {
        return releases.existsById(capability.id());
    }
}
