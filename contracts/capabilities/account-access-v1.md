# account.access.v1

Provider: Account. Allowed callers in this profile: `audit`, `notes`, `files`. Internal contract;
there is no public HTTP invocation endpoint. Composition binds the caller's
module identity; input principal/account IDs alone provide no authentication.

Request object, all fields required:

```json
{"principal_id":"00000000-0000-4000-8000-000000000001","account_id":"00000000-0000-4000-8000-000000000002","action":"account.read"}
```

IDs are canonical nonzero UUIDs. Action is `account.read` or `account.rename`.
Unknown fields/invalid IDs/actions are invalid input. Account's current owner
policy answers both actions. Membership/grant extensions remain future work.
Notes and Files request `account.read` as admission to the current owner-only scope for
its reads and mutations. This does not establish read-only member roles; introduce
a deliberate action policy when membership/grants are implemented.

Response object: `{"allowed":true|false}`; the boolean field must be present
and nonnull. Unknown account or a principal without ownership returns an
admitted `false`. Store failure remains failure, never `false`.

Local invocation crosses JSON admission/public failure projection, bounds each
object to 16 KiB and uses a two-second cooperative budget constrained by caller
deadline. There is no automatic retry. The registry enforces allowed callers,
rejects duplicate/late registrations and resolves requirements before workers
start. A malformed/missing response is internal failure; an absent provider is
unavailable. Cancellation retains context identity.

This capability is implemented in-process. Remote transport/service identity
and local/remote conformance are not implemented. See
[module boundaries](../behavior/module-boundaries.md).
