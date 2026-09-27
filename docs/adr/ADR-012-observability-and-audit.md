# ADR-012: Hash-Chained Audit Trail and Evidence-Computed Reliability Metrics

## Status

Proposed (2026-09-26) — pending acceptance at Gate G4.

## Context

Audit-grade observability and traceability, plus reliability metrics (success rate, retry and
rollback frequency, MTTR, end-to-end latency), are required (A§4.4; FR-AUD-01..06; constitution
IX). Evidence must be tamper-evident and must not be fabricated or confused with production data.

## Decision Drivers

- Tamper evidence without external services
- Reproducible metrics that survive restarts
- Clear labeling of demonstration data
- Standard operational telemetry for production-readiness

## Options Considered

| Option | Advantages | Disadvantages / risks |
|--------|------------|-----------------------|
| **Insert-only audit table with a per-run SHA-256 hash chain; metrics computed from persisted evidence; Micrometer for operational telemetry** | verifiable, reproducible, standard | a chain can be rewritten by someone with database write access (documented) |
| Logs only | trivial | not queryable; not tamper-evident |
| In-memory Micrometer meters as the metric source | standard | lost on restart; not reproducible |
| External ledger or WORM storage | strongest integrity | external dependency (backlog BL-06) |

## Decision

- `AuditService.append(event)` assigns `seq` per chain (run id or `GLOBAL`) and computes
  `hash = SHA-256(prev_hash | fields…)` in the same transaction as the state change it records.
  There is no update or delete path. `AuditVerifier` recomputes each chain and reports the first
  broken `seq`.
- `FailureEventRecorder` opens and closes failure episodes (data-model.md), and
  `ReliabilityReportService` computes the metrics in plan §7. MTTR includes only recovered events;
  unrecovered and excluded events are listed separately; every report carries the label
  `DEMONSTRATION DATA - not production statistics`.
- Operational telemetry: Micrometer meters and `Observation`s (plan §7), Prometheus endpoint
  restricted to the auditor role, MDC keys in logs, ECS structured logs in the `json-logs` profile.

## Rationale

The chosen design keeps a database-only footprint, gives reviewers an executable integrity check,
and keeps metrics computable from the same evidence.

## Consequences

- **Positive**: `GET .../audit/verification` proves integrity on demand; metrics are reproducible.
- **Negative**: audit writes add a small cost to each transition.
- **Testing**: tamper tests (modify, delete, insert), MTTR calculation tests with known episodes,
  exclusion tests.
- **Governance**: evidence integrity is enforced by policy `AUD-001` before release.

## Risks and Mitigations

| Risk | Mitigation |
|------|------------|
| Whole-chain rewrite by a database administrator | documented residual risk; BL-06 external anchoring |
| Clock skew affecting durations | single process, single `Clock` bean |

## Reversibility

High.

## Traceability

- Requirements: FR-AUD-01..06, NFR-OBS-01/02, NFR-AUD-01/02, NFR-RCV-02, SC-006, SC-008
- Plan: §7, §4; data-model.md `audit_event`, `failure_event`
- Tasks: audit and metrics task groups

## Validation

`AuditChainTest`, `AuditTamperDetectionTest`, `ReliabilityReportServiceTest`,
`RunReconstructionTest`.
