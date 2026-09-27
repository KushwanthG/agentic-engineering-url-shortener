# Links API — URL shortener (application plane)

Contract: [`openapi.yaml`](../../specs/001-agentic-url-shortener/contracts/openapi.yaml) (tags `links`,
`redirect`). This page explains behavior; the contract is authoritative and contract tests
(`LinkApiContractTest`) validate every response shape against it.

## Authentication

| Endpoint | Who |
|---|---|
| `POST /api/v1/links`, `GET /api/v1/links/{code}`, `GET /api/v1/links/{code}/stats` | Bearer token with role `API_CONSUMER` |
| `GET /{code}` | public (no credentials) |

Demo profile token: `demo-consumer-token` (non-production; only its SHA-256 hash is configured).
Missing or invalid credentials → `401 UNAUTHENTICATED`; another role → `403 FORBIDDEN`. Requests
are rejected before any link lookup.

## Create a link — `POST /api/v1/links`

```bash
curl -i -X POST http://localhost:8080/api/v1/links \
  -H "Authorization: Bearer demo-consumer-token" -H "Content-Type: application/json" \
  -H "Idempotency-Key: order-4711" \
  -d '{"url":"https://www.example.com/articles/2026/spring-sale?ref=newsletter","expiresAt":"2026-12-31T23:59:59Z"}'
```

```http
HTTP/1.1 201
Location: /api/v1/links/pIPKtAY

{"code":"pIPKtAY","shortUrl":"http://localhost:8080/pIPKtAY",
 "targetUrl":"https://www.example.com/articles/2026/spring-sale?ref=newsletter",
 "createdAt":"2026-09-26T12:34:17.984038Z","expiresAt":"2026-12-31T23:59:59Z",
 "status":"ACTIVE","customAlias":false}
```

| Field | Rules |
|---|---|
| `url` (required) | absolute `http`/`https` URL, at most 2,048 characters; stored normalized (lower-case scheme and host, IDN host in ASCII, default port removed, path/query/fragment unchanged) |
| `expiresAt` | optional; must be in the future and at most 1,826 days (5 years) ahead; the link is expired **at** that instant |
| `alias` | requires capability `custom-alias`; while unreleased → `422 CAPABILITY_NOT_AVAILABLE` |
| `maxClicks` | requires capability `click-limit`; while unreleased → `422 CAPABILITY_NOT_AVAILABLE` |

Short codes are 7 random base62 characters from `SecureRandom` (62^7 ≈ 3.5 × 10^12). A collision is
retried with a new code up to 5 times; if all attempts collide the request fails with
`503 CODE_GENERATION_EXHAUSTED` (retryable) and nothing is stored. The same target URL submitted
twice creates two links (no de-duplication).

### Custom aliases (capability `custom-alias`, contract 1.1.0)

A consumer may choose the short code with `alias` once the `custom-alias` capability has been
released by a governed workflow run (scenario SCN-A); until then an `alias` is rejected with
`422 CAPABILITY_NOT_AVAILABLE`, never ignored.

| Rule | Outcome |
|---|---|
| 3–32 characters of letters, digits, hyphen, underscore; case-sensitive | the alias becomes the code; response `customAlias: true` |
| other characters, or shorter than 3 / longer than 32 | `400 INVALID_ALIAS` |
| reserved word (`api`, `actuator`, `admin`, `health`, ...; compared ignoring case) | `400 RESERVED_ALIAS` |
| alias already used by any link (aliases share the namespace of generated codes) | `409 ALIAS_CONFLICT` |

An aliased link resolves like any other link (`GET /spring-sale` → 302). Withdrawing the capability
stops new aliases only; existing aliases keep resolving.

### Click-limited links (capability `click-limit`, contract 1.2.0)

A consumer may limit how many times a link redirects with `maxClicks` once the `click-limit`
capability has been released by a governed workflow run (scenario SCN-B). Until then, `maxClicks`
is rejected with `422 CAPABILITY_NOT_AVAILABLE`.

| Input | Result |
|---|---|
| `maxClicks` from 1 to 1,000,000 | the link is created with the limit; responses report `maxClicks` |
| `maxClicks` outside 1..1,000,000 | `400 INVALID_CLICK_LIMIT` |
| no `maxClicks` | an unlimited link, exactly as before the capability existed |

How a limit is enforced:

- The first `maxClicks` resolutions redirect, and every later one returns `410 LINK_EXPIRED`.
- The metadata status becomes `EXPIRED` once the limit is used up.
- Each click is counted with a single conditional update, so concurrent resolutions can never
  exceed the limit.
- Withdrawing the capability stops new limits only. Stored limits stay enforced.

### Default expiry (capability `default-expiry`)

When the `default-expiry` capability is released, a link created **without** `expiresAt` expires a
number of days after creation. The number of days is a release parameter (`defaultExpiryDays`)
decided by a human during clarification (scenario SCN-C; the reference decision is 30 days).

