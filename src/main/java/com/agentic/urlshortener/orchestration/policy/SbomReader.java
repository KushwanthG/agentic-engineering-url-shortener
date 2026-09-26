package com.agentic.urlshortener.orchestration.policy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.core.io.ClassPathResource;

import com.agentic.urlshortener.common.util.CanonicalJson;

import tools.jackson.databind.JsonNode;

/** Reads the CycloneDX SBOM the build generates ({@code classpath:META-INF/sbom/application.cdx.json}). */
public final class SbomReader {

    private static final String LOCATION = "META-INF/sbom/application.cdx.json";

    /** One component with its declared license ids, names, or expressions. */
    public record Component(String name, List<String> licenses) {
    }

    private SbomReader() {
    }

    /** The components, or empty when the SBOM is missing (for example when running from an IDE without a build). */
    public static Optional<List<Component>> read() {
        ClassPathResource resource = new ClassPathResource(LOCATION);
        if (!resource.exists()) {
            return Optional.empty();
        }
        try (InputStream in = resource.getInputStream()) {
            JsonNode root = CanonicalJson.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            List<Component> components = new ArrayList<>();
            for (JsonNode component : root.path("components")) {
                List<String> licenses = new ArrayList<>();
                for (JsonNode entry : component.path("licenses")) {
                    JsonNode license = entry.path("license");
                    if (license.hasNonNull("id")) {
                        licenses.add(license.path("id").asString());
                    } else if (license.hasNonNull("name")) {
                        licenses.add(license.path("name").asString());
                    } else if (entry.hasNonNull("expression")) {
                        licenses.add(entry.path("expression").asString());
                    }
                }
                String group = component.path("group").asString("");
                components.add(new Component((group.isEmpty() ? "" : group + ":") + component.path("name").asString(), licenses));
            }
            return Optional.of(components);
        } catch (IOException e) {
            return Optional.empty();
        }
    }
}
