package com.agentic.urlshortener.orchestration.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.agentic.urlshortener.orchestration.domain.Artifact;

public interface ArtifactRepository extends JpaRepository<Artifact, UUID> {

    List<Artifact> findByRunIdOrderByCreatedAtAsc(UUID runId);

    /** The current (not superseded) artifacts of a run, one per type. */
    List<Artifact> findByRunIdAndSupersededFalseOrderByCreatedAtAsc(UUID runId);

    Optional<Artifact> findFirstByRunIdAndArtifactTypeAndSupersededFalseOrderByVersionDesc(UUID runId, String artifactType);

    Optional<Artifact> findByIdAndRunId(UUID id, UUID runId);

    @Query("select coalesce(max(a.version), 0) from Artifact a where a.runId = :runId and a.artifactType = :type")
    int maxVersion(@Param("runId") UUID runId, @Param("type") String type);
}
