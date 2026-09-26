package com.agentic.urlshortener.common.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Canonical JSON: object keys sorted recursively, no insignificant whitespace. Two documents with
 * the same content therefore produce the same text and the same fingerprint, which artifact
 * provenance, approval binding (ADR-008), and replanning reuse (ADR-011) rely on.
 */
public final class CanonicalJson {

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .build();

    private CanonicalJson() {
    }

    /** Re-serializes a JSON document in canonical form. */
    public static String canonicalize(String json) {
        return MAPPER.writeValueAsString(sorted(MAPPER.readTree(json)));
    }

    /** Serializes any value (records, maps, lists) in canonical form. */
    public static String write(Object value) {
        JsonNode tree = MAPPER.valueToTree(value);
        return MAPPER.writeValueAsString(sorted(tree));
    }

    /** Parses canonical or non-canonical JSON into a tree. */
    public static JsonNode parse(String json) {
        return MAPPER.readTree(json);
    }

    /** Converts a JSON document into an instance of {@code type}. */
    public static <T> T read(String json, Class<T> type) {
        return MAPPER.readValue(json, type);
    }

    private static JsonNode sorted(JsonNode node) {
        if (node.isObject()) {
            List<String> names = new ArrayList<>();
            for (Map.Entry<String, JsonNode> property : node.properties()) {
                names.add(property.getKey());
            }
            names.sort(String::compareTo);
            ObjectNode result = JsonNodeFactory.instance.objectNode();
            for (String name : names) {
                result.set(name, sorted(node.get(name)));
            }
            return result;
        }
        if (node.isArray()) {
            ArrayNode result = JsonNodeFactory.instance.arrayNode();
            for (JsonNode element : node) {
                result.add(sorted(element));
            }
            return result;
        }
        return node;
    }
}
