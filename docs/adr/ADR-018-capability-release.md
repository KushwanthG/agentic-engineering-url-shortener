# ADR-018: Governed Capability Release Flags with In-Process Preview Verification

## Status

Proposed (2026-09-26) — pending acceptance at Gate G4. Builds on Clarifications Q4 (provisional,
pending G3).

## Context

A run must end in a *real* engineering outcome on the application plane: the capability becomes
available to consumers only through the governed release stage (FR-CAP-01, FR-RDY-03). It must be
verifiable before release (FR-ORC-16) and reversible (FR-REL-05) without touching consumer data
(Q4).

## Decision Drivers

- Real, observable change to the running system
- Pre-release verification against the live implementation
- Safe, idempotent rollback
- No way for consumers to bypass release gating

## Options Considered

| Option | Advantages | Disadvantages / risks |
|--------|------------|-----------------------|
| **Persisted capability flags changed only through the port by `RELEASE`; preview scope for synthetic probe calls** | real, audited, reversible; verification before exposure | preview scope must be tightly contained |
| Static configuration flags | simple | not changeable at runtime; no audit |
| One deployment per capability | realistic CI/CD | not demonstrable within a single run |

## Decision

- `capability_release` rows are created (unreleased) at startup for every registered
  `CapabilityProvider`. The shortener reads flags through `CapabilityService`, with short-lived
  caching invalidated on change.
- Only `ApplicationPlanePort.setCapabilityRelease(...)`, with permission
  `CHANGE_CAPABILITY_RELEASE` (held by the `RELEASE` agent alone), writes flags; each change emits
  a `GLOBAL`-chain audit event with the run id.
- Preview: `ApplicationPlanePort.withPreview(capability, action)` sets a thread-scoped override
  used only by in-process probe calls on synthetic data; no HTTP header or parameter can activate
  it.
- Flags gate **new use only**; stored per-link behavior is always honored (Q4).
- Parameters (for example `defaultExpiryDays`) are stored with the flag and come from the design's
  release plan, which the clarification decision drives in SCN-C.

## Rationale

This makes release a genuine governed state change of production behavior, with rollback that is
truthful and consumer-safe.

## Consequences

- **Positive**: `GET /api/v1/capabilities` and the redirect/create behavior visibly change after a
  release.
- **Negative**: flag sprawl in long-lived systems (documented: remove flags once capabilities are
  permanent).
- **Testing**: unreleased → 422 tests; preview isolation test (HTTP cannot enable preview); release
  and rollback idempotency tests.

## Risks and Mitigations

| Risk | Mitigation |
|------|------------|
| Preview leaking to consumer threads | thread-scoped override cleared in `finally`; test with concurrent consumer requests |
| Concurrent releases by two runs | optimistic version; conflict recorded; ADR-010 rollback rule |

## Reversibility

High.

## Traceability

- Requirements: FR-CAP-01..04, FR-RDY-03, FR-REL-05, FR-ORC-15/16; Clarifications Q4
- Plan: §2 capability rows; §3 `RELEASE`; research R-15
- Tasks: capability service and release agent tasks

## Validation

`CapabilityServiceTest`, `PreviewIsolationTest`, `ReleaseAgentTest`, scenario end-to-end runs
(release visible), RDR-03.
