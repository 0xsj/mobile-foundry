# identity.session.eligible.v1

Provider: Identity. Allowed caller: notifications only. This is an internal
point-in-time answer about an owned session, not access-token authentication or
notification permission. The API edge and other modules cannot invoke it.
It uses the existing caller policy, strict bounded object decoder, two-second
deadline and public failure boundary.

Request: principal_id and session_id, canonical nonzero UUIDs. No access/refresh
token, installation proof or account ID is accepted. Unknown fields and malformed
or missing IDs fail. Duplicate fields retain the existing decoder's last-key-wins
behavior.

Response: exactly eligible, a required boolean. True means the session belongs
to that principal, has started, is not revoked and remains before its absolute
refresh-session expiry. Short access-token expiry does not make it ineligible.
Renewal preserves the session ID. This query never renews/resolves credentials.

Missing sessions, mismatched principals, revoked sessions and absolute expiry
return false. Dependency/store failures and corrupt state are errors, never
negative answers. Consumers reject missing/null/non-boolean responses.

Identity alone reads its owned store and injected clock. No Notifications table,
aggregate or foreign transaction is involved. True is neither a lease nor a
promise that revocation cannot race an external send. Read
[dispatch behavior](../behavior/push-dispatch.md).

The existing Identity module registers this capability. Sessionless/fixture
profiles do not acquire Identity implicitly. Optional push composition must
refuse a missing provider. Credential v1/v2 and public auth responses are unchanged.
