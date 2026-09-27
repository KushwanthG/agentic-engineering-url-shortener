package com.agentic.urlshortener.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import com.agentic.urlshortener.common.util.CanonicalJson;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

/**
 * Drives the running application over real HTTP for the end-to-end scenario and drill tests
 * ({@code RANDOM_PORT}): requests with demo bearer tokens, waits for gates and terminal states, and
 * reads artifacts. Redirects are not followed, so 302 responses can be asserted.
 */
public final class HttpDriver {

    private static final Duration WAIT = Duration.ofSeconds(60);

    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    private final int port;

    public HttpDriver(int port) {
        this.port = port;
    }

    public HttpResponse<String> send(String method, String path, String token, String body) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).timeout(Duration.ofSeconds(20));
        if (token != null) {
            request.header("Authorization", Tokens.bearer(token));
        }
        if (body != null) {
            request.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(body));
        } else {
            request.method(method, HttpRequest.BodyPublishers.noBody());
        }
        try {
            return http.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException(method + " " + path + " failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(method + " " + path + " interrupted", e);
        }
    }

    public JsonNode get(String path, String token) {
        return json(send("GET", path, token, null));
    }

    /** Submits a requirement as the requester; returns the run path {@code /api/v1/workflows/{id}}. */
    public String submit(String requirementJson) {
        HttpResponse<String> response = send("POST", "/api/v1/workflows", Tokens.REQUESTER, requirementJson);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        return "/api/v1/workflows/" + json(response).path("runId").asString();
    }

    public JsonNode run(String runPath) {
        return get(runPath, Tokens.AUDITOR);
    }

    /** Waits until {@code stageKey} is a pending action of the run; fails early if the run ends first. */
    public void awaitPendingAction(String runPath, String stageKey) {
        await().atMost(WAIT).pollInterval(Duration.ofMillis(100)).until(() -> {
            JsonNode run = run(runPath);
            assertThat(run.path("terminalOutcome").isMissingNode())
                    .as("run ended before %s: %s %s", stageKey, run.path("status"), run.path("terminalReason")).isTrue();
            for (JsonNode action : run.path("pendingActions")) {
                if (action.path("stageKey").asString().equals(stageKey)) {
                    return true;
                }
            }
            return false;
        });
    }

    public JsonNode awaitTerminal(String runPath) {
        JsonNode[] last = new JsonNode[1];
        await().atMost(WAIT).pollInterval(Duration.ofMillis(100)).until(() -> {
            last[0] = run(runPath);
            return last[0].path("terminalOutcome").isString();
        });
        return last[0];
    }

    public HttpResponse<String> decideGate(String runPath, String gate, String token, String decision, String rationale) {
        return send("POST", runPath + "/gates/" + gate + "/decision", token,
                CanonicalJson.write(Map.of("decision", decision, "rationale", rationale)));
    }

    public HttpResponse<String> operate(String runPath, String action, String token, String reason) {
        return send("POST", runPath + "/" + action, token, CanonicalJson.write(Map.of("reason", reason)));
    }

    /** Current (not superseded) artifacts by type, with content. */
    public Map<String, JsonNode> currentArtifacts(String runPath) {
        Map<String, JsonNode> current = new LinkedHashMap<>();
        for (JsonNode artifact : get(runPath + "/artifacts", Tokens.AUDITOR)) {
            if (!artifact.path("superseded").asBoolean()) {
                current.put(artifact.path("type").asString(),
                        get(runPath + "/artifacts/" + artifact.path("artifactId").asString(), Tokens.AUDITOR));
            }
        }
        return current;
    }

    /** An artifact's content: parsed JSON for JSON artifacts, a text node otherwise. */
    public static JsonNode content(JsonNode artifactDetail) {
        assertThat(artifactDetail).as("artifact present").isNotNull();
        String content = artifactDetail.path("content").asString();
        return artifactDetail.path("mediaType").asString().equals("application/json")
                ? CanonicalJson.parse(content)
                : JsonNodeFactory.instance.stringNode(content);
    }

    public static JsonNode json(HttpResponse<String> response) {
        return CanonicalJson.parse(response.body());
    }

    /** A fixed scenario input from {@code src/main/resources/scenarios/}. */
    public static String scenario(String name) {
        try (InputStream in = Objects.requireNonNull(HttpDriver.class.getResourceAsStream("/scenarios/" + name))) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The scenario input with simulation options added (demonstration input, labeled simulated). */
    public static String withSimulation(String requirementJson, String simulationJson) {
        String trimmed = requirementJson.trim();
        return trimmed.substring(0, trimmed.lastIndexOf('}')) + ",\"simulation\":" + simulationJson + "}";
    }
}
