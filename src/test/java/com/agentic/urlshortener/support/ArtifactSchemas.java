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

    private ArtifactSchemas() {
    }

    /** Asserts {@code json} conforms to {@code contracts/schemas/<name>.schema.json}. */
    public static void assertValid(String name, String json) {
        List<Error> errors = schema(name).validate(json, InputFormat.JSON);
        assertThat(errors).as("%s violations:%n%s", name,
                errors.stream().map(Object::toString).collect(Collectors.joining(System.lineSeparator()))).isEmpty();
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
