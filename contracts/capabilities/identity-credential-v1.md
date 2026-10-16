# identity.credential.resolve.v1

Provider: Identity. Allowed caller: `api`, selected by process composition.
The API adapter implements the generic principal Resolver port; handlers do not
import the Identity repository or security adapter.

Request: one `access_token` string. Success: one valid canonical `principal_id`.
The provider verifies token purpose/encoding and current persisted session
expiry/revocation before admitting the principal. There is no successful negative
or anonymous decision. Unaccepted credentials return typed unauthenticated
failure; missing provider, storage failure, timeout and malformed responses stay
distinct failures.

Use the existing bounded object, caller policy, two-second cooperative budget
and public failure projection. Tokens cross this private invocation request and
must never be logged, placed in errors, events or response diagnostics. Remote
routing/service authentication remains unimplemented.
