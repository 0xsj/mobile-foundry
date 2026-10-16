# Runtime HTTP v1

Implemented by the Go runtime, 2026-10-12. API base is `/v1`.

| Endpoint | Success | Failure |
| --- | --- | --- |
| GET /v1/health/live | 200, `{"status":"ok"}` | Unexpected boundary failure |
| GET /v1/health/ready | 200, `{"status":"ok"}` | 503, unavailable, `health.not_ready` when starting/draining or a selected probe fails |

HEAD has the corresponding GET status/headers with no body. Unsupported methods
on health paths return 405 and Allow. Unknown GET/POST/PATCH/PUT/DELETE paths
return 404; other unsupported methods return 405. ServeMux retains native path
cleaning/redirect behavior.

Success uses application/json. Errors use application/problem+json with `type`,
`title`, `status`, `detail`, `kind`, optional `code`, `fields` and `request_id`.
Type is `urn:problem:<kind>`; code is the separate condition identifier. Internal
and unknown failures return generic detail without codes, fields or causes.
Kinds map invalid→400, unauthenticated→401, forbidden→403, not_found→404,
conflict→409, rate_limited→429, unavailable→503, timeout→504, canceled→408,
internal→500. Owned admission conditions also select 413/415/405/428.

Responses carry no-store, nosniff and a fresh server-owned X-Request-ID. Caller
request IDs are ignored. Optional X-Correlation-ID admits one value of 1–128
ASCII letters/digits or ._:- and echoes it; invalid/repeated values return 400.
Default access logs contain ID, method, status and elapsed time; failure logs
contain safe classification. URLs, bodies, credentials, panic payloads and
diagnostic causes are omitted.

The boundary supplies a cooperative 15-second request context. Server
read-header/read/write/idle budgets are 5/10/20/60 seconds, with a 16 KiB header
budget. Uncooperative handlers are not forcibly interrupted. Panics are reported
as defects and produce generic 500 only before response commitment. There are
no application retries.

Helpers used by business endpoints admit one JSON object up to 16 KiB, with one
application/json Content-Type, no unknown fields or trailing values. Go decoder
last-key-wins behavior remains for duplicate members. Domain constructors must
validate admitted values. If-Match admits one strong quoted canonical integer
from 1 through 9007199254740991; the required reader returns 428 when absent.
Idempotency-Key admits one value using the correlation grammar. These readers
do not implement atomic writes, replay, receipts or reconciliation.

The [Account/Audit HTTP workflow](account-audit-v1.md) uses these helpers with
memory/PostgreSQL stores and local event/capability adapters.
[Owned Identity authentication](identity-sessions-v1.md) is explicitly selectable;
CORS/TLS deployment policy, external auth providers and native Go composition remain
unimplemented. PostgreSQL-selected module probes now participate in readiness;
Health itself retains its generic application probe port.
See [runtime behavior](../behavior/go-runtime.md) and [Go setup](../../backend/go/README.md).
