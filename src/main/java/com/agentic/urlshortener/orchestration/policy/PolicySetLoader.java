package com.agentic.urlshortener.orchestration.policy;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/** Loads {@code classpath:orchestration/policy-set.yaml} once at startup (safe YAML: plain maps and lists only). */
@Component
public class PolicySetLoader {

    private static final String LOCATION = "orchestration/policy-set.yaml";

    private final PolicySet current;

    public PolicySetLoader() {
        this.current = load(LOCATION);
    }

    public PolicySet current() {
        return current;
    }

    @SuppressWarnings("unchecked")
    static PolicySet load(String location) {
        try (InputStream in = new ClassPathResource(location).getInputStream()) {
            Map<String, Object> root = new Yaml(new SafeConstructor(new LoaderOptions())).load(in);
            List<Map<String, Object>> policies = (List<Map<String, Object>>) root.get("policies");
            return new PolicySet(String.valueOf(root.get("version")), policies.stream()
                    .map(p -> new PolicySet.PolicyDefinition(str(p, "id"), str(p, "title"), str(p, "domain"), str(p, "severity"),
                            str(p, "appliesWhen"), str(p, "description")))
                    .toList());
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + location, e);
        }
    }

    private static String str(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value == null ? null : value.toString();
    }
}
