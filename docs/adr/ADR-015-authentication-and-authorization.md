# ADR-015: Hashed Bearer Tokens Mapped to Roles (Stand-in for an Identity Provider)

## Status

Proposed (2026-09-26) — pending acceptance at Gate G4. Builds on Clarifications Q2 (provisional,
pending G3).

## Context

Link management requires authenticated API consumers, and redirects are public (Q2). The control
plane requires human principals with distinct roles and separation of duties (FR-GOV-03/04,
FR-RDY-04, NFR-SEC-02). No enterprise identity provider is available (ASM-02); credentials must
not be stored in plaintext in the repository (constitution V).

## Decision Drivers

- Least privilege and separation of duties are demonstrable
- No external identity infrastructure for reviewers
- A clear path to production authentication

## Options Considered

| Option | Advantages | Disadvantages / risks |
|--------|------------|-----------------------|
| **Stateless bearer tokens; principals and roles in configuration; token SHA-256 hashes, constant-time comparison** | simple, stateless, no plaintext tokens in config | static tokens (no expiry or rotation) |
| HTTP Basic with in-memory users | built in | passwords in configuration; weaker API semantics |
| OAuth2 resource server with a local issuer | production-like | heavy setup for reviewers |

## Decision

- `BearerTokenAuthenticationFilter` hashes the presented token, looks up a configured principal,
  and populates the `SecurityContext` with roles `API_CONSUMER`, `REQUESTER`, `APPROVER`,
  `RELEASE_OWNER`, `AUDITOR`. Unknown or missing tokens yield `401 UNAUTHENTICATED`, without
  revealing why.
- Authorization: URL rules in `SecurityConfig` (links → `API_CONSUMER`; workflow reads → any
  control-plane role; submissions and change requests → `REQUESTER`; gate, clarification, and
  exception decisions → role checked per gate in the service; operator actions →
  `RELEASE_OWNER`; evidence → `AUDITOR` or `RELEASE_OWNER`; actuator beyond health and info →
  `AUDITOR`). Separation of duties is enforced in services.
- CSRF disabled (no cookies, stateless); sessions `STATELESS`; default security headers.
- The `demo` profile defines five labeled principals; the default profile defines none.
- Production path: replace the filter with `spring-boot-starter-oauth2-resource-server` and map
  JWT claims to the same roles; no other code changes.

## Rationale

The chosen design makes roles and separation of duties real and testable today, with an
identity-provider migration that touches one component.

## Consequences

- **Positive**: authorization matrix fully testable with MockMvc.
- **Negative**: static tokens lack expiry and rotation (documented limitation).
- **Testing**: per-endpoint 401/403 matrix; SoD tests; log capture proving tokens never appear.

## Risks and Mitigations

| Risk | Mitigation |
|------|------------|
| Demo tokens reused in a real deployment | demo principals only in the `demo` profile; startup warning when it is active |
| Token brute force | 128-bit-plus random tokens in production (documented); creation rate limits |

## Reversibility

High (filter replacement).

## Traceability

- Requirements: FR-LNK-13, FR-GOV-03/04, FR-RDY-04, NFR-SEC-02/03; Clarifications Q2
- Plan: §5, §8; research R-10
- Tasks: security task group

## Validation

`SecurityMatrixTest`, `SeparationOfDutiesTest`, `TokenNotLoggedTest`.
