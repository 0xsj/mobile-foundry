# Identity HTTP v1

Implemented under explicit `AUTH_MODE=identity`. Responses use the shared
JSON/problem, request/correlation metadata and `Cache-Control: no-store` policy.
Read [behavior](../behavior/identity-sessions.md) for semantics and limits.

| Method and path | Admitted input | Success |
| --- | --- | --- |
| POST `/v1/identity/register` | `login`, `display_name`, `password` | 201 principal profile; no automatic sign-in/account |
| POST `/v1/identity/sign-in` | `login`, `password` | 200 session grant |
| POST `/v1/identity/renew` | `refresh_token` | 200 rotated grant |
| POST `/v1/identity/sign-out` | `refresh_token` | 204; repeated known-family sign-out succeeds |
| GET `/v1/identity/me` | One Bearer access credential | 200 principal profile |
| GET `/v1/principals/me/audit` | Bearer access credential; optional `limit`, `cursor` | 200 principal-scoped evidence page |

Credential command bodies are bounded JSON objects; missing/null/wrong-shape,
unknown fields and trailing JSON are refused. Duplicate keys retain the shared
decoder's documented last-key-wins behavior. Credential routes accept no query
parameters. Unsupported Idempotency-Key is refused. The refresh body itself is
proof for renew/sign-out; a principal/session ID or an access token is not a
replacement. No credentials are accepted through URL parameters.

Principal profile contains `id`, canonical `login` and `display_name`.
Grant fields: `authority_id` (`go.identity.v1`), `principal`, `session_id`,
`session_version`, `access_token`, `refresh_token`, `access_expires_at` and
`refresh_expires_at`. Times use UTC RFC 3339 microseconds. It contains no account
selection or password/hash. Identity endpoints emit credentials only in grants.

Invalid registration input/JSON returns 400; duplicate login 409. Wrong/unknown
sign-in credentials, expired/revoked/wrong-purpose tokens and refresh reuse return
401 with the same safe credential refusal. Admission budgets return 429. Storage/
dependency failure remains 5xx; a corrupt hash/state is internal. A consumed
refresh returning 401 may have committed revocation; writes are not replayable.

The Audit page uses `entries` and nullable `next_cursor`. Principal entries carry
sequence/event/actor/principal IDs, optional session ID, action, aggregate version,
occurrence/recording times, optional metadata and revocation reason. Account ID
is omitted. Limits are 1–100/default 50; cursor position is exclusive and bound
to the current principal. Another principal's cursor returns 400, not its data.
HEAD follows GET metadata without a body.
