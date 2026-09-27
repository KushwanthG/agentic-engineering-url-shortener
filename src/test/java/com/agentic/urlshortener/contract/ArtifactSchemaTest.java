package com.agentic.urlshortener.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.agentic.urlshortener.orchestration.domain.StageType;
import com.agentic.urlshortener.support.ArtifactSchemas;

/**
 * T111 (FR-ORC-10, NFR-CHG-01): the artifact-schema mapping used by the scenario tests is complete
 * and meaningful. Every artifact type a stage can output is either Markdown or mapped to a contract
 * schema that exists. Every schema file is used. Every schema rejects an empty document, so a
 * passing scenario validation is not vacuous. The validation of every artifact of the SCN-A,
 * SCN-B, and SCN-C runs happens in those end-to-end tests ({@code assertRunArtifactsValid}).
 */
@Tag("FR-ORC-10")
@Tag("NFR-CHG-01")
class ArtifactSchemaTest {

    private static final Path SCHEMAS = Path.of("specs", "001-agentic-url-shortener", "contracts", "schemas");

    @Test
    void everyStageOutputIsMarkdownOrHasAContractSchema() {
        Set<String> outputs = new TreeSet<>();
        Arrays.stream(StageType.values()).forEach(s -> outputs.addAll(s.outputArtifactTypes()));
        for (String type : outputs) {
            assertThat(ArtifactSchemas.MARKDOWN_TYPES.contains(type) || ArtifactSchemas.SCHEMA_BY_TYPE.containsKey(type))
                    .as("artifact type %s has a schema or is Markdown", type).isTrue();
        }
        // CLARIFICATION_REQUEST is produced by requirement analysis in addition to its declared output
        assertThat(ArtifactSchemas.SCHEMA_BY_TYPE).containsKey("CLARIFICATION_REQUEST");
    }

    @Test
    void everySchemaFileIsMappedAndEveryMappedSchemaExists() throws Exception {
        Set<String> files = new TreeSet<>();
        try (var list = Files.list(SCHEMAS)) {
            list.map(p -> p.getFileName().toString().replace(".schema.json", "")).forEach(files::add);
        }
        assertThat(new TreeSet<>(ArtifactSchemas.SCHEMA_BY_TYPE.values())).isEqualTo(files);
    }

    @Test
    void everySchemaRejectsAnEmptyDocument() {
        for (String schema : new TreeSet<>(ArtifactSchemas.SCHEMA_BY_TYPE.values())) {
            assertThatThrownBy(() -> ArtifactSchemas.assertValid(schema, "{}")).as(schema).isInstanceOf(AssertionError.class);
        }
    }
}
