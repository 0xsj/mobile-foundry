# Local commits and observation lifetime

A confirmed local write and a screen's current read snapshot have separate lifetimes.

## Origin and evidence

The first durable Notes slice, 2026-10-10, extends the existing service, query and
mutation boundaries. Source inspection, real SQLite file tests and deliberate
late-result tests establish the behavior described here. This is local storage
evidence; it establishes no remote synchronization protocol.

## What and why

Keep admitted commands and returned domain values behind repository ports. The
screen chooses neither SQL nor SDK types. Memory and disk can then exercise the
same feature, while read-only callers keep their smaller service interface.

A write can commit before its caller stops observing. Cancellation or closing a
route must prevent late presentation, but cannot promise to undo that commit.
After acknowledgement, retain the receipt while reloading the list. If that read
fails, offer refresh and preserve the successful write instead of replaying it.

For one feature owner, explicit reload after each write is a useful first
observation policy. Multiple concurrent consumers would need an explicit
invalidation or observation contract; a SQLite file alone does not provide one.

## Example

```text
validated command → repository commit → retained receipt → fresh list
                                               └ read fails: keep receipt,
                                                 retain prior list, offer refresh
route closes → invalidate result generation → stop presenting that operation
new route opens → read the database again → observe any completed commit
```

The draft is transient; a saved record has stable identity and insertion position.
Editing changes its title without moving it. Confirming deletion captures one
identity, rather than whichever row happens to occupy a screen position later.

## Gotchas

- Owned snapshots prevent callers from mutating repository state accidentally.
- Atomic writes do not make duplicate create requests idempotent.
- Schema versions describe stored data; an unfamiliar version is a refusal,
  not permission to reset the database.
- Domain admission still applies to decoded rows. Refuse a malformed snapshot
  rather than publishing a partially valid list.
- A new repository handle tests file lifetime. A process relaunch is separate
  evidence, and neither establishes backup, power-loss or device performance.

## Used in and verification

Follow the [Swift Data walkthrough](../../frontend/swift/notes/modules/packages/FoundryData/README.md)
and [Kotlin Data walkthrough](../../frontend/kotlin/notes/modules/project/core/data/README.md),
then their Local notes consumers. Shared fixtures include preserved whitespace,
Unicode, quotes, newline and embedded NUL. Both feature owners exercise a
dependency that ignores cancellation and commits late, plus a successful write
whose subsequent read fails. Their notebooks record native execution separately.

## Related and next questions

[Forms and mutations](forms-and-mutation-ownership.md),
[query state](query-state-and-rendering.md),
[behavior](../../contracts/behavior/local-notes.md) and
[construction](../../docs/blueprints/local-notes.md).

Next: when do multiple consumers justify database observation, how should guest
records acquire account scope, and which writes need an outbox before remote sync?
