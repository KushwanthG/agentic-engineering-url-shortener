package com.agentic.urlshortener.orchestration.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.agentic.urlshortener.orchestration.domain.ActorType;
import com.agentic.urlshortener.support.IntegrationTest;
import com.agentic.urlshortener.orchestration.domain.AuditRecord;

/** T012: modification, deletion, insertion, and reordering of audit events are detected (SC-006). */
@IntegrationTest
@Tag("FR-AUD-02")
@Tag("NFR-AUD-02")
@Tag("SC-006")
class AuditTamperDetectionTest {

    @Autowired
    private AuditService audit;

    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbc;
    private String chain;

    @BeforeEach
    void appendThreeEvents() {
        jdbc = new JdbcTemplate(dataSource);
        UUID run = UUID.randomUUID();
        chain = run.toString();
        for (String action : new String[] {"RUN_CREATED", "GATE_DECIDED", "RUN_TERMINATED"}) {
            audit.append(new AuditRecord(run, ActorType.HUMAN, "bob", action, "run:" + run, null, null, "OK", "original", null));
        }
        assertThat(audit.verify(chain).valid()).isTrue();
    }

    @Test
    void detectsAModifiedEvent() {
        jdbc.update("UPDATE audit_event SET reason = 'forged' WHERE chain_id = ? AND seq = 2", chain);
        AuditVerification result = audit.verify(chain);
        assertThat(result.valid()).isFalse();
        assertThat(result.firstBrokenSeq()).isEqualTo(2L);
    }

    @Test
    void detectsADeletedMiddleEvent() {
        jdbc.update("DELETE FROM audit_event WHERE chain_id = ? AND seq = 2", chain);
        AuditVerification result = audit.verify(chain);
        assertThat(result.valid()).isFalse();
        assertThat(result.firstBrokenSeq()).isEqualTo(2L);
    }

    @Test
    void detectsATruncatedTail() {
        jdbc.update("DELETE FROM audit_event WHERE chain_id = ? AND seq = 3", chain);
        AuditVerification result = audit.verify(chain);
        assertThat(result.valid()).isFalse();
        assertThat(result.firstBrokenSeq()).isEqualTo(3L);
    }

    @Test
    void detectsAnInsertedForgery() {
        jdbc.update("INSERT INTO audit_event (chain_id, seq, occurred_at, actor_type, actor_id, action, result, reason, prev_hash, hash) "
                + "VALUES (?, 4, CURRENT_TIMESTAMP, 'HUMAN', 'mallory', 'GATE_DECIDED', 'OK', 'forged', ?, ?)",
                chain, "a".repeat(64), "b".repeat(64));
        AuditVerification result = audit.verify(chain);
        assertThat(result.valid()).isFalse();
        assertThat(result.firstBrokenSeq()).isEqualTo(4L);
    }

    @Test
    void detectsReorderedEvents() {
        jdbc.update("UPDATE audit_event SET seq = 99 WHERE chain_id = ? AND seq = 1", chain);
        jdbc.update("UPDATE audit_event SET seq = 1 WHERE chain_id = ? AND seq = 2", chain);
        jdbc.update("UPDATE audit_event SET seq = 2 WHERE chain_id = ? AND seq = 99", chain);
        AuditVerification result = audit.verify(chain);
        assertThat(result.valid()).isFalse();
        assertThat(result.firstBrokenSeq()).isEqualTo(1L);
    }
}
