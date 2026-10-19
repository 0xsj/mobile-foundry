# Producer receipts and consumer views

Claim: composing two confirmed features should preserve their independent
evidence and recovery, rather than promote a producer's receipt into a consumer's
state.

Origin: optional Files + Inbox product composition, 2026-10-19. Source behavior
is in [the contract](../../contracts/behavior/connected-media.md); actual selected
checks and limits are recorded in [construction](../../docs/blueprints/connected-media.md).
This builds on [file recovery](file-receipts-and-byte-recovery.md) and
[inbox readback](inbox-refresh-and-setting-uncertainty.md).

## What and why

A successful completion receipt proves the Files use case accepted that command.
Notifications may consume its committed fact later, suppress delivery under the
recipient's current preference, or still be retrying its own transaction. Adding
a local notice immediately would invent a delivery outcome. A receipt can also
outlive deletion of the file; it is historical producer evidence.

Conceptual:

```text
Files completion + fact commit → receipt acknowledged or lost
  → Notifications consumes fact → accepted or suppressed decision
  → UI reads current Inbox → displays server notices
```

The native composition therefore connects owners through the account/session
boundary and navigation, not by having FilesStore append to InboxStore. Opening
Activity refreshes its port; a manual refresh handles later delivery. Rebinding
both to one lease supplies common authority without merging their local state.

## Different uncertainty rules stay different

Files retains exact accepted bytes/keys and checks completion receipts before
another PUT. Inbox read/settings writes have no receipts/CAS; failed replies
require a current-state read. A shared generic "retry everything" controller would
erase that distinction. Tab/foreground reads can refresh state, but cannot cancel
or replay a pending mutation. Each owner's busy and generation guards remain in
force after composition.

The CLI uses complete explicit manifests because arbitrary overlay ordering could
silently replace one session model/root with the other. Only the combined
composition files are new; the existing feature owners are selected unchanged.
Copy tests assert their bytes and backend source equality. Native acceptance is
still required to prove both owners work together through lifecycle transitions.

## Evidence, gotchas and next questions

The new screen scenario starts with an empty inbox, uploads through the actual
photo picker and observes backend-created notices. Three uploads around a
false/true media preference transition must produce two notices; the muted middle
decision must not reappear after enabling. Process restoration while muted also
checks server preferences without replay. A local proxy loses committed replies
and counts original requests independently of UI assertions.

Delivery is asynchronous: a missing row immediately after completion is not
evidence of suppression. Preferences apply when the consumer decides, not to an
imagined atomic mobile upload/notice transaction. Tests use explicit UI reads for
eventual positive delivery and preserve the muted state through restoration.
Shared backend conformance separately establishes suppression/replay atomicity.

Next: durable staged file bytes and retention of cleanup responsibility; additional notice
kinds need deliberate native admission and destinations, rather than arbitrary
payload navigation. Neither this composition nor a passing memory API run
establishes physical-device push or retained-database native restart behavior.

## Durable product follow-up — CLI 0.25.0

The generated Files product now uses [atomic disk staging](atomic-byte-staging-before-transport.md) before HTTP. Earlier ephemeral-lifetime observations describe the original extraction/catalog owner. Receipt/cleanup/abandonment distinctions still apply; Forget must now acknowledge exact journal removal before releasing pending state. This remains manual one-item recovery rather than a transfer queue or secure erasure.
