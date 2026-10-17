# Native identity lifecycle and account live hints

Daily + go-connected selects these Swift/Kotlin capabilities through CLI 0.15.0.
Catalog sign-in examples and other shell/provider combinations are separate.
The backend contracts remain [identity recovery](identity-recovery.md) and
[Realtime](realtime.md); grants, receipts and stored credentials keep their formats.

## Identity

IdentityRecovery is a swappable port. Its HTTP adapter separates public code/reset
requests from contact/request-verification/password-change requests using an
exact-lease authenticated client. Codes have distinct e1/p1 purposes. Malformed
codes, oversized addresses and invalid password bounds are refused before I/O.
Passwords preserve submitted bytes and require twelve Unicode scalars through
256 UTF-8 bytes. Backend mailbox policy remains authoritative. Contact projection
admits only the defined boolean/string/time shape.

Account opens a transient security form for verification/change when signed in,
recovery otherwise. Password/code drafts are never saved, logged or persisted.
They clear on submit, session transition, background and dismissal. Responses use
public failures and generic reset-request wording. No mutation automatically
retries. Contact readback after verification is explicit.

Confirmed password change clears local refresh credentials through the session
owner and returns to sign-in. An interrupted or unconfirmed submitted change also
closes local access conservatively; known invalid input can retain the session.
The user signs in or uses recovery to resolve uncertainty. Generation/cancellation
checks prevent a late reply affecting a replacement session. Reset is available
while signed out and never implicitly signs in. Secure-storage clearing failure
cannot claim completed sign-out. Multiwindow ownership remains separate.

## Live reads

AccountLiveHints is separate from CRUD and recovery. Its Go implementation admits
only the configured deployment's exact session lease/account. It uses a Bearer
header, no query credentials/cookies, and refuses redirects. Text frames must be
at most 1024 UTF-8 bytes with exactly type/resource/account_id/refetch, resource
notes, matching account and boolean true. Ready comes first and once; subsequent
frames are changed. Hints carry no record content or account authority.

URLSession/OkHttp adapters own socket resources and cancellation. Swift bounds
native receive buffering. OkHttp admits messages after its callback materializes
them and buffers at most sixteen pending callbacks; this is not a pre-allocation
byte cap against an arbitrary hostile peer. It connects only to the explicit API.
Neither adapter silently renews or replays requests.

The foreground product owner reconnects with bounded backoff/jitter, at most six
attempts per bind/manual reconnect. Background, account/session replacement and
renewal cancel old sockets. Foreground/new leases create fresh owners; ready
requests refetch. The UI exposes status/Reconnect and ordinary HTTP Refresh.

Notes coalesces hints during an active operation. Accepted writes and journal
settlement finish before one queued read for that burst. Reads retain drafts and
pending commands and never execute them. Old-scope work cannot populate new owners.
This does not add durable delivery, merging, multi-item sync, presence or background
networking.

## Acceptance

The copied driver owns API, loopback in-memory SMTP/code capture, fault/WebSocket
proxy and explicit device mappings. Capture is test tooling, never a production
endpoint. Shared vectors/unit checks cover admission and stale/canceled work.
Real transports and generated screens separately verify lifecycle forms and an
external write → hint → HTTP read before promoting this scope. External SMTP,
deployment and physical-device networking require separate acceptance.
