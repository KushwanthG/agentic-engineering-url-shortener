package com.agentic.urlshortener;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Agentic SDLC System — URL Shortener. One deployable with two planes separated by a port
 * (ADR-001): the URL shortener (application plane) and the governed orchestration engine
 * (control plane).
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class UrlShortenerApplication {

    public static void main(String[] args) {
        SpringApplication.run(UrlShortenerApplication.class, args);
    }
}
