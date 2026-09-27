package com.agentic.urlshortener.orchestration.knowledge;

import java.util.List;
import java.util.Map;

/** One capability of the catalog ({@code orchestration/capability-catalog.yaml}). */
public record CapabilityEntry(
        String id,
        String name,
        boolean baseline,
        String changeType,
        List<String> keywords,
        List<Component> components,
        Api api,
        Schema schema,
        Boolean securityControlChange,
        Release release,
        String rollback,
        List<String> acceptanceProbes,
        List<String> securityProbes,
        List<String> regressionProbes,
        List<String> impactSeeds,
        List<String> impactedRequirements,
        List<Threat> threats,
        List<DocAnchor> docAnchors,
        List<Task> tasks,
        List<RegressionRisk> regressionRisks,
        List<String> acceptanceTemplates) {

    public CapabilityEntry {
        keywords = orEmpty(keywords);
        securityControlChange = Boolean.TRUE.equals(securityControlChange);
        components = orEmpty(components);
        acceptanceProbes = orEmpty(acceptanceProbes);
        securityProbes = orEmpty(securityProbes);
        regressionProbes = orEmpty(regressionProbes);
        impactSeeds = orEmpty(impactSeeds);
        impactedRequirements = orEmpty(impactedRequirements);
        threats = orEmpty(threats);
        docAnchors = orEmpty(docAnchors);
        tasks = orEmpty(tasks);
        regressionRisks = orEmpty(regressionRisks);
        acceptanceTemplates = orEmpty(acceptanceTemplates);
    }

    public record Component(String name, String path, String change, String responsibility) {
    }

    public record Api(String contractVersion, List<String> fields, List<String> errorCodes, List<ApiChange> changes) {
        public Api {
            fields = orEmpty(fields);
            errorCodes = orEmpty(errorCodes);
            changes = orEmpty(changes);
        }
    }

    public record ApiChange(String operation, String change, String compatibility) {
    }

    public record Schema(String migration, List<SchemaChange> changes, List<String> columns) {
        public Schema {
            changes = orEmpty(changes);
            columns = orEmpty(columns);
        }
    }

    public record SchemaChange(String migration, String change, String compatibility) {
    }

    public record Release(String strategy, Map<String, Object> parameters) {
        public Release {
            parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
        }
    }

    public record Threat(String id, String category, String description, String severity, String mitigation, List<String> verifiedBy) {
        public Threat {
            verifiedBy = orEmpty(verifiedBy);
        }
    }

    /** A repository document expected to mention the capability. */
    /** A risk the change poses to existing behavior, with its mitigation (brownfield impact analysis). */
    public record RegressionRisk(String id, String description, String severity, String mitigation) {
    }

    public record DocAnchor(String path, String mentions) {
    }

    /**
     * Task template of the capability's decomposition. {@code criteria} are phrases that map the task to
     * acceptance criteria containing them; {@code "*"} covers every criterion.
     */
    public record Task(String id, String title, String kind, String component, List<String> dependsOn, boolean parallelizable,
            List<String> criteria) {
        public Task {
            dependsOn = orEmpty(dependsOn);
            criteria = orEmpty(criteria);
        }
    }

    private static <T> List<T> orEmpty(List<T> list) {
        return list == null ? List.of() : List.copyOf(list);
    }
}
