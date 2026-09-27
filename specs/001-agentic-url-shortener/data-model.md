# Data Model: Agentic Software Engineering System — URL Shortener

**Feature**: `001-agentic-url-shortener` | **Date**: 2026-09-26 | **Phase**: 1 (plan)

The persistence schema is owned by Flyway migrations under `src/main/resources/db/migration/`.
Hibernate runs with `ddl-auto=validate`, so it can never change the schema. All timestamps are
UTC (`TIMESTAMP WITH TIME ZONE`, microsecond precision). Identifiers: `BIGINT` identity for
high-volume application rows, `UUID` for control-plane rows that appear in APIs and audit evidence.

**Type conventions (implementation note, task T007)**: hashes and fingerprints are `VARCHAR(64)`
(lower-case hex); large text columns noted as `CLOB` below are implemented as large `VARCHAR`
(up to 1,000,000 characters), which keeps Hibernate schema validation and plain string mapping
simple on both H2 and PostgreSQL. Column names avoid SQL reserved words (for example the plan
version's trigger is stored as `trigger_type`).

## Migration plan (versioned persistence schema)

| Migration | Owner | Content | Compatibility |
|-----------|-------|---------|---------------|
| `V1__shortener_baseline.sql` | application plane | `short_link`, `click_event`, `idempotency_record`, `capability_release` | baseline |
| `V2__orchestration_baseline.sql` | control plane | run, requirement, plan, stage, attempt, artifact, decision, change request, policy, audit, failure tables | baseline |
| `V3__custom_alias.sql` | SCN-A capability | `short_link.custom_alias BOOLEAN NOT NULL DEFAULT FALSE` | additive, backward compatible |
| `V4__click_limit.sql` | SCN-B capability | `short_link.max_clicks BIGINT NULL` with `CHECK (max_clicks BETWEEN 1 AND 1000000)` | additive, backward compatible (NULL = unlimited) |

**Migration rules** (NFR-CHG-01/02):

1. Migrations are append-only; an applied migration is never edited.
2. Within API major version 1, schema changes are additive only (new nullable or defaulted columns,
   new tables, new indexes). Dropping or renaming a column, or tightening a constraint on existing
   data, is a breaking change: it needs a change-control approval and an expand-and-contract plan.
3. Every capability migration is independent of that capability's release state. Withdrawing a
   capability never needs a down-migration (Clarifications Q4).
4. The SCN-C default-expiry capability needs no schema change; its parameter lives in
   `capability_release.parameters`.

---

## Application plane

### ShortLink (`short_link`)

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | BIGINT | PK, identity | internal only |
| `code` | VARCHAR(32) | NOT NULL, unique `uk_short_link_code` | generated `^[A-Za-z0-9]{7}$` or alias `^[A-Za-z0-9_-]{3,32}$` |
| `target_url` | VARCHAR(2048) | NOT NULL | normalized form (FR-LNK-04) |
| `created_by` | VARCHAR(64) | NOT NULL | API consumer id; `synthetic` for probe data |
| `created_at` | TIMESTAMPTZ | NOT NULL | |
| `expires_at` | TIMESTAMPTZ | NULL | explicit or default expiry |
| `click_count` | BIGINT | NOT NULL DEFAULT 0 | changed only by atomic SQL |
| `last_accessed_at` | TIMESTAMPTZ | NULL | |
| `synthetic` | BOOLEAN | NOT NULL DEFAULT FALSE | created by an orchestration probe |
| `synthetic_run_id` | UUID | NULL, indexed | run that owns the synthetic row |
| `custom_alias` | BOOLEAN | NOT NULL DEFAULT FALSE | **V3** |
| `max_clicks` | BIGINT | NULL, CHECK 1..1,000,000 | **V4**; NULL = unlimited |

**Validation rules**: target URL per FR-LNK-02/03 (R-07); `expires_at` strictly in the future and
no later than now + 5 years (PVT-07); `max_clicks` 1..1,000,000 (PVT-09); alias 3–32 characters
from `[A-Za-z0-9_-]`, not in the reserved list (`api`, `actuator`, `admin`, `health`, `login`,
`logout`, `static`, `assets`, `swagger`, `docs`, `error`, `favicon.ico`, `robots.txt`).

**Derived state**: `ACTIVE` if (`expires_at` is NULL or `expires_at > now`) and (`max_clicks` is NULL
or `click_count < max_clicks`); otherwise `EXPIRED`. Resolution outcome is `REDIRECT`, `NOT_FOUND`,
`EXPIRED`, or `UNAVAILABLE`.

### ClickEvent (`click_event`)

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | BIGINT | PK, identity | |
| `link_id` | BIGINT | NOT NULL, FK → `short_link.id` ON DELETE CASCADE | |
| `occurred_at` | TIMESTAMPTZ | NOT NULL | index (`link_id`, `occurred_at`) |
| `referrer_host` | VARCHAR(255) | NULL | host part only; no path, no query, no IP address stored |

No client IP address, user agent string, or other personal identifier is stored (FR-ANL-02).

### IdempotencyRecord (`idempotency_record`)

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | BIGINT | PK, identity | |
| `consumer_id` | VARCHAR(64) | NOT NULL | |
| `idem_key` | VARCHAR(128) | NOT NULL; unique (`consumer_id`, `idem_key`) | `^[A-Za-z0-9_-]{1,128}$` |
| `request_fingerprint` | CHAR(64) | NOT NULL | SHA-256 of the canonical request |
| `response_status` | INT | NOT NULL | |
| `response_body` | CLOB | NOT NULL | JSON snapshot replayed on retry |
| `link_id` | BIGINT | NULL, FK | |
| `created_at`, `expires_at` | TIMESTAMPTZ | NOT NULL | window 24 h (PVT-04); expired rows are ignored and replaced |

### CapabilityRelease (`capability_release`)

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `capability_id` | VARCHAR(64) | PK | `custom-alias`, `click-limit`, `default-expiry` |
| `released` | BOOLEAN | NOT NULL DEFAULT FALSE | rows are created unreleased at startup for every registered capability |
| `parameters` | CLOB | NULL | JSON, e.g. `{"defaultExpiryDays":30}` |
| `changed_by` | VARCHAR(64) | NULL | agent id of the release stage |
| `changed_by_run` | UUID | NULL | |
| `changed_at` | TIMESTAMPTZ | NULL | |
| `reason` | VARCHAR(500) | NULL | |
| `version` | BIGINT | NOT NULL | optimistic lock |

Every change also emits a `GLOBAL`-chain audit event (actor, run, before, and after state).

---

## Control plane

### WorkflowRun (`workflow_run`)

| Column | Type | Notes |
|--------|------|-------|
| `id` | UUID PK | the run identifier and correlation id |
| `requirement_ref` | VARCHAR(64) | e.g. `GF-001` |
| `title` | VARCHAR(200) | |
| `requested_by` | VARCHAR(64) | requester principal (used for separation of duties) |
| `classification` | VARCHAR(32) | `NEW_CAPABILITY`, `CHANGE_TO_EXISTING`, `UNDETERMINED` |
| `status` | VARCHAR(32) | see run state machine |
| `current_plan_version` | INT | |
| `current_requirement_version` | INT | |
| `policy_set_version` | VARCHAR(20) | pinned at creation (FR-POL-01) |
| `readiness` | VARCHAR(40) NULL | `READY`, `READY_WITH_ACCEPTED_LIMITATIONS`, `NOT_READY` |
| `terminal_outcome` | VARCHAR(32) NULL | `COMPLETED`, `REJECTED`, `SAFE_STOPPED` |
| `terminal_reason` | VARCHAR(1000) NULL | |
| `manual_intervention_required` | BOOLEAN | set when compensation fails |
| `clarification_rounds` | INT | bounded by PVT-14 |
| `attempts_used`, `processing_millis` | INT, BIGINT | autonomy budget accounting (PVT-17) |
| `fault_plan` | CLOB NULL | simulated faults (only when fault injection is enabled) |
| `gate_deadline_seconds` | BIGINT NULL | per-run gate deadline override (fault-injection mode only) |
| `created_at`, `started_at`, `updated_at`, `completed_at` | TIMESTAMPTZ | |
| `version` | BIGINT | optimistic lock |

### RequirementVersion (`requirement_version`)

`id` UUID · `run_id` FK · `version` INT (unique per run) · `source` (`SUBMITTED`, `CLARIFIED`,
`CHANGE_REQUEST`) · `content` CLOB (JSON *RequirementDocument*, see the contracts) · `fingerprint`
CHAR(64) · `created_by` · `created_at` · `decision_id` UUID NULL (the decision that produced it).

### PlanVersion (`plan_version`)

`id` UUID · `run_id` FK · `version` INT (unique per run) · `graph` CLOB (JSON: stages with type,
dependencies, condition, gate flag) · `diff` CLOB NULL (added, removed, activated, invalidated
stages) · `trigger_type` (`INITIAL`, `ANALYSIS_REFINEMENT`, `CLARIFICATION`, `CHANGE_REQUEST`,
`CHANGE_DECISION`, `LATE_AMBIGUITY`) · `reason` · `created_by` · `created_at`.

### StageNode (`stage_node`)

| Column | Type | Notes |
|--------|------|-------|
| `id` | UUID PK | |
| `run_id` | UUID FK | unique (`run_id`, `stage_key`) |
| `stage_key`, `stage_type` | VARCHAR(40) | key equals type; each type appears at most once per plan |
| `status` | VARCHAR(32) | see stage state machine |
| `awaiting` | VARCHAR(32) NULL | `APPROVAL`, `CLARIFICATION`, `POLICY_EXCEPTION`, `CHANGE_APPROVAL` |
| `depends_on` | VARCHAR(1000) | comma-separated stage keys |
| `generation` | INT | incremented on invalidation (replanning) |
| `attempts` | INT | attempts in the current generation |
| `input_fingerprint` | CHAR(64) NULL | fingerprint of inputs of the last successful execution |
| `reused`, `degraded` | BOOLEAN | reuse without re-execution; fallback used |
| `skip_reason` | VARCHAR(500) NULL | mandatory when `SKIPPED` (FR-ORC-06) |
| `next_attempt_at` | TIMESTAMPTZ NULL | backoff schedule (`RETRY_WAIT`) |
| `decision_deadline` | TIMESTAMPTZ NULL | gate deadline (FR-GOV-07) |
| `last_failure_class`, `last_failure_reason` | VARCHAR | `TRANSIENT` or `PERMANENT` |
| `started_at`, `finished_at` | TIMESTAMPTZ NULL | |
| `version` | BIGINT | optimistic lock (one gate decision wins) |

### StageAttempt (`stage_attempt`)

`id` UUID · `run_id` · `stage_key` · `generation` · `attempt_no` · `agent_id` (e.g.
`requirement-analyst@1.0`) · `fallback` BOOLEAN · `simulated_fault` VARCHAR NULL ·
`scheduling_cycle` BIGINT (the coordinator cycle that dispatched the attempt; parallel stages share
it — review RC-2) · `started_at` ·
`finished_at` NULL · `outcome` (`SUCCEEDED`, `FAILED_TRANSIENT`, `FAILED_PERMANENT`, `TIMED_OUT`,
`INTERRUPTED`, `DISCARDED`, `NEEDS_CLARIFICATION`, `POLICY_BLOCKED`, `REUSED`) · `failure_class` ·
`error` · `input_fingerprint`. The timeline API and the parallelism evidence are built from these
rows (FR-AUD-06).

### Artifact (`artifact`)

`id` UUID · `run_id` · `stage_key` · `generation` · `attempt_no` · `artifact_type` · `version` INT
(unique per run and type) · `media_type` (`application/json` or `text/markdown`) · `content` CLOB ·
`fingerprint` CHAR(64) (SHA-256 of content) · `produced_by` (agent id) · `input_refs` CLOB (JSON
list of `{artifactId, type, version, fingerprint}`) · `superseded` BOOLEAN · `created_at`.

**Artifact types** (JSON Schemas in `contracts/schemas/` where marked ✓):

| Type | Producer | Schema |
|------|----------|--------|
| `REQUIREMENT` | ingestion | ✓ `requirement-document` |
| `NORMALIZED_REQUIREMENT` | analysis | ✓ `normalized-requirement` (includes quality checks and ambiguities) |
| `CLARIFICATION_REQUEST` | analysis / any agent escalating | ✓ `clarification-request` |
| `TASK_GRAPH` | decomposition | ✓ `task-graph` |
| `IMPACT_ANALYSIS` | impact analysis | ✓ `impact-analysis` |
| `THREAT_MODEL` | threat assessment | ✓ `threat-model` |
| `DESIGN` | design | ✓ `design` |
| `CHANGE_SET` | implementation | ✓ `change-set` |
| `TEST_REPORT`, `REGRESSION_REPORT`, `SECURITY_REPORT` | testing stages | ✓ `verification-report` |
| `DOCUMENTATION` | documentation | Markdown |
| `VALIDATION_REPORT` | validation | ✓ `validation-report` |
| `COMPLIANCE_REPORT` | compliance evaluation | ✓ `compliance-report` |
| `READINESS_REPORT` | compliance evaluation | ✓ `readiness-report` |
| `RELEASE_RECORD` | release | ✓ `release-record` |
| `FINAL_SUMMARY` | final summary / finalizer | Markdown |

### Decision (`decision`)

`id` UUID · `run_id` · `stage_key` NULL · `decision_type` (`GATE`, `CLARIFICATION_ANSWER`,
`CHANGE_REQUEST`, `CHANGE_DECISION`, `EXCEPTION_REQUEST`, `EXCEPTION_DECISION`, `FALLBACK_USED`,
`REPLAN`, `OPERATOR_ACTION`, `SAFE_STOP`) · `outcome` (e.g. `APPROVED`, `REJECTED`, `ANSWERED`,
`APPLIED`, `PAUSED`, `RESUMED`) · `actor_type` (`HUMAN`, `AGENT`, `SYSTEM`) · `actor_id` ·
`actor_role` · `rationale` · `payload` CLOB NULL · `bound_fingerprints` CLOB NULL (JSON map
artifact type → fingerprint) · `valid` BOOLEAN · `invalidated_reason` · `supersedes` UUID NULL ·
`created_at`. Decisions are never deleted; invalidation sets `valid = FALSE` and records the reason
(FR-GOV-05).

### ChangeRequest (`change_request`)

`id` UUID · `run_id` · `requested_by` · `requested_at` · `reason` · `amended_requirement` CLOB ·
`material` BOOLEAN · `impact` CLOB (affected stages and invalidated approvals) · `status`
(`PENDING_APPROVAL`, `APPLIED`, `REJECTED`) · `decided_by` · `decided_at` · `decision_rationale`.

### PolicyEvaluation (`policy_evaluation`) and PolicyException (`policy_exception`)

`policy_evaluation`: `id` · `run_id` · `stage_key` · `generation` · `policy_id` ·
`policy_set_version` · `severity` (`MANDATORY`, `ADVISORY`) · `outcome` (`PASS`, `FAIL`,
`EXCEPTION_REQUESTED`, `NOT_APPLICABLE`) · `evidence` · `exception_id` NULL · `simulated` ·
`evaluated_at`.

`policy_exception`: `id` · `run_id` · `policy_id` · `reason` · `scope` · `compensating_control` ·
`requested_by` · `requested_at` · `expires_at` · `status` (`PENDING`, `APPROVED`, `REJECTED`) ·
`decided_by` · `decided_at` · `decision_rationale`. Approval requires the `APPROVER` role and an
approver different from the requester; an approved exception with `expires_at <= now` counts as
unapproved (FR-POL-04).

### AuditEvent (`audit_event`)

`id` BIGINT identity · `chain_id` (run UUID or `GLOBAL`) · `seq` (unique per chain) · `run_id` NULL ·
`occurred_at` · `actor_type` · `actor_id` · `action` (e.g. `RUN_CREATED`, `STAGE_TRANSITION`,
`ATTEMPT_FAILED`, `RETRY_SCHEDULED`, `FALLBACK_USED`, `GATE_DECIDED`, `DECISION_REFUSED`,
`PLAN_VERSION_CREATED`, `STAGE_INVALIDATED`, `POLICY_EVALUATED`, `COMPENSATION_EXECUTED`,
`CAPABILITY_CHANGED`, `RUN_TERMINATED`) · `target` · `from_state` · `to_state` · `result` ·
`reason` · `details` CLOB · `prev_hash` CHAR(64) · `hash` CHAR(64).

`hash = SHA-256(canonical JSON of {prevHash, chainId, seq, runId, occurredAt, actorType, actorId,
action, target, fromState, toState, result, reason, details})`; canonical JSON (sorted keys, no
whitespace) makes the encoding unambiguous. The genesis `prev_hash` is 64 zeros. Rows are
insert-only.

### AuditChainHead (`audit_chain_head`)

`chain_id` VARCHAR(64) PK · `last_seq` BIGINT · `last_hash` VARCHAR(64). Appends lock the head row
(`SELECT … FOR UPDATE`) so concurrent appends to one chain serialize; verification compares the
head with the last event, which detects truncation of the chain's tail (task T012).

### FailureEvent (`failure_event`)

`id` UUID · `run_id` · `stage_key` · `generation` · `classification` · `cause` (`AGENT_ERROR`,
`TIMEOUT`, `PROCESS_INTERRUPTION`, `VERIFICATION_FAILURE`, `COMPENSATION_ERROR`) · `simulated` ·
`detected_at` · `recovery_started_at` NULL · `recovery_completed_at` NULL · `mechanism` (`RETRY`,
`FALLBACK`, `RESUME`) NULL · `status` (`OPEN`, `RECOVERED`, `UNRECOVERED`) · `excluded_reason` NULL.
One failure event covers one failure **episode** of a stage generation: from the first failed
attempt to the stage's eventual success (`RECOVERED`) or terminal failure (`UNRECOVERED`).

---

## State machines

### Run status

| From | Allowed to | Trigger |
|------|-----------|---------|
| `CREATED` | `RUNNING` | start after plan validation |
| `RUNNING` | `AWAITING_HUMAN`, `PAUSED`, `COMPENSATING`, `COMPLETED`, `SAFE_STOPPED` | no runnable stage without a decision / operator pause / failure or rejection with side effects / all stages done / safe-stop without side effects |
| `AWAITING_HUMAN` | `RUNNING`, `PAUSED`, `COMPENSATING`, `SAFE_STOPPED`, `REJECTED` | decision recorded / pause / failure with side effects / deadline or operator safe-stop / rejection without side effects |
| `PAUSED` | `RUNNING`, `COMPENSATING`, `SAFE_STOPPED` | resume / operator safe-stop |
| `COMPENSATING` | `SAFE_STOPPED`, `REJECTED` | compensation finished (or failed, which sets `manual_intervention_required`) |
| `COMPLETED`, `REJECTED`, `SAFE_STOPPED` | — | terminal: no transition, no replanning (FR-RPL-05) |

### Stage status

| From | Allowed to |
|------|-----------|
| `PENDING` | `READY` (dependencies satisfied and entry criteria pass), `SKIPPED` (condition false; reason required), `CANCELLED`, `REMOVED` (replan) |
| `READY` | `RUNNING` (dispatched), `SUCCEEDED` (reused: input fingerprint unchanged), `AWAITING_DECISION` (gate opened), `CANCELLED` |
| `RUNNING` | `SUCCEEDED`, `RETRY_WAIT` (transient failure, attempts remain), `RUNNING` (fallback attempt), `FAILED` (permanent or exhausted with no fallback), `AWAITING_DECISION` (needs clarification or policy blocked), `PENDING` (invalidated in flight; late result discarded), `CANCELLED` |
| `RETRY_WAIT` | `RUNNING`, `PENDING` (invalidated), `CANCELLED` |
| `AWAITING_DECISION` | `SUCCEEDED` (approved or resolved), `READY` (exception approved or clarification answered: re-execute), `FAILED` (rejected or deadline passed), `PENDING` (invalidated), `CANCELLED` |
| `SUCCEEDED` | `PENDING` (invalidated by replanning), `COMPENSATED` |
| `SKIPPED` | `PENDING` (condition re-activated by replanning) |
| `FAILED`, `COMPENSATED`, `CANCELLED`, `REMOVED` | — (terminal for the node) |

**Prohibited examples** (rejected and audited as `ILLEGAL_TRANSITION`): `PENDING → SUCCEEDED`,
`SUCCEEDED → RUNNING`, `AWAITING_DECISION → RUNNING` without a decision, any transition out of
`FAILED`, `CANCELLED`, `REMOVED`, or `COMPENSATED`, and any gate reaching `SUCCEEDED` without a
recorded human decision.

## Relationships

```text
WorkflowRun 1─* RequirementVersion      WorkflowRun 1─* PlanVersion
WorkflowRun 1─* StageNode 1─* StageAttempt
WorkflowRun 1─* Artifact (input_refs → Artifact)       Decision ─ bound_fingerprints → Artifact
WorkflowRun 1─* Decision   WorkflowRun 1─* ChangeRequest   WorkflowRun 1─* FailureEvent
WorkflowRun 1─* PolicyEvaluation *─0..1 PolicyException
WorkflowRun 1─* AuditEvent (chain_id = run id);  GLOBAL chain for capability and security events
ShortLink 1─* ClickEvent;  ShortLink 0..1─1 IdempotencyRecord;  ShortLink *─0..1 WorkflowRun (synthetic_run_id)
```
