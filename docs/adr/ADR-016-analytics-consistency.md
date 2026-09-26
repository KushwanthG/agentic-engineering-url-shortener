# ADR-016: Exact, Synchronous Click Analytics with Fail-Open / Fail-Closed Rules

## Status

Proposed (2026-09-26) — pending acceptance at Gate G4. Builds on Clarifications Q5 (provisional,
pending G3).

## Context

Every successful redirect must be counted exactly once (FR-ANL-01/05). Analytics failures must not
break redirects for normal links (FR-ANL-04), but click-limited links (SCN-B) must fail closed
when the click cannot be recorded, because the limit cannot otherwise be guaranteed (BF-001
AC-6). Redirect caching would hide repeat clicks.

## Decision Drivers

- Exactness under concurrency
- Redirect availability for normal links
- Correct enforcement for click-limited links
- Simplicity at prototype scale

## Options Considered

| Option | Advantages | Disadvantages / risks |
|--------|------------|-----------------------|
| **Synchronous atomic SQL increments; 302 + `no-store`; fail open without a rule, fail closed with a click limit** | exact; simple; enforceable | one extra write per redirect |
| Asynchronous queue or buffer | lower redirect latency | loss on crash; cannot enforce click limits |
| 301 permanent redirects | lowest load | browsers cache, so analytics undercount |

## Decision

- Redirect status `302 Found` with `Cache-Control: no-store`.
- `ClickRecorder` runs in its own short transaction (`REQUIRES_NEW`):
  `UPDATE short_link SET click_count = click_count + 1, last_accessed_at = :now WHERE id = :id`
  plus an `INSERT` into `click_event` (timestamp and referrer host only).
- With a click limit: `UPDATE … WHERE id = :id AND click_count < max_clicks`. Zero rows updated
  means the limit is exhausted → `410`; a data-access failure → `503 STORE_UNAVAILABLE` (fail
  closed).
- Without a click limit: a data-access failure is logged without the target URL, increments
  `shortener.analytics.failures`, and the redirect still happens (fail open).

## Rationale

The database performs the atomic read-modify-write, so no application locks or queues are
needed, and exactness holds under any concurrency.

## Consequences

- **Positive**: counts equal redirects (SC-005); click limits are exact.
- **Negative**: redirect latency includes one write (measured against PVT-19).
- **Testing**: 200-way concurrent redirect test; exact-N click-limit concurrency test; fail-open
  and fail-closed tests with an injected repository failure.

## Risks and Mitigations

| Risk | Mitigation |
|------|------------|
| Hot-row contention on very popular links | acceptable at prototype scale; production path: sharded counters or an event stream with an exact limiter (documented) |

## Reversibility

Medium: moving to asynchronous counting changes analytics semantics and requires a new
clarification decision.

## Traceability

- Requirements: FR-ANL-01/04/05, FR-RED-02, FR-CAP-03, NFR-REL-01, SC-005; Clarifications Q5
- Plan: §2 redirect flow; research R-08
- Tasks: redirect and analytics task group; SCN-B click-limit tasks

## Validation

`RedirectConcurrencyTest`, `ClickLimitConcurrencyTest`, `AnalyticsFailOpenTest`,
`ClickLimitFailClosedTest`, performance measurement (labeled demonstration data).
