package com.agentic.urlshortener.orchestration.agent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.agentic.urlshortener.common.util.CanonicalJson;
import com.agentic.urlshortener.orchestration.domain.FailureClass;
import com.agentic.urlshortener.orchestration.domain.StageType;
import com.agentic.urlshortener.orchestration.knowledge.CapabilityCatalog;
import com.agentic.urlshortener.orchestration.knowledge.CapabilityEntry;
import com.agentic.urlshortener.orchestration.port.DeliveryStatus;

import tools.jackson.databind.JsonNode;

/**
 * IMPLEMENTATION (FR-ORC-15): produces the change set (planned change per task and component) and
 * verifies, live through the port, that the capability is actually delivered in the running system:
 * provider registered, migration applied, contract fields declared. An absent capability is a
 * permanent failure; success is never reported for code that is not there.
 */
@Component
public class ImplementationAgent implements StageAgent {

    private final CapabilityCatalog catalog;

    public ImplementationAgent(CapabilityCatalog catalog) {
        this.catalog = catalog;
    }

    @Override
    public StageType stageType() {
        return StageType.IMPLEMENTATION;
    }

    @Override
    public String agentId() {
        return "implementer@1.0";
    }

    @Override
    public Set<AgentPermission> permissions() {
        return Set.of(AgentPermission.READ_CAPABILITIES);
    }

    @Override
    public StageResult execute(StageContext context) {
        String capabilityId = context.inputJson("DESIGN").path("releasePlan").path("capability").asString();
        CapabilityEntry capability = catalog.get(capabilityId).orElse(null);
        if (capability == null) {
            return new StageResult.Failed(FailureClass.PERMANENT, "the design names an unknown capability: " + capabilityId);
        }
        String migration = capability.schema() == null ? null : capability.schema().migration();
        List<String> fields = capability.api() == null ? List.of() : capability.api().fields();
        DeliveryStatus delivery = context.port().deliveryStatus(capabilityId, migration, fields);
        if (!delivery.delivered()) {
            List<String> failed = delivery.checks().stream().filter(c -> "FAIL".equals(c.result()))
                    .map(c -> c.check() + ": " + c.evidence()).toList();
            return new StageResult.Failed(FailureClass.PERMANENT,
                    "capability " + capabilityId + " is not delivered in the running system: " + failed);
        }

        List<Map<String, Object>> units = new ArrayList<>();
        for (JsonNode task : context.inputJson("TASK_GRAPH").path("tasks")) {
            String component = task.path("component").asString("");
            String path = capability.components().stream().filter(c -> c.name().equals(component)).map(CapabilityEntry.Component::path)
                    .findFirst().orElse(component);
            units.add(Map.of("taskId", task.path("id").asString(), "path", path, "change", task.path("title").asString()));
        }
        Map<String, Object> changeSet = new LinkedHashMap<>();
        changeSet.put("capability", capabilityId);
        changeSet.put("delivered", true);
        changeSet.put("checks", delivery.checks().stream()
                .map(c -> Map.of("check", c.check(), "result", c.result(), "evidence", c.evidence())).toList());
        changeSet.put("units", units);
        return new StageResult.Succeeded(List.of(ArtifactDraft.json("CHANGE_SET", CanonicalJson.write(changeSet))),
                "capability " + capabilityId + " delivered; " + units.size() + " change units");
    }
}
