package com.agentic.urlshortener.orchestration.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enables the control plane's periodic jobs (gate deadline sweep, retry wake-ups). */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
