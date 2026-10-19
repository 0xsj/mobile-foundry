# Connected account inbox

Established 2026-10-19. CLI 0.21.0 adds an explicit inbox capability to Daily + go-connected + Go or
Daily + rust-connected + Rust. Swift, Kotlin and combined outputs select the
same native ports, state and UI for both backends. Other shells, inbox alone,
local-data/inbox and mixed connected-backend selections are refused before copying.
Omitting inbox retains the existing Activity placeholder and narrow service closure.

The capability selects NotificationInboxReader/Writer, their composed repository,
the one-attempt HTTP adapter, shared admission helper and notifications fixtures.
Inbox alone does not select native Files; the explicit
[combined profile](connected-media.md) composes them. System notifications and
provider SDKs are separate. Existing
backend upload-completion events populate in-app notices. DTO/type admission
remains the [Notifications contract](notifications.md); new types need explicit
backend mapping and native admission rather than arbitrary payload rendering.

Activity owns presentation. InboxStore owns ephemeral read/filter/page/write state
and injects NotificationInboxRepository. ConnectedSessionModel binds the HTTP
repository through the exact current SessionLease, including same-account renewal.
Every detach/bind cancels old work, advances the owner generation and clears rows,
counts, preferences and cursors. A late reply cannot publish into another owner.
Foreground/session restoration reads current server state. No inbox disk cache,
background scheduler or mutation queue is selected.

Pages contain 20 items; at most 200 are retained. Server order/count are authoritative.
A full refresh replaces rows and reads media preferences before enabling changes.
Unread filtering resets pagination and performs that full read. Duplicate items
across pages stop paging until a successful refresh. New arrivals require manual
refresh or lifecycle rebind; Notes live hints do not become an Inbox feed.

Mark-read and media preference assignment wait for acknowledgement and a fresh
read. Their ports do not offer receipts/CAS. An unconfirmed write keeps the prior
display, blocks further mutations and asks for current-state refresh. Failed
refresh retains that gate; success restores controls from server observations.
There is no automatic resend, optimistic badge decrement or durable write intent.
Sign-out hides rows immediately; app relaunch restores the session and reads anew.

The copied acceptance driver owns temporary users and real upload-completion
events, not product debug hooks. For each native platform it seeds 25 notices
through the selected API, loses one committed read and preference reply, then
checks exactly one attempt/commit per operation across readback and restoration.
Actual screens exercise paging, filtering, account isolation, renewal and process
relaunch. Kotlin separately recreates the Activity and force-stops the process.
Required selected tests must run with no failures/skips before readiness promotion.

Read [construction/evidence](../../docs/blueprints/connected-inbox.md).
Fresh Go and Rust products pass both native platforms: Go's inbox acceptance
and Rust's full connected suite, including inbox, with no selected failures/skips.
The capability is promoted only after that independent generated execution.
Local memory acceptance does not establish connected PostgreSQL restart behavior,
offline inbox, public deployment, live OS push, richer notification types/audiences
or a retention/erasure policy. Existing standalone backend acceptance is separate.
