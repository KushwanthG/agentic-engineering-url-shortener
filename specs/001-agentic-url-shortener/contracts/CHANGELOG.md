# Contract Changelog and Change Rules

Contracts in this directory are **versioned deliverables**. `openapi.yaml` is the source of truth
for the HTTP API; `schemas/*.schema.json` (JSON Schema 2020-12) define the stage artifacts the
orchestration produces; `src/main/resources/db/migration/` holds the persistence schema (see
[`../data-model.md`](../data-model.md)).

## Versioning rules

| Change | Classification | Version impact | Required approval |
|--------|----------------|----------------|-------------------|
| New optional request field, new response field, new endpoint, new error `code` | Backward compatible | MINOR (`1.x.0`) | Architecture approval of the run that introduces it (policy `CHG-001`) |
| Documentation or example only | Compatible | PATCH (`1.x.y`) | None |
| Remove or rename a field, endpoint, enum value, or error code; make an optional field required; change a type or meaning | **Breaking** | MAJOR: new path prefix `/api/v2`, `/api/v1` kept for a deprecation window | Change-control approval plus a documented migration and deprecation plan |
| Additive nullable/defaulted column or new table | Backward compatible | Flyway `V<n>`; no API impact unless exposed | Architecture approval |
| Drop, rename, or tighten an existing column | **Breaking** | Expand-and-contract across releases | Change-control approval |

## Ownership and consumers

| Contract | Owner | Consumers | Validation |
|----------|-------|-----------|------------|
| `openapi.yaml` — links and redirect | Application plane | API consumers, browsers (redirect) | `OpenApiContractTest` (responses validated against the contract), `ContractDriftTest` |
| `openapi.yaml` — workflows, governance, operations, evidence | Control plane | Requesters, approvers, release owners, auditors, scripts in `scripts/` | Same, plus the end-to-end scenario tests |
| `schemas/*.schema.json` | Control plane (each producing agent) | Gates (review bundles), policy rules, final summary | `ArtifactSchemaTest` validates every agent output |
| Flyway migrations | Owning plane | Hibernate (`ddl-auto=validate`) | Flyway applies them on every test context start |

## Rollout and rollback of contract changes

- Capability fields (`alias`, `maxClicks`) exist in the contract before their capability is released;
  while unreleased they return `422 CAPABILITY_NOT_AVAILABLE`. Release and withdrawal are governed
  by an orchestration run (see [`../plan.md`](../plan.md) §3 and §9), so rolling back a capability
  never changes the contract or the schema.

## History

| Version | Date | Change | Classification | Introduced by |
|---------|------|--------|----------------|---------------|
| 1.0.0 | 2026-09-26 | Baseline: links, redirect, stats; workflows, governance, operations, evidence. Amended before first release during task T009 with the generic codes `RESOURCE_NOT_FOUND`, `METHOD_NOT_ALLOWED`, `UNSUPPORTED_MEDIA_TYPE` for framework-level errors (additive; listed for change-control review in T112) | — | Feature 001 baseline (US1–US4, US7) |
| 1.1.0 | 2026-09-26 | `CreateLinkRequest.alias`, `LinkResponse.customAlias`; codes `INVALID_ALIAS`, `RESERVED_ALIAS`, `ALIAS_CONFLICT` | Backward compatible | SCN-A (GF-001, custom alias) |
| 1.2.0 | 2026-09-26 | `CreateLinkRequest.maxClicks`, `LinkResponse.maxClicks`; code `INVALID_CLICK_LIMIT`; `410` for click-exhausted links | Backward compatible | SCN-B (BF-001, click limit) |

The SCN-C default-expiry capability changes behavior only (links created without `expiresAt`
receive a default expiry). It adds no field and no version bump; the behavior change is recorded
as a documentation-level PATCH once released (`1.2.1`) by the run that releases it.
