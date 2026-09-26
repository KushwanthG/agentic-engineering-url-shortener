package com.agentic.urlshortener.orchestration.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.agentic.urlshortener.orchestration.config.OrchestrationProperties;
import com.agentic.urlshortener.support.AgentChain;
import com.agentic.urlshortener.support.RequirementFixtures;

/** T050: generated documentation (changelog, capability text, runbook) plus a check of the repository docs. */
@Tag("FR-ORC-03")
@Tag("SCN-A")
class DocumentationAgentTest {

    private static OrchestrationProperties root(String codebaseRoot) {
        return new OrchestrationProperties(codebaseRoot, 365, null, 3, 8, null, null);
    }

    private static AgentChain designed() {
        return AgentChain.analyzed(RequirementFixtures.gf001())
                .then(new DecompositionAgent(AgentChain.CATALOG))
                .then(new ThreatAssessmentAgent(AgentChain.CATALOG))
                .then(new DesignAgent(AgentChain.CATALOG));
    }

    @Test
    void generatesChangelogCapabilityTextAndRunbookAndFindsTheRepositoryDocs() {
        String doc = designed().then(new DocumentationAgent(AgentChain.CATALOG, root("."))).artifact("DOCUMENTATION");
        assertThat(doc).contains("# Custom aliases").contains("API changelog").contains("1.1.0").contains("POST /api/v1/links")
                .contains("ALIAS_CONFLICT").contains("Runbook").contains("Withdraw").contains("AC-1");
        assertThat(DocumentationAgent.repositoryDocsUpdated(doc)).isTrue();
        assertThat(doc).contains("docs/api/links.md");
    }

    @Test
    void reportsRepositoryDocsThatDoNotMentionTheCapability(@TempDir Path emptyRepository) {
        String doc = designed().then(new DocumentationAgent(AgentChain.CATALOG, root(emptyRepository.toString()))).artifact("DOCUMENTATION");
        assertThat(DocumentationAgent.repositoryDocsUpdated(doc)).isFalse();
        assertThat(DocumentationAgent.isDegraded(doc)).isFalse();
    }
}
