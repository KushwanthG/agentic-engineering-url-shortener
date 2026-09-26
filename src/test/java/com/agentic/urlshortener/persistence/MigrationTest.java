package com.agentic.urlshortener.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** T007: the Flyway baseline creates the persistence schema of data-model.md with its constraints. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Tag("FR-ORC-08")
@Tag("NFR-CHG-01")
@Tag("FR-LNK-05")
class MigrationTest {

    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbc() {
        return new JdbcTemplate(dataSource);
    }

    @Test
    void createsEveryTableOfTheDataModel() {
        List<String> tables = jdbc().queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'", String.class);
        assertThat(tables).contains(
                "short_link", "click_event", "idempotency_record", "capability_release",
                "workflow_run", "requirement_version", "plan_version", "stage_node", "stage_attempt",
                "artifact", "decision", "change_request", "policy_evaluation", "policy_exception",
                "audit_event", "audit_chain_head", "failure_event");
    }

    @Test
    void appliesTheBaselineMigrationsInOrder() {
        List<String> versions = jdbc().queryForList(
                "SELECT \"version\" FROM \"flyway_schema_history\" WHERE \"success\" = TRUE AND \"version\" IS NOT NULL "
                        + "ORDER BY \"installed_rank\"",
                String.class);
        assertThat(versions).startsWith("1", "2");
    }

    @Test
    void shortCodesAreUnique() {
        insertLink("aB3dE9x");
        assertThatThrownBy(() -> insertLink("aB3dE9x")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void idempotencyKeysAreUniquePerConsumer() {
        insertIdempotency("consumer-a", "key-1");
        insertIdempotency("consumer-b", "key-1");
        assertThatThrownBy(() -> insertIdempotency("consumer-a", "key-1"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void auditSequenceIsUniquePerChain() {
        insertAudit("chain-1", 1);
        insertAudit("chain-2", 1);
        assertThatThrownBy(() -> insertAudit("chain-1", 1)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void stageKeysAreUniquePerRun() {
        UUID run = UUID.randomUUID();
        insertRun(run);
        insertStage(run, "DESIGN");
        assertThatThrownBy(() -> insertStage(run, "DESIGN")).isInstanceOf(DataIntegrityViolationException.class);
    }

    private void insertLink(String code) {
        jdbc().update("INSERT INTO short_link (code, target_url, created_by, created_at) VALUES (?, ?, ?, CURRENT_TIMESTAMP)",
                code, "https://example.com/", "consumer");
    }

    private void insertIdempotency(String consumer, String key) {
        jdbc().update("INSERT INTO idempotency_record (consumer_id, idem_key, request_fingerprint, response_status, response_body, created_at, expires_at) "
                + "VALUES (?, ?, ?, 201, '{}', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", consumer, key, "f".repeat(64));
    }

    private void insertAudit(String chain, long seq) {
        jdbc().update("INSERT INTO audit_event (chain_id, seq, occurred_at, actor_type, actor_id, action, result, prev_hash, hash) "
                + "VALUES (?, ?, CURRENT_TIMESTAMP, 'SYSTEM', 'test', 'TEST', 'OK', ?, ?)", chain, seq, "0".repeat(64), "1".repeat(64));
    }

    private void insertRun(UUID run) {
        jdbc().update("INSERT INTO workflow_run (id, title, requested_by, classification, status, current_plan_version, "
                + "current_requirement_version, policy_set_version, manual_intervention_required, clarification_rounds, attempts_used, "
                + "processing_millis, created_at, updated_at, version) VALUES (?, 't', 'alice', 'UNDETERMINED', 'CREATED', 1, 1, '1.0.0', "
                + "FALSE, 0, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)", run);
    }

    private void insertStage(UUID run, String key) {
        jdbc().update("INSERT INTO stage_node (id, run_id, stage_key, stage_type, status, depends_on, generation, attempts, "
                + "reused, degraded, version) VALUES (?, ?, ?, ?, 'PENDING', '', 1, 0, FALSE, FALSE, 0)",
                UUID.randomUUID(), run, key, key);
    }
}
