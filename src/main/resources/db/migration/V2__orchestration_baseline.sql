-- V2: control-plane baseline (data-model.md, "Control plane").

CREATE TABLE workflow_run (
    id                           UUID PRIMARY KEY,
    requirement_ref              VARCHAR(64),
    title                        VARCHAR(200)  NOT NULL,
    requested_by                 VARCHAR(64)   NOT NULL,
    classification               VARCHAR(32)   NOT NULL,
    status                       VARCHAR(32)   NOT NULL,
    current_plan_version         INTEGER       NOT NULL,
    current_requirement_version  INTEGER       NOT NULL,
    policy_set_version           VARCHAR(20)   NOT NULL,
    readiness                    VARCHAR(40),
    terminal_outcome             VARCHAR(32),
    terminal_reason              VARCHAR(1000),
    manual_intervention_required BOOLEAN       NOT NULL DEFAULT FALSE,
    clarification_rounds         INTEGER       NOT NULL DEFAULT 0,
    attempts_used                INTEGER       NOT NULL DEFAULT 0,
    processing_millis            BIGINT        NOT NULL DEFAULT 0,
    fault_plan                   VARCHAR(10000),
    gate_deadline_seconds        BIGINT,
    created_at                   TIMESTAMP WITH TIME ZONE NOT NULL,
    started_at                   TIMESTAMP WITH TIME ZONE,
    updated_at                   TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at                 TIMESTAMP WITH TIME ZONE,
    version                      BIGINT        NOT NULL DEFAULT 0
);

CREATE INDEX idx_workflow_run_status ON workflow_run (status);
CREATE INDEX idx_workflow_run_created ON workflow_run (created_at);

CREATE TABLE requirement_version (
    id           UUID PRIMARY KEY,
    run_id       UUID             NOT NULL,
    version      INTEGER          NOT NULL,
    source       VARCHAR(32)      NOT NULL,
    content      VARCHAR(1000000) NOT NULL,
    fingerprint  VARCHAR(64)      NOT NULL,
    created_by   VARCHAR(64)      NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    decision_id  UUID,
    CONSTRAINT fk_requirement_run FOREIGN KEY (run_id) REFERENCES workflow_run (id),
    CONSTRAINT uk_requirement_version UNIQUE (run_id, version)
);

CREATE TABLE plan_version (
    id            UUID PRIMARY KEY,
    run_id        UUID             NOT NULL,
    version       INTEGER          NOT NULL,
    graph         VARCHAR(1000000) NOT NULL,
    diff          VARCHAR(1000000),
    trigger_type  VARCHAR(40)      NOT NULL,
    reason        VARCHAR(1000)    NOT NULL,
    created_by    VARCHAR(64)      NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_plan_run FOREIGN KEY (run_id) REFERENCES workflow_run (id),
    CONSTRAINT uk_plan_version UNIQUE (run_id, version)
);

CREATE TABLE stage_node (
    id                   UUID PRIMARY KEY,
    run_id               UUID          NOT NULL,
    stage_key            VARCHAR(40)   NOT NULL,
    stage_type           VARCHAR(40)   NOT NULL,
    status               VARCHAR(32)   NOT NULL,
    awaiting             VARCHAR(32),
    depends_on           VARCHAR(1000) NOT NULL,
    generation           INTEGER       NOT NULL,
    attempts             INTEGER       NOT NULL DEFAULT 0,
    input_fingerprint    VARCHAR(64),
    reused               BOOLEAN       NOT NULL DEFAULT FALSE,
    degraded             BOOLEAN       NOT NULL DEFAULT FALSE,
    skip_reason          VARCHAR(500),
    next_attempt_at      TIMESTAMP WITH TIME ZONE,
    decision_deadline    TIMESTAMP WITH TIME ZONE,
    last_failure_class   VARCHAR(16),
    last_failure_reason  VARCHAR(1000),
    started_at           TIMESTAMP WITH TIME ZONE,
    finished_at          TIMESTAMP WITH TIME ZONE,
    version              BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT fk_stage_run FOREIGN KEY (run_id) REFERENCES workflow_run (id),
    CONSTRAINT uk_stage_run_key UNIQUE (run_id, stage_key)
);

CREATE TABLE stage_attempt (
    id                 UUID PRIMARY KEY,
    run_id             UUID          NOT NULL,
    stage_key          VARCHAR(40)   NOT NULL,
    generation         INTEGER       NOT NULL,
    attempt_no         INTEGER       NOT NULL,
    agent_id           VARCHAR(80)   NOT NULL,
    fallback           BOOLEAN       NOT NULL DEFAULT FALSE,
    simulated_fault    VARCHAR(40),
    scheduling_cycle   BIGINT,
    started_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    finished_at        TIMESTAMP WITH TIME ZONE,
    outcome            VARCHAR(32),
    failure_class      VARCHAR(16),
    error              VARCHAR(2000),
    input_fingerprint  VARCHAR(64),
    CONSTRAINT fk_attempt_run FOREIGN KEY (run_id) REFERENCES workflow_run (id)
);

CREATE INDEX idx_attempt_run_stage ON stage_attempt (run_id, stage_key);

CREATE TABLE artifact (
    id             UUID PRIMARY KEY,
    run_id         UUID             NOT NULL,
    stage_key      VARCHAR(40)      NOT NULL,
    generation     INTEGER          NOT NULL,
    attempt_no     INTEGER          NOT NULL,
    artifact_type  VARCHAR(40)      NOT NULL,
    version        INTEGER          NOT NULL,
    media_type     VARCHAR(40)      NOT NULL,
    content        VARCHAR(1000000) NOT NULL,
    fingerprint    VARCHAR(64)      NOT NULL,
    produced_by    VARCHAR(80)      NOT NULL,
    input_refs     VARCHAR(100000)  NOT NULL,
    superseded     BOOLEAN          NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_artifact_run FOREIGN KEY (run_id) REFERENCES workflow_run (id),
    CONSTRAINT uk_artifact_version UNIQUE (run_id, artifact_type, version)
);