- An explicit `expiresAt` is always kept.
- Existing links are never changed: the default applies at creation only.
- Withdrawing the capability stops new defaults. Expiries already assigned remain.

### URL safety rules (no DNS resolution)

Rejected with `400` and a specific code:

| Code | Examples |
|---|---|
| `URL_SCHEME_NOT_ALLOWED` | `javascript:`, `data:`, `file:`, `ftp:`, `mailto:`, `vbscript:` |
| `URL_CREDENTIALS_NOT_ALLOWED` | `https://user:pw@example.com/`, `https://example.com@evil.example/` |
| `URL_HOST_MISSING` | `https:///path` |
| `URL_TOO_LONG` | more than 2,048 characters |
| `URL_HOST_NOT_ALLOWED` | `localhost` and `*.localhost`, `*.local`, `*.internal`, single-label hosts; loopback, private, link-local (incl. `169.254.169.254`), CGNAT, unspecified, multicast and reserved IPv4 in any notation (`127.1`, `2130706433`, `0x7f000001`, `0177.0.0.1`); IPv6 `::1`, `fe80::/10`, `fc00::/7`, IPv4-mapped private addresses; the shortener's own host |
| `URL_INVALID` | not an absolute URL; percent-encoded host; malformed port or IPv6 |

The full catalog (36 inputs) is tested at unit level (`UrlPolicyTest`) and through HTTP
(`UrlSecurityCatalogIT`).

### Idempotency

`Idempotency-Key` (1–128 of `[A-Za-z0-9_-]`, scoped per consumer, kept 24 h):

- same key + identical body → the original `201` response is replayed with `Idempotency-Replayed: true`;
- same key + different body → `409 IDEMPOTENCY_KEY_REUSED`;
- malformed key → `400 INVALID_IDEMPOTENCY_KEY`;
- concurrent identical requests create exactly one link (the link and its key record commit in one
  transaction; the losers replay the winner).

## Read a link — `GET /api/v1/links/{code}`

Returns the same shape as creation; `status` is `EXPIRED` once `expiresAt` has passed. Unknown code
→ `404 LINK_NOT_FOUND`.

## Analytics — `GET /api/v1/links/{code}/stats`

```json
{"code":"pIPKtAY","totalClicks":3,"lastAccessedAt":"2026-09-26T12:34:18.202609Z",
 "windowDays":30,"daily":[{"date":"2026-09-26","clicks":3}]}
```

Counts are exact and synchronous: each successful redirect is counted once, before the redirect is
returned, with an atomic SQL update. `daily` lists UTC days with clicks in the last 30 days (days
without clicks are omitted). Click events store the time and the referrer **host** only — no IP
address or other personal data.

## Redirect — `GET /{code}`

| Outcome | Response |
|---|---|
| active link | `302 Found`, `Location: <target>`, `Cache-Control: no-store` (every resolution reaches the service and is counted) |
| unknown code | `404 LINK_NOT_FOUND` (a code of `[A-Za-z0-9_-]` shorter than 3 or longer than 32 characters → `404 RESOURCE_NOT_FOUND`; paths with other characters are refused by the deny-by-default security rules) |
| expired link, or a click-limited link that has used all its clicks | `410 LINK_EXPIRED` (not counted) |
| link store unavailable | `503 STORE_UNAVAILABLE` with `Retry-After` |

If recording the click fails, the redirect is still served and the failure is counted in the
metric `shortener.analytics.failures` (fail-open; ADR-016). **Exception:** for a click-limited
link, the redirect is refused with `503 STORE_UNAVAILABLE`, because an uncounted redirect could
exceed the limit (fail-closed; BF-001 AC-6).

## Rate limits

| Limit | Default | Key | Exceeded |
|---|---|---|---|
| link creation | 30 per minute | API consumer | `429 RATE_LIMITED` + `Retry-After` |
| not-found redirects | 60 per minute | client address | further redirects from that address → `429` + `Retry-After` |

Token buckets refill continuously; at most 10,000 keys are tracked (least recently used evicted).
Limits are per instance (multi-instance limiting is backlog item BL-05).

## Errors

All errors are `application/problem+json` (RFC 9457) with `type`, `title`, `status`, `detail`,
`code`, and `correlationId` (also returned in the `X-Correlation-Id` header); `429` and `503` add
`retryAfterSeconds`. No stack traces or SQL are ever returned.

## Operations

- `GET /actuator/health/liveness` — process is alive.
- `GET /actuator/health/readiness` — includes `linkStore`; `DOWN` (503) while the database cannot
  answer, so traffic can be withheld. Creation and redirects fail fast (connection timeout 2 s)
  with `503 STORE_UNAVAILABLE` and leave no partial writes.
