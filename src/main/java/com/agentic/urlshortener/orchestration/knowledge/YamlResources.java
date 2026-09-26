package com.agentic.urlshortener.orchestration.knowledge;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

import org.springframework.core.io.ClassPathResource;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import com.agentic.urlshortener.common.util.CanonicalJson;

/** Reads a classpath YAML file with the safe constructor and binds it to a record through canonical JSON. */
final class YamlResources {

    private YamlResources() {
    }

    static <T> T read(String location, Class<T> type) {
        try (InputStream in = new ClassPathResource(location).getInputStream()) {
            Object tree = new Yaml(new SafeConstructor(new LoaderOptions())).load(in);
            return CanonicalJson.read(CanonicalJson.write(tree), type);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + location, e);
        }
    }
}
