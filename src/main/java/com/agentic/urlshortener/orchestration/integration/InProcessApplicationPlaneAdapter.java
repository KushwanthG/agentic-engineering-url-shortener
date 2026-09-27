package com.agentic.urlshortener.orchestration.integration;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import com.agentic.urlshortener.common.exception.ApiException;
import com.agentic.urlshortener.common.util.CanonicalJson;
import com.agentic.urlshortener.orchestration.audit.AuditService;
import com.agentic.urlshortener.orchestration.domain.ActorType;
import com.agentic.urlshortener.orchestration.domain.AuditRecord;
import com.agentic.urlshortener.orchestration.port.ApplicationPlanePort;
import com.agentic.urlshortener.orchestration.port.CapabilityReleaseReader;
import com.agentic.urlshortener.orchestration.port.CapabilityState;
import com.agentic.urlshortener.orchestration.port.DeliveryStatus;
import com.agentic.urlshortener.orchestration.port.LinkSnapshot;
import com.agentic.urlshortener.orchestration.port.ProbeResponse;
import com.agentic.urlshortener.orchestration.port.SyntheticLinkSpec;
import com.agentic.urlshortener.shortener.domain.Capability;
import com.agentic.urlshortener.shortener.dto.CapabilityChange;
import com.agentic.urlshortener.shortener.dto.CreateLinkCommand;
import com.agentic.urlshortener.shortener.dto.CreatedLink;
import com.agentic.urlshortener.shortener.dto.Resolution;
import com.agentic.urlshortener.shortener.repository.ShortLinkRepository;
import com.agentic.urlshortener.shortener.service.CapabilityService;
import com.agentic.urlshortener.shortener.service.ClickRecorder;
import com.agentic.urlshortener.shortener.service.LinkCreationService;
import com.agentic.urlshortener.shortener.service.RedirectService;

/**
 * The in-process implementation of {@link ApplicationPlanePort} (ADR-001, ADR-018). Probe calls use
 * the real creation and redirect services, so verification exercises the running system; release
 * changes are audited on the {@code GLOBAL} chain in the same transaction as the flag change.
 */
@Component
public class InProcessApplicationPlaneAdapter implements ApplicationPlanePort, CapabilityReleaseReader {

    private static final String CONTRACT = "contracts/openapi.yaml";

    private final LinkCreationService creation;
    private final RedirectService redirects;
    private final ShortLinkRepository links;
    private final CapabilityService capabilities;
    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final Clock clock;

