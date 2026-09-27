package com.agentic.urlshortener.support;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.agentic.urlshortener.shortener.domain.SecureRandomShortCodeGenerator;
import com.agentic.urlshortener.shortener.domain.ShortCodeGenerator;

/** Replaces the clock and the short-code generator with controllable versions (deterministic tests). */
@TestConfiguration
public class ControllableTestConfig {

    @Bean
    @Primary
    public MutableClock mutableClock() {
        return new MutableClock(Instant.parse("2026-09-26T10:00:00Z"));
    }

    @Bean
    @Primary
    public ScriptedShortCodeGenerator scriptedShortCodeGenerator() {
        return new ScriptedShortCodeGenerator();
    }

    /** Returns queued codes first (to force collisions), then random codes. */
    public static class ScriptedShortCodeGenerator implements ShortCodeGenerator {

        private final Deque<String> queue = new ArrayDeque<>();
        private final ShortCodeGenerator fallback = new SecureRandomShortCodeGenerator(7);

        public synchronized void enqueue(String... codes) {
            for (String code : codes) {
                queue.addLast(code);
            }
        }

        public synchronized void clear() {
            queue.clear();
        }

        @Override
        public synchronized String next() {
            return queue.isEmpty() ? fallback.next() : queue.removeFirst();
        }
    }
}
