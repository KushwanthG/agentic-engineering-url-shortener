package com.agentic.urlshortener.orchestration.domain;

/** Who performed an action recorded in audit events and decisions. */
public enum ActorType {
    HUMAN,
    AGENT,
    SYSTEM
}