    public InProcessApplicationPlaneAdapter(LinkCreationService creation, RedirectService redirects, ShortLinkRepository links,
            CapabilityService capabilities, JdbcTemplate jdbc, AuditService audit, Clock clock) {
        this.creation = creation;
        this.redirects = redirects;
        this.links = links;
        this.capabilities = capabilities;
        this.jdbc = jdbc;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    public Optional<LinkSnapshot> findLink(String code) {
        return links.findByCode(code).map(link -> new LinkSnapshot(link.getCode(), link.getTargetUrl(),
                link.statusAt(clock.instant()).name(), link.getClickCount(), link.isCustomAlias(), link.isSynthetic(),
                link.getMaxClicks(), link.getExpiresAt()));
    }

    @Override
    public ProbeResponse resolve(String code) {
        try {
            Resolution resolution = redirects.resolve(code, null);
            return switch (resolution.outcome()) {
                case REDIRECT -> ProbeResponse.redirect(resolution.targetUrl());
                case NOT_FOUND -> ProbeResponse.notFound();
                case EXPIRED -> ProbeResponse.expired();
                case UNAVAILABLE -> ProbeResponse.unavailable("click could not be recorded (fail closed)");
            };
        } catch (DataAccessException e) {
            return ProbeResponse.unavailable(e.getClass().getSimpleName());
        }
    }

    @Override
    public ProbeResponse createSyntheticLink(UUID runId, SyntheticLinkSpec spec) {
        try {
            CreatedLink created = creation.create(new CreateLinkCommand(spec.url(), spec.expiresAt(), spec.alias(), spec.maxClicks(),
                    "run:" + runId.toString().substring(0, 8), spec.idempotencyKey(), runId));
            return ProbeResponse.created(created.view().code(), created.view().targetUrl());
        } catch (ApiException e) {
            return ProbeResponse.rejected(e.code().name(), e.getMessage());
        } catch (DataAccessException e) {
            return ProbeResponse.unavailable(e.getClass().getSimpleName());
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<CapabilityRecord> capabilities() {
        return capabilities.releases().stream().map(r -> new CapabilityRecord(r.getCapabilityId(), r.isReleased(),
                r.getParameters() == null ? Map.of() : (Map<String, Object>) CanonicalJson.read(r.getParameters(), Map.class),
                r.getChangedBy(), r.getChangedByRun(), r.getChangedAt(), r.getReason())).toList();
    }

    @Override
    public int deleteSyntheticLinks(UUID runId) {
        return links.deleteBySyntheticRunId(runId);
    }

    @Override
    public CapabilityState capability(String capabilityId) {
        Capability capability = require(capabilityId);
        return new CapabilityState(capabilityId, capabilities.isReleased(capability), capabilities.parameters(capability),
                capabilities.lastChangedByRun(capability).orElse(null));
    }

    @Override
    public DeliveryStatus deliveryStatus(String capabilityId, String migrationVersion, List<String> contractFields) {
        List<DeliveryStatus.Check> checks = new ArrayList<>();
        Optional<Capability> capability = Capability.fromId(capabilityId);
        boolean registered = capability.isPresent() && capabilities.isRegistered(capability.get());
        checks.add(new DeliveryStatus.Check("PROVIDER_REGISTERED", registered ? "PASS" : "FAIL",
                registered ? "capability '" + capabilityId + "' is known and has a release row"
                        : "capability '" + capabilityId + "' is not provided by the running system"));
        if (migrationVersion == null) {
            checks.add(new DeliveryStatus.Check("MIGRATION_APPLIED", "NOT_APPLICABLE", "no schema change declared"));
        } else {
            Integer applied = jdbc.queryForObject("SELECT COUNT(*) FROM \"flyway_schema_history\" WHERE \"version\" = ? AND \"success\" = TRUE",
                    Integer.class, migrationVersion);
            boolean ok = applied != null && applied > 0;
            checks.add(new DeliveryStatus.Check("MIGRATION_APPLIED", ok ? "PASS" : "FAIL",
                    "Flyway history " + (ok ? "contains" : "does not contain") + " a successful migration V" + migrationVersion));
        }
        if (contractFields.isEmpty()) {
            checks.add(new DeliveryStatus.Check("CONTRACT_DECLARES_FIELDS", "NOT_APPLICABLE", "no contract fields declared"));
        } else {
            Set<String> declared = linkContractFields();
            List<String> missing = contractFields.stream().filter(f -> !declared.contains(f)).toList();
            checks.add(new DeliveryStatus.Check("CONTRACT_DECLARES_FIELDS", missing.isEmpty() ? "PASS" : "FAIL",
                    missing.isEmpty() ? "openapi.yaml declares " + contractFields : "openapi.yaml lacks " + missing));
        }
        checks.add(new DeliveryStatus.Check("RELEASE_STATE_KNOWN", registered ? "PASS" : "FAIL",
                registered ? "release state readable" : "no release state"));
        return new DeliveryStatus(checks);
    }

    @Override
    public <T> T withPreview(String capabilityId, Map<String, Object> parameters, Supplier<T> action) {
        return capabilities.withPreview(require(capabilityId), parameters, action);
    }

    @Override
    public <T> T withSyntheticClickOutage(Supplier<T> action) {
        return ClickRecorder.withSyntheticOutage(action);
    }

    @Override
    @Transactional
    public CapabilityState setRelease(String capabilityId, boolean released, Map<String, Object> parameters, UUID runId,
            String reason) {
        CapabilityChange change = capabilities.setRelease(require(capabilityId), released, parameters, "orchestrator", runId, reason);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("capability", capabilityId);
        details.put("previouslyReleased", change.previouslyReleased());
        details.put("released", released);
        details.put("parameters", change.parameters());
        details.put("runId", String.valueOf(runId));
        audit.append(new AuditRecord(null, ActorType.SYSTEM, "orchestrator", "CAPABILITY_CHANGED", capabilityId,
                String.valueOf(change.previouslyReleased()), String.valueOf(released), change.changed() ? "CHANGED" : "UNCHANGED",
                reason, CanonicalJson.write(details)));
        return new CapabilityState(capabilityId, released, change.parameters(), runId);
    }

    private static Capability require(String capabilityId) {
        return Capability.fromId(capabilityId)
                .orElseThrow(() -> new IllegalArgumentException("unknown capability " + capabilityId));
    }

    /** Property names of the link request and response schemas in the published contract. */
    @SuppressWarnings("unchecked")
    private static Set<String> linkContractFields() {
        try (InputStream in = new ClassPathResource(CONTRACT).getInputStream()) {
            Map<String, Object> root = new Yaml(new SafeConstructor(new LoaderOptions())).load(in);
            Map<String, Object> schemas = (Map<String, Object>) ((Map<String, Object>) root.get("components")).get("schemas");
            Set<String> fields = new HashSet<>();
            for (String schema : List.of("CreateLinkRequest", "LinkResponse")) {
                Map<String, Object> properties = (Map<String, Object>) ((Map<String, Object>) schemas.get(schema)).get("properties");
                fields.addAll(properties.keySet());
            }
            return fields;
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + CONTRACT, e);
        }
    }
}
