package com.agentic.urlshortener.orchestration.planning;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.agentic.urlshortener.orchestration.agent.StageContext.ArtifactInput;

/**
 * T093 (FR-RPL-01, FR-RPL-05, review RC-1): an attempt's input fingerprint is deterministic and
 * changes whenever anything the agent depends on changes: the requirement, any input artifact, the
 * agent id or version, or the version of the knowledge the agent reads (catalog, lexicon, policy set).
 */
@Tag("FR-RPL-01")
@Tag("FR-RPL-05")
class InputFingerprinterTest {

    private static ArtifactInput input(String type, String fingerprint) {
        return new ArtifactInput(UUID.randomUUID(), type, 1, fingerprint, "application/json", "{}");
    }

    private static Map<String, ArtifactInput> inputs(String designFingerprint) {
        Map<String, ArtifactInput> inputs = new LinkedHashMap<>();
        inputs.put("TASK_GRAPH", input("TASK_GRAPH", "aaa"));
        inputs.put("DESIGN", input("DESIGN", designFingerprint));
        return inputs;
    }

    private final InputFingerprinter fingerprinter = new InputFingerprinter("knowledge-v1");

    @Test
    void theSameInputsGiveTheSameFingerprintRegardlessOfOrder() {
        Map<String, ArtifactInput> reversed = new LinkedHashMap<>();
        reversed.put("DESIGN", input("DESIGN", "bbb"));
        reversed.put("TASK_GRAPH", input("TASK_GRAPH", "aaa"));

        assertThat(fingerprinter.fingerprint("req-1", inputs("bbb"), "designer@1.0"))
                .isEqualTo(fingerprinter.fingerprint("req-1", reversed, "designer@1.0"))
                .hasSize(64);
    }

    @Test
    void anyChangedDependencyChangesTheFingerprint() {
        String base = fingerprinter.fingerprint("req-1", inputs("bbb"), "designer@1.0");

        assertThat(fingerprinter.fingerprint("req-2", inputs("bbb"), "designer@1.0")).as("requirement").isNotEqualTo(base);
        assertThat(fingerprinter.fingerprint("req-1", inputs("ccc"), "designer@1.0")).as("input artifact").isNotEqualTo(base);
        Map<String, ArtifactInput> more = inputs("bbb");
        more.put("IMPACT_ANALYSIS", input("IMPACT_ANALYSIS", "ddd"));
        assertThat(fingerprinter.fingerprint("req-1", more, "designer@1.0")).as("added input").isNotEqualTo(base);
        assertThat(fingerprinter.fingerprint("req-1", inputs("bbb"), "designer@1.1")).as("agent version").isNotEqualTo(base);
        assertThat(new InputFingerprinter("knowledge-v2").fingerprint("req-1", inputs("bbb"), "designer@1.0"))
                .as("knowledge version").isNotEqualTo(base);
    }

    @Test
    void theKnowledgeVersionCoversTheCatalogTheLexiconAndThePolicySet() {
        String v100 = InputFingerprinter.knowledgeVersion("1.0.0");
        assertThat(v100).isEqualTo(InputFingerprinter.knowledgeVersion("1.0.0")).hasSize(64);
        assertThat(InputFingerprinter.knowledgeVersion("1.1.0")).isNotEqualTo(v100);
    }
}
