package com.agentic.urlshortener.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MvcResult;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.model.Request;
import com.atlassian.oai.validator.model.SimpleResponse;
import com.atlassian.oai.validator.report.ValidationReport;

/**
 * Executable contract validation: recorded MockMvc responses are validated against the
 * contract-first {@code openapi.yaml} (copied to {@code classpath:contracts/} by the build).
 */
public final class OpenApiContract {

    private static final OpenApiInteractionValidator VALIDATOR = OpenApiInteractionValidator
            .createForSpecificationUrl(specificationUrl())
            // The validator forbids undeclared properties per sub-schema; merge allOf compositions
            // (e.g. ArtifactDetail = ArtifactSummary + detail fields) so strictness applies to the whole.
            .withResolveCombinators(true)
            .build();

    private OpenApiContract() {
    }

    private static String specificationUrl() {
        URL url = Objects.requireNonNull(OpenApiContract.class.getResource("/contracts/openapi.yaml"),
                "contracts/openapi.yaml must be on the test classpath");
        return url.toString();
    }

    /** Validates status, documented headers, content type, and body of the response for {@code method path}. */
    public static void assertResponseMatches(String method, String path, MvcResult result) throws Exception {
        ValidationReport report = validate(method, path, result.getResponse());
        assertThat(report.hasErrors())
                .as("Contract violations for %s %s (status %d):%n%s", method, path, result.getResponse().getStatus(), describe(report))
                .isFalse();
    }

    static ValidationReport validate(String method, String path, MockHttpServletResponse response) throws Exception {
        SimpleResponse.Builder builder = SimpleResponse.Builder.status(response.getStatus());
        if (response.getContentType() != null) {
            builder.withContentType(response.getContentType());
        }
        String body = response.getContentAsString();
        if (!body.isEmpty()) {
            builder.withBody(body);
        }
        for (String name : response.getHeaderNames()) {
            Collection<String> values = response.getHeaders(name);
            builder.withHeader(name, List.copyOf(values));
        }
        return VALIDATOR.validateResponse(path, Request.Method.valueOf(method), builder.build());
    }

    private static String describe(ValidationReport report) {
        return report.getMessages().stream()
                .map(message -> "  - [" + message.getLevel() + "] " + message.getKey() + ": " + message.getMessage())
                .collect(Collectors.joining(System.lineSeparator()));
    }
}
