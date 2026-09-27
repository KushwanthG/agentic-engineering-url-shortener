package com.agentic.urlshortener.orchestration.planning;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.agentic.urlshortener.common.util.Fingerprints;
import com.agentic.urlshortener.orchestration.agent.StageContext.ArtifactInput;
import com.agentic.urlshortener.orchestration.policy.PolicySetLoader;

/**
 * Fingerprint of everything an attempt depends on (FR-RPL-05, review RC-1): the requirement version,
 * the fingerprints of its declared input artifacts, the agent id and version, and the version of the
 * knowledge agents read (capability catalog, ambiguity lexicon, policy set). A re-opened stage whose
 * fingerprint equals that of an earlier successful attempt can reuse its result.
 */
@Component
public class InputFingerprinter {

    private static final String[] KNOWLEDGE = { "/orchestration/capability-catalog.yaml", "/orchestration/ambiguity-lexicon.yaml" };

    private final String knowledgeVersion;

    @Autowired
    public InputFingerprinter(PolicySetLoader policySets) {
        this(knowledgeVersion(policySets.current().version()));
    }

    InputFingerprinter(String knowledgeVersion) {
        this.knowledgeVersion = knowledgeVersion;
    }

    public String fingerprint(String requirementFingerprint, Map<String, ArtifactInput> inputs, String agentId) {
        Map<String, Object> basis = new TreeMap<>();
        basis.put("requirement", requirementFingerprint);
        basis.put("agent", agentId);
        basis.put("knowledge", knowledgeVersion);
        Map<String, String> inputFingerprints = new TreeMap<>();
        inputs.forEach((type, input) -> inputFingerprints.put(type, input.fingerprint()));
        basis.put("inputs", inputFingerprints);
        return Fingerprints.ofValue(basis);
    }

    public String knowledgeVersion() {
        return knowledgeVersion;
    }

    /** SHA-256 over the knowledge resources and the policy-set version. */
    static String knowledgeVersion(String policySetVersion) {
        StringBuilder content = new StringBuilder("policy-set:").append(policySetVersion).append('\n');
        for (String resource : KNOWLEDGE) {
            try (InputStream in = Objects.requireNonNull(InputFingerprinter.class.getResourceAsStream(resource), resource)) {
                content.append(resource).append('\n').append(new String(in.readAllBytes(), StandardCharsets.UTF_8)).append('\n');
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return Fingerprints.sha256(content.toString());
    }
}
