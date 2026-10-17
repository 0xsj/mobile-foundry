# Identity recovery integration facts

The following are new v1 names, not changes to existing v1 credential events:

| Name | Stream/version | Data |
| --- | --- | --- |
| identity.email.verified.v1 | Principal revision ≥2 | principal_id, version |
| identity.password.changed.v1 | Principal revision ≥2 | principal_id, version |
| identity.password.recovered.v1 | Principal revision ≥2 | principal_id, version |

Producer/actor is Identity/the affected principal. Account and session IDs are
absent. Audit admits these facts into principal evidence. No contact address,
credential/hash, token/digest or message content is included.

`identity.session.revoked.v2` retains principal_id, session_id, session version ≥2
and reason, but admits only password_change/password_reset. The existing
identity.session.revoked.v1 remains limited to sign_out/refresh_reuse. This new
name avoids silently expanding a closed v1 reason vocabulary.

Audit's receiver maps both revocation schemas into its existing canonical evidence
action and validates each schema's reason vocabulary. Notifications' independent
receiver maps v2 into its own session retirement input and existing atomic inbox/
tombstone/token cleanup. Wrong-schema reasons are refused. Domain state/outbox and
consumer state/inbox remain atomic; transport/receiving adapters own wire mapping.
