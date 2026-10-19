# Notice targets and current authority

Claim: an inbox notice identifies something to resolve; current authorized reads,
not the notification or its producer receipt, supply the destination's content.

Origin: CLI 0.26.0 Files + Inbox navigation, 2026-10-19. The existing notice has a
file ID but no current file specification or access grant. Read
[behavior](../../contracts/behavior/notice-destinations.md) and
[execution evidence](../../docs/blueprints/connected-media.md) separately.

## What and why

An upload-ready notice can outlive the file. Its event remains useful history
after another client deletes the target. Scanning the current first page cannot
prove absence, and a completion receipt deliberately returns historical state.
The destination therefore calls the existing metadata endpoint by ID under the
current account's exact session lease. The native adapter validates target,
account, nondeleted status and strong ETag before exposing a domain value.

Conceptual:

```text
current inbox item + explicit Open
  → current lease and scope admission
  → current metadata GET
  → detail / pending file / not found / denied / dependency failure
  → optional explicit verified byte download
```

Opening and marking read are separate product actions. The read-only destination
port cannot upload, delete, settle recovery or mutate the inbox. The app composition
owns the connection between independent feature owners, so opening never has to
cancel an accepted Files command or clear an uncertain inbox preference.

## Native lifetime

Swift's Observation owner and Kotlin's StateFlow owner follow the existing
generation pattern: close/bind increments generation and cancels the old task.
Cancellation is cooperative; a provider may still finish. Publication requires
the original generation and a cancellation check, even on same-scope renewal.
The current composition additionally checks exact lease and current inbox item
membership before admitting Open. No new shared router or navigation framework
is needed for one modal destination. Rebinding deliberately closes it instead
of silently reopening under a new lease.

## Evidence and gotchas

Four owner tests per native implementation cover scope checks and explicit
download, noncooperative metadata after same-scope rebinding, distinct missing/
denied and mismatched metadata, and late/corrupt downloads after close. Seven
adapter scenarios exercise available/pending metadata and invalid identity,
account, deletion or ETag responses; invalid path inputs send no HTTP. Existing
real-backend service flows now read pending/available metadata and deleted 404.

The generated media workflow opens a backend-created notice and verifies its
download without mark-read. A later open races an independent authorized delete,
then receives the real backend 404. The fault proxy counts exactly two metadata
reads, one success and one not-found, and still requires exactly the explicit
mark-read/preferences writes. Exact platform results are recorded in the guide;
this note does not infer OS delivery, deep links or physical-device performance.

## Actual use and next questions

Daily Files + Inbox selects the app-owned FileDestinationStore and screen;
Inbox-only keeps its current UI with no Open action. Both backends reuse the
same native source. More notice kinds should introduce admitted typed targets
and explicit capability selection. Account-switch confirmation and OS push
entry need their own contracts; they should not be inferred from an in-app tap.

Related: [producer and consumer views](producer-receipts-and-consumer-views.md),
[inbox uncertainty](inbox-refresh-and-setting-uncertainty.md),
[byte recovery](file-receipts-and-byte-recovery.md), and
[connected composition](connected-products-and-composition.md).