CREATE TABLE decision (
    id                  UUID PRIMARY KEY,
    run_id              UUID            NOT NULL,
    stage_key           VARCHAR(40),
    decision_type       VARCHAR(40)     NOT NULL,
    outcome             VARCHAR(40)     NOT NULL,
    actor_type          VARCHAR(16)     NOT NULL,
    actor_id            VARCHAR(64)     NOT NULL,
    actor_role          VARCHAR(32),
    rationale           VARCHAR(2000),
    payload             VARCHAR(100000),
    bound_fingerprints  VARCHAR(10000),
    valid               BOOLEAN         NOT NULL DEFAULT TRUE,
    invalidated_reason  VARCHAR(500),
    supersedes          UUID,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_decision_run FOREIGN KEY (run_id) REFERENCES workflow_run (id)
);

CREATE INDEX idx_decision_run ON decision (run_id);

CREATE TABLE change_request (
    id                   UUID PRIMARY KEY,
    run_id               UUID             NOT NULL,
    requested_by         VARCHAR(64)      NOT NULL,
    requested_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    reason               VARCHAR(1000)    NOT NULL,
    amended_requirement  VARCHAR(1000000) NOT NULL,
    material             BOOLEAN          NOT NULL,
    impact               VARCHAR(100000)  NOT NULL,
    status               VARCHAR(20)      NOT NULL,
    decided_by           VARCHAR(64),
    decided_at           TIMESTAMP WITH TIME ZONE,
    decision_rationale   VARCHAR(2000),
    CONSTRAINT fk_change_request_run FOREIGN KEY (run_id) REFERENCES workflow_run (id)
);

CREATE TABLE policy_evaluation (
    id                  UUID PRIMARY KEY,
    run_id              UUID          NOT NULL,
    stage_key           VARCHAR(40)   NOT NULL,
    generation          INTEGER       NOT NULL,
    policy_id           VARCHAR(20)   NOT NULL,
    policy_set_version  VARCHAR(20)   NOT NULL,
    severity            VARCHAR(12)   NOT NULL,
    outcome             VARCHAR(24)   NOT NULL,
    evidence            VARCHAR(2000) NOT NULL,
    exception_id        UUID,
    simulated           BOOLEAN       NOT NULL DEFAULT FALSE,
    evaluated_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_policy_evaluation_run FOREIGN KEY (run_id) REFERENCES workflow_run (id)
);

CREATE INDEX idx_policy_evaluation_run ON policy_evaluation (run_id);

CREATE TABLE policy_exception (
    id                    UUID PRIMARY KEY,
    run_id                UUID          NOT NULL,
    policy_id             VARCHAR(20)   NOT NULL,
    reason                VARCHAR(1000) NOT NULL,
    scope                 VARCHAR(500)  NOT NULL,
    compensating_control  VARCHAR(1000) NOT NULL,
    requested_by          VARCHAR(64)   NOT NULL,
    requested_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at            TIMESTAMP WITH TIME ZONE NOT NULL,
    status                VARCHAR(16)   NOT NULL,
    decided_by            VARCHAR(64),
    decided_at            TIMESTAMP WITH TIME ZONE,
    decision_rationale    VARCHAR(2000),
    CONSTRAINT fk_policy_exception_run FOREIGN KEY (run_id) REFERENCES workflow_run (id)
);

-- Insert-only audit trail; no foreign key so that events can be recorded for any chain (runs and GLOBAL).
CREATE TABLE audit_event (
    id           BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    chain_id     VARCHAR(64)      NOT NULL,
    seq          BIGINT           NOT NULL,
    run_id       UUID,
    occurred_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    actor_type   VARCHAR(16)      NOT NULL,
    actor_id     VARCHAR(64)      NOT NULL,
    action       VARCHAR(60)      NOT NULL,
    target       VARCHAR(200),
    from_state   VARCHAR(40),
    to_state     VARCHAR(40),
    result       VARCHAR(24)      NOT NULL,
    reason       VARCHAR(2000),
    details      VARCHAR(1000000),
    prev_hash    VARCHAR(64)      NOT NULL,
    hash         VARCHAR(64)      NOT NULL,
    CONSTRAINT uk_audit_chain_seq UNIQUE (chain_id, seq)
);

CREATE INDEX idx_audit_run ON audit_event (run_id);

CREATE TABLE audit_chain_head (
    chain_id   VARCHAR(64) PRIMARY KEY,
    last_seq   BIGINT      NOT NULL,
    last_hash  VARCHAR(64) NOT NULL
);

CREATE TABLE failure_event (
    id                     UUID PRIMARY KEY,
    run_id                 UUID         NOT NULL,
    stage_key              VARCHAR(40)  NOT NULL,
    generation             INTEGER      NOT NULL,
    classification         VARCHAR(16)  NOT NULL,
    cause                  VARCHAR(40)  NOT NULL,
    simulated              BOOLEAN      NOT NULL DEFAULT FALSE,
    detected_at            TIMESTAMP WITH TIME ZONE NOT NULL,
    recovery_started_at    TIMESTAMP WITH TIME ZONE,
    recovery_completed_at  TIMESTAMP WITH TIME ZONE,
    mechanism              VARCHAR(20),
    status                 VARCHAR(16)  NOT NULL,
    excluded_reason        VARCHAR(200),
    CONSTRAINT fk_failure_run FOREIGN KEY (run_id) REFERENCES workflow_run (id)
);

CREATE INDEX idx_failure_run ON failure_event (run_id);
