# Composer drafts and transfer ownership

Claim: an editable draft, admitted attachment, transfer operation and delivered
message have different lifetimes, so a reusable composer must project caller
values rather than own the transaction.

## Origin and evidence

Added 2026-10-09 for the ninth UI batch. Native source and consumer checks exercise
one local conversation fixture. Native walkthroughs record actual execution and
limits. No file admission, upload service, outbox or durable message store exists
in this batch; the existing service/query/mutation boundaries remain available.

## What and why

MessageComposer renders a native multiline field, supplied attachment/actions,
send eligibility and busy/disabled state. The app holds text above the route and
decides what can be sent. A UI control cannot know whether a blank text is valid:
an attachment-only message may be useful, while an incomplete file can make a
nonblank text temporarily ineligible. Admission belongs with the feature.

The example admits a nonblank text or a completed fixture attachment. Adding the
attachment blocks sending until it is ready. Failed/paused transfer state keeps
both text and file intent. Cancel removes the attachment while preserving text;
retry changes transfer presentation without replacing the draft. Successful local
send appends a preview and only then clears the draft/attachment. Disabled callbacks
are also guarded at the app boundary. These rules are example policy, not shared
component defaults.

Example: write a reply, attach a brief, fail its transfer and go back to another
family. Reopening the conversation should recover the reply and failure status.
Retry and finish the file, then send once; a second activation sees an empty draft
and cannot append a duplicate local preview.

MessageBubble's direction is logical placement, not evidence that delivery
succeeded. Its metadata is caller copy. AttachmentRow's passive preview and
independent action slot keep Inspect/Remove separate from message text. ConversationRow
has one open action; do not embed controls in its hidden identity artwork.
TypingIndicator's presence belongs to an external participant/event owner; its
decorative pulse is merely presentation.

## Gotchas and alternatives

- A real upload needs stable operation/file identity, cancellation and protection
  against late completions after removal/replacement. The synchronous fixture
  cannot establish those guarantees. Reuse the existing result/mutation seams
  when an actual service is introduced.
- Send failure should not discard the user's draft. If editing is allowed while
  sending, clear only the submitted revision on success; otherwise a completion
  may erase a newer reply. The current composer gates editing while busy.
- Swift current-view state is not durable storage. Android saved-instance state
  restores the nonsecret fixture draft/primitive values, but is not a message cache.
  Never extrapolate this choice to sensitive or arbitrarily large content.
- Message lists need stable domain IDs when reordered, edited or deleted. Kotlin's
  append-only fixture uses positions; Swift's fixture assigns UUIDs. A real history
  needs paging, ordering and scroll-anchor policy above these display components.
- Long transcript/recovery content belongs in scrolling space. Keep pinned slots
  compact; keyboard insets and viewport sizing remain screen responsibilities.
- UI-core slots must receive disabled state. Swift inherits it through the subtree;
  Compose slot controls explicitly consume the supplied interactive argument.

## Used in and related

Read [behavior](../../contracts/behavior/ui-components.md#communication-and-attachments),
[usage](../../docs/blueprints/ui-components.md#communication-and-attachments),
[Swift native mechanics](../../frontend/swift/notes/substrate/swiftui-composer-and-safe-area.md)
and [Compose native mechanics](../../frontend/kotlin/notes/substrate/compose-composer-and-ime.md).
Compare [component slots](component-slots-and-caller-owned-state.md) and
[command forms](../../contracts/behavior/forms-mutations.md).
Next: what draft revision and operation identity should a real asynchronous send capture?
