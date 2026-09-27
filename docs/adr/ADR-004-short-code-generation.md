# ADR-004: Random Base62 Short Codes with Database-Enforced Uniqueness

## Status

Proposed (2026-09-26) — pending acceptance at Gate G4.

## Context

Codes must be unique (FR-LNK-05), non-predictable (FR-LNK-06), short, and generated safely
under concurrency (FR-LNK-09). Custom aliases (SCN-A) share the same namespace.

## Decision Drivers

- Unpredictability (enumeration resistance)
- Collision probability at the design point of 10 million links (NFR-SCA-02)
- Correctness under concurrent creation
- Simplicity

## Options Considered

| Option | Advantages | Disadvantages / risks |
|--------|------------|-----------------------|
| **SecureRandom base62, length 7, unique index, bounded regeneration** | unpredictable; stateless; collisions detected by the database | needs a retry loop |
| Base62 of a database sequence | no collisions | sequential, so trivially enumerable |
| Hash of the target URL (truncated) | deterministic | implies de-duplication (rejected by Q3); deterministic collisions |

## Decision

Generate 7 characters uniformly from `[0-9A-Za-z]` with `SecureRandom`. Insert inside a
transaction; the unique index `uk_short_link_code` is the source of truth. On a code collision,
retry the whole create transaction with a new code, at most 5 attempts (PVT-03), then fail with
`503 CODE_GENERATION_EXHAUSTED` (retryable, no partial link). An alias collision is not retried:
it returns `409 ALIAS_CONFLICT`. The generator is an interface so tests can force collisions.

## Rationale

62⁷ ≈ 3.52 × 10¹². At 10⁷ stored links the per-attempt collision probability is about
2.8 × 10⁻⁶, so five consecutive collisions are effectively impossible while codes stay short and
non-enumerable.

## Consequences

- **Positive**: no coordination between instances; enumeration needs about 3.5 × 10¹² guesses per
  hit at 1 link, and the not-found throttle slows scanning further.
- **Negative**: a small retry path to test.
- **Testing**: forced-collision tests and an exhaustion test; statistical smoke check of the
  alphabet.

## Risks and Mitigations

| Risk | Mitigation |
|------|------------|
| Weak randomness | `SecureRandom` only; generator tests |
| Offensive generated words | accepted residual risk for a prototype (a production blocklist is possible) |

## Reversibility

High: the generator is behind an interface; the code column accepts up to 32 characters.

## Traceability

- Requirements: FR-LNK-05, FR-LNK-06, FR-LNK-09, NFR-SCA-02, FR-CAP-02
- Plan: §2 components; research R-06
- Tasks: code generator and collision-handling tasks

## Validation

`ShortCodeGeneratorTest`, `LinkCreationCollisionTest` (forced collisions, exhaustion), concurrent
creation test.
