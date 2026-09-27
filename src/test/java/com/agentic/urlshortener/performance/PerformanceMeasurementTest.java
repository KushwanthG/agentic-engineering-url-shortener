package com.agentic.urlshortener.performance;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

import com.agentic.urlshortener.common.util.CanonicalJson;
import com.agentic.urlshortener.support.EvidenceExporter;
import com.agentic.urlshortener.support.Tokens;

/**
 * T113 (NFR-PRF-01): a labeled demonstration measurement of redirect and creation latency. It runs
 * 20 concurrent clients over real HTTP against the running application (test profile, in-memory
 * H2, one machine), after a warm-up. Only a generous regression bound (10× the targets) is asserted,
 * because timings vary between machines. Whether the PVT-19 and PVT-20 targets were met is recorded
 * in the exported evidence ({@code target/evidence/performance/}) and in
 * docs/assessment/performance.md.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Tag("NFR-PRF-01")
class PerformanceMeasurementTest {

    private static final int CLIENTS = 20;
    private static final int CREATIONS_PER_CLIENT = 20;
    private static final int REDIRECTS_PER_CLIENT = 50;
    private static final long REDIRECT_TARGET_MS = 50;
    private static final long CREATION_TARGET_MS = 150;

    @Autowired private Environment environment;

    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    private String base() {
        return "http://localhost:" + Objects.requireNonNull(environment.getProperty("local.server.port"));
    }

    private HttpResponse<String> create(String url) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(base() + "/api/v1/links"))
                .header("Authorization", Tokens.bearer(Tokens.CONSUMER)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"url\":\"" + url + "\"}")).build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> resolve(String code) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(base() + "/" + code)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private static long timed(Callable<HttpResponse<String>> call, int expectedStatus) throws Exception {
        long start = System.nanoTime();
        HttpResponse<String> response = call.call();
        long micros = (System.nanoTime() - start) / 1_000;
        assertThat(response.statusCode()).as(response.body()).isEqualTo(expectedStatus);
        return micros;
    }

    private static List<Long> concurrently(int clients, Callable<List<Long>> perClient) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(clients);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<List<Long>>> futures = new ArrayList<>();
            for (int i = 0; i < clients; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return perClient.call();
                }));
            }
            start.countDown();
            List<Long> all = new ArrayList<>();
            for (Future<List<Long>> future : futures) {
                all.addAll(future.get());
            }
            Collections.sort(all);
            return all;
        } finally {
            pool.shutdownNow();
        }
    }

    private static long percentileMillis(List<Long> sortedMicros, double p) {
        int index = Math.max(0, (int) Math.ceil(p * sortedMicros.size()) - 1);
        return Math.round(sortedMicros.get(index) / 1000.0);
    }

    private static Map<String, Object> summary(List<Long> sortedMicros, long targetMs) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("samples", sortedMicros.size());
        summary.put("p50Millis", percentileMillis(sortedMicros, 0.50));
        summary.put("p95Millis", percentileMillis(sortedMicros, 0.95));
        summary.put("p99Millis", percentileMillis(sortedMicros, 0.99));
        summary.put("maxMillis", Math.round(sortedMicros.getLast() / 1000.0));
        summary.put("targetP95Millis", targetMs);
        summary.put("targetMet", percentileMillis(sortedMicros, 0.95) <= targetMs);
        return summary;
    }

    @Test
    void redirectAndCreationLatencyUnderTwentyConcurrentClients() throws Exception {
        List<String> codes = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            HttpResponse<String> created = create("https://www.example.com/perf/warmup/" + i);
            String code = CanonicalJson.parse(created.body()).path("code").asString();
            codes.add(code);
            resolve(code);
        }

        List<Long> creations = concurrently(CLIENTS, () -> {
            List<Long> samples = new ArrayList<>();
            String client = Thread.currentThread().getName();
            for (int i = 0; i < CREATIONS_PER_CLIENT; i++) {
                String url = "https://www.example.com/perf/" + client + "/" + i;
                samples.add(timed(() -> create(url), 201));
            }
            return samples;
        });
        List<Long> redirects = concurrently(CLIENTS, () -> {
            List<Long> samples = new ArrayList<>();
            for (int i = 0; i < REDIRECTS_PER_CLIENT; i++) {
                String code = codes.get(i % codes.size());
                samples.add(timed(() -> resolve(code), 302));
            }
            return samples;
        });

        Map<String, Object> redirect = summary(redirects, REDIRECT_TARGET_MS);
        Map<String, Object> creation = summary(creations, CREATION_TARGET_MS);
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("label", "DEMONSTRATION MEASUREMENT - one local machine, not a production benchmark");
        evidence.put("loadProfile", Map.of("concurrentClients", CLIENTS, "creationsPerClient", CREATIONS_PER_CLIENT,
                "redirectsPerClient", REDIRECTS_PER_CLIENT, "warmupRequests", 100, "database", "in-memory H2 (test profile)",
                "transport", "HTTP/1.1 over loopback, java.net.http.HttpClient"));
        evidence.put("machine", Map.of("availableProcessors", Runtime.getRuntime().availableProcessors(),
                "os", System.getProperty("os.name") + " " + System.getProperty("os.version"), "jdk", System.getProperty("java.version"),
                "maxHeapMb", Runtime.getRuntime().maxMemory() / (1024 * 1024)));
        evidence.put("redirect", redirect);
        evidence.put("creation", creation);
        new EvidenceExporter("performance", PerformanceMeasurementTest.class, false).json("performance-measurement", evidence);

        assertThat(redirects).hasSize(CLIENTS * REDIRECTS_PER_CLIENT);
        assertThat(creations).hasSize(CLIENTS * CREATIONS_PER_CLIENT);
        assertThat(percentileMillis(redirects, 0.95)).as("redirect p95 regression bound (10x PVT-19)").isLessThan(10 * REDIRECT_TARGET_MS);
        assertThat(percentileMillis(creations, 0.95)).as("creation p95 regression bound (10x PVT-20)").isLessThan(10 * CREATION_TARGET_MS);
    }
}
