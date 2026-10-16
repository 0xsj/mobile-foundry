# Identity lifecycle facts v1

Producer: `identity`. Independent receiver: `audit.identity`. Publication is
after the producer state/outbox commit, through the existing Publisher port.
Stable identity and consumer inbox deduplication survive retries/restarts.

| Name | Aggregate version | Data |
| --- | --- | --- |
| `identity.principal.registered.v1` | 1 | `principal_id`, `version` |
| `identity.session.issued.v1` | 1 | `principal_id`, `session_id`, `version` |
| `identity.session.renewed.v1` | ≥2 | `principal_id`, `session_id`, `version` |
| `identity.session.revoked.v1` | ≥2 | Above plus `reason`: `sign_out` or `refresh_reuse` |

The envelope actor equals principal ID; account ID is omitted. Occurrence time
comes from the canonical committed transition. Event ID, source version and
request/correlation metadata retain their existing envelope validation. Data
must agree with the envelope; unexpected fields, mixed scopes, wrong actor/
version and unknown action/reason are rejected by Audit.

No login, display name, password, password hash, access/refresh secret or token
digest belongs in the fact. Failed sign-in/registration attempts are not audited
in this subset. Session streams preserve pending delivery order in PostgreSQL;
there is no global ordering promise across principals/sessions/modules.

The generic envelope now permits omitted account ID for non-account facts;
account-specific receiving/domain contracts continue to require a real account
ID. Audit represents principal scope explicitly instead of fabricating an account.
