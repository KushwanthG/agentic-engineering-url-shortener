package com.agentic.urlshortener.orchestration.agent;

import com.agentic.urlshortener.orchestration.domain.Artifact;

/** An artifact produced by an agent; the engine versions, fingerprints, and stores it. */
public record ArtifactDraft(String type, String mediaType, String content) {

    public static ArtifactDraft json(String type, String content) {
        return new ArtifactDraft(type, Artifact.JSON, content);
    }

    public static ArtifactDraft markdown(String type, String content) {
        return new ArtifactDraft(type, Artifact.MARKDOWN, content);
    }
}
