# identity.credential.resolve.v2

Provider: Identity. Allowed caller: api. Request: access_token, with the same
bounded object/caller/deadline/failure contract as
[v1](identity-credential-v1.md). Response: principal_id, session_id and
session_expires_at (the refresh session's absolute UTC expiry, not access expiry).
All IDs are canonical nonzero UUIDs. Current access-token validity, rotation,
revocation and expiry are checked by Identity's owned store/security ports.

The process-selected principal resolver now uses v2 and carries this trusted
session evidence. Installation requests never accept principal/session/expiry from
JSON or headers. Development fixtures have no Identity session and cannot register
installations. There is no successful anonymous/negative resolution or fallback
to sessionless authentication when v2 fails.

v1 remains registered and returns exactly its original principal-only response.
Strict v1 clients continue to work. New application adapters must implement
SessionAccess to compose the v2 provider. No public sign-in/renewal response changes.
Tokens never enter diagnostics, events or the successful capability response.
