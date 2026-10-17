# Device notifications and navigation hints

An OS notification tap should supply an admitted navigation intent, while current
application state supplies any authority to read or change data.

## Origin and evidence

The 2026-10-17 system notification slice adds local previews after the connected
inbox and installation registry. Shared fixtures and owner tests cover payload
admission, explicit permission requests, posting failure and late completions.
Native verification and its limits are recorded in
[the construction guide](../../docs/blueprints/system-notifications.md).

## What and why

A user can grant device notification permission while disabling a category in an
account inbox, or keep an installation token while denying visible alerts. Do not
make any of these values an alias for another. Read OS settings again after
foreground return, and let the user request permission deliberately.

The operating system can present a notification after the screen owner has gone
away. A response can arrive before the root navigation tree exists. Put the
pending navigation intent in a small app-lifetime owner, then let navigation
consume that exact intent after it adds/selects the destination. Keep native
payload objects outside portable state and keep reusable UI passive.

## Example

Conceptual sequence:

```text
explicit user action -> request native permission -> read current settings
explicit preview -> OS accepts local notification -> screen closes
OS default tap -> validate version/kind/UUID -> pending intent
root navigation becomes ready -> select notification screen -> consume same ID
```

The local preview has no account authority. A future remote tap should similarly
be admitted first, then wait for an appropriate session/scope and fetch current
data through its existing port. Do not infer current access from payload text or
silently switch the user's selected account.

## Gotchas

- Scheduling/posting acknowledgement does not prove a banner appeared. Focus,
  channels, permission changes and quiet delivery can affect presentation.
- Canceling UI work does not undo OS acceptance. A clear action addresses the
  preview's owned slot, rather than deleting all application notifications.
- An immutable Android PendingIntent prevents another sender from filling in
  fields. It does not turn a launch Intent into an authentication credential.
- A fixed preview slot bounds notifications and PendingIntents; replacement can
  update what an old rendering opens. Do not pretend this is immutable history.
- Bounded process deduplication protects navigation from repeated callbacks, not
  business side effects or permanent replay. Those belong to their domain owner.
- Readback after returning from Settings is useful even if a permission callback
  never arrives at a screen that was disposed.

## Used in and related

Read the [Swift Services walkthrough](../../frontend/swift/notes/modules/packages/FoundryServices/README.md)
and [Kotlin Services walkthrough](../../frontend/kotlin/notes/modules/project/core/services/README.md),
then the native app adapters/navigation links. Compare
[installation ownership](installation-identity-and-current-state-reconciliation.md)
and [inbox uncertainty](inbox-refresh-and-setting-uncertainty.md).
Next: what additional account/lease admission must a remote inbox tap perform?
