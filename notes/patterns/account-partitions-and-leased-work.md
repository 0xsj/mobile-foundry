# Account partitions and leased work

A stable account partition owns saved data while a short-lived session lease owns permission to start and publish work.

## Origin and evidence

Native account-access batch, 2026-10-10. The [contract](../../contracts/behavior/account-access.md)
and [construction guide](../../docs/blueprints/account-access.md) own behavior and placement.
Shared fixtures, real SQLite files and controlled late completions establish
partition identity and publication guards. Native walkthroughs record execution counts.

## What and why

Data ownership survives access renewal. Partition by authority and account, while
checking the full principal/account/authority/owner/epoch lease around every
operation. Two admitted members of the same account therefore see its collection;
a different authority with the same account identifier sees another collection.
Returning to an account opens a fresh feature owner and reads its saved records.

Keep domain repository ports independent of session machinery. A decorator can
bind one concrete repository and one lease. It checks before work and after
suspension, covering both values and errors. It never looks up the latest account
to decide where an operation already underway should finish. Replacing the owner
also clears native drafts, delete targets, receipts and displayed snapshots.

## Example

```text
Alex: accept create → original Alex repository waits
switch to Sam → close Alex lease/editor → Sam repository loads empty
Alex create finishes → may commit in Alex partition → discard old result
return to Alex → new lease/editor → load the committed note
```

This is deliberately distinct from rollback. A canceled local write or HTTP
request can already have taken effect. Idempotency, uncertain receipts and remote
reconciliation require their own product contract. A 401 is one failure here;
automatic refresh/replay is neither implied nor hidden inside the HTTP boundary.

## Gotchas and actual use

Canonical naming must distinguish case and avoid ambiguous concatenation on
native filesystems. A versioned, delimited ASCII-ID tuple hashed to lowercase
hex provides bounded filenames. Hashing provides naming, not secrecy or access
control. Device records remain after sign-out; Memory records remain only while
the factory owner survives. Guest records occupy a separate unchanged namespace.

The catalog composes account editors inline and keys their identity to the lease.
Pushing a child under a gallery that shuts its session down on disappearance
would immediately close that child's access. Lifetime ownership must be decided
before adding navigation. The HTTP probe is a local wire fixture; its report
contains public metadata rather than tokens or captured request headers.

## Related and next

Read [session lifetime](session-scope-and-credential-lifetime.md),
[commit versus observation](local-commits-and-observation-lifetime.md), then
the [Swift Session](../../frontend/swift/notes/modules/packages/FoundrySession/README.md#authenticated-http)
and [Kotlin Data](../../frontend/kotlin/notes/modules/project/core/data/README.md#account-collections) walkthroughs.
Next: which provider validates membership, how are issuer/audience configured,
and what retention or deletion policy should apply to production device records?
