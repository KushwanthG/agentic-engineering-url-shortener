package com.agentic.urlshortener.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import com.agentic.urlshortener.support.IntegrationTest;

/**
 * T110 (NFR-CHG-01): no drift between the running API and {@code openapi.yaml}. Every controller
 * mapping of the application appears in the contract, and every contract operation is implemented.
 * Path variables are compared by position; regular-expression constraints are ignored.
 * Framework endpoints (error, actuator) are outside the contract.
 */
@IntegrationTest
@Tag("NFR-CHG-01")
class ContractDriftTest {

    private static final Set<String> HTTP_METHODS = Set.of("get", "post", "put", "patch", "delete");

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping mappings;

    private static String normalize(String path) {
        return path.replaceAll("\\{[^/]*}", "{}");
    }

    private Set<String> implemented() {
        Set<String> operations = new TreeSet<>();
        for (Map.Entry<RequestMappingInfo, ?> entry : mappings.getHandlerMethods().entrySet()) {
            RequestMappingInfo info = entry.getKey();
            if (!entry.getValue().toString().startsWith("com.agentic.urlshortener")) {
                continue;
            }
            Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
            for (String pattern : info.getPatternValues()) {
                for (RequestMethod method : methods) {
                    operations.add(method.name() + " " + normalize(pattern));
                }
            }
        }
        return operations;
    }

    @SuppressWarnings("unchecked")
    private static Set<String> contracted() throws IOException {
        Set<String> operations = new TreeSet<>();
        try (InputStream in = ContractDriftTest.class.getResourceAsStream("/contracts/openapi.yaml")) {
            assertThat(in).as("contract on the classpath").isNotNull();
            Map<String, Object> spec = new Yaml(new SafeConstructor(new LoaderOptions())).load(in);
            Map<String, Map<String, Object>> paths = (Map<String, Map<String, Object>>) spec.get("paths");
            paths.forEach((path, item) -> item.keySet().stream().filter(HTTP_METHODS::contains)
                    .forEach(method -> operations.add(method.toUpperCase() + " " + normalize(path))));
        }
        return operations;
    }

    @Test
    void everyControllerMappingIsInTheContract() throws IOException {
        Set<String> undocumented = new TreeSet<>(implemented());
        undocumented.removeAll(contracted());
        assertThat(undocumented).as("implemented but missing from openapi.yaml").isEmpty();
    }

    @Test
    void everyContractOperationIsImplemented() throws IOException {
        Set<String> missing = new TreeSet<>(contracted());
        missing.removeAll(implemented());
        assertThat(missing).as("in openapi.yaml but not implemented").isEmpty();
        assertThat(contracted()).as("contract operations parsed (29 operationIds)").hasSize(29);
    }
}
