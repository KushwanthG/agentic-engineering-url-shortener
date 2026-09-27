package com.agentic.urlshortener.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.networknt.schema.Error;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;

/** Validates agent outputs against the artifact JSON Schemas of the contract (draft 2020-12). */
public final class ArtifactSchemas {

    private static final SchemaRegistry REGISTRY = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);
    private static final Map<String, Schema> SCHEMAS = new ConcurrentHashMap<>();

    /** Schema of every JSON artifact type (contract `schemas/`); DOCUMENTATION and FINAL_SUMMARY are Markdown. */
    public static final Map<String, String> SCHEMA_BY_TYPE = Map.ofEntries(
            Map.entry("REQUIREMENT", "requirement-document"),
            Map.entry("NORMALIZED_REQUIREMENT", "normalized-requirement"),
            Map.entry("CLARIFICATION_REQUEST", "clarification-request"),
            Map.entry("TASK_GRAPH", "task-graph"),
            Map.entry("IMPACT_ANALYSIS", "impact-analysis"),
            Map.entry("THREAT_MODEL", "threat-model"),
            Map.entry("DESIGN", "design"),
            Map.entry("CHANGE_SET", "change-set"),
            Map.entry("TEST_REPORT", "verification-report"),
            Map.entry("REGRESSION_REPORT", "verification-report"),
            Map.entry("SECURITY_REPORT", "verification-report"),
            Map.entry("VALIDATION_REPORT", "validation-report"),
            Map.entry("COMPLIANCE_REPORT", "compliance-report"),
            Map.entry("READINESS_REPORT", "readiness-report"),
            Map.entry("RELEASE_RECORD", "release-record"));
    public static final java.util.Set<String> MARKDOWN_TYPES = java.util.Set.of("DOCUMENTATION", "FINAL_SUMMARY");

    private ArtifactSchemas() {
    }

    /** Asserts {@code json} conforms to {@code contracts/schemas/<name>.schema.json}. */
    public static void assertValid(String name, String json) {
        List<Error> errors = schema(name).validate(json, InputFormat.JSON);
        assertThat(errors).as("%s violations:%n%s", name,
                errors.stream().map(Object::toString).collect(Collectors.joining(System.lineSeparator()))).isEmpty();
    }

    /**
     * T111: every artifact of a run (current and superseded) validates against its schema. Returns the
     * number of JSON artifacts validated. A JSON artifact type without a schema fails.
     */
    public static int assertRunArtifactsValid(HttpDriver http, String runPath) {
        int validated = 0;
        for (tools.jackson.databind.JsonNode summary : http.get(runPath + "/artifacts", Tokens.AUDITOR)) {
            String type = summary.path("type").asString();
            if (MARKDOWN_TYPES.contains(type)) {
                continue;
            }
            assertThat(SCHEMA_BY_TYPE).as("schema for artifact type " + type).containsKey(type);
            String content = http.get(runPath + "/artifacts/" + summary.path("artifactId").asString(), Tokens.AUDITOR).path("content")
                    .asString();
            assertValid(SCHEMA_BY_TYPE.get(type), content);
            validated++;
        }
        return validated;
    }

    private static Schema schema(String name) {
        return SCHEMAS.computeIfAbsent(name, n -> {
            String location = "/contracts/schemas/" + n + ".schema.json";
            try (InputStream in = ArtifactSchemas.class.getResourceAsStream(location)) {
                if (in == null) {
                    throw new IllegalArgumentException("no schema " + location);
                }
                return REGISTRY.getSchema(in);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }
}
