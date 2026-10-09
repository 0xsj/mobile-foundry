# Challenge drafts and attempt identity

Claim: a code draft is input, while a verification attempt must identify the
current challenge and request before a result can change feature state.

Origin/evidence, 2026-10-09: the twenty-third UI batch adds a native code field
and verification card over a local challenge fixture. It exercises input admission,
incorrect/unavailable responses, cancellation, resend and manual expiry. No code
is delivered and no account is authenticated.

## What and why

One native field preserves editing, cursor, paste and platform input hints. A
complete draft enables an explicit action; input and autofill do not automatically
start a command. Preserve leading zeroes as text. Input formatting is separate
from a server deciding whether a challenge is valid.

CodeFormat requires a canonical partial ASCII digit value and a bounded length.
Edits can remove specified separators. Reject an invalid/oversized whole edit
rather than extracting a plausible code from arbitrary text or truncating it.
Empty string is a valid cleared draft; a missing admission result means rejection.
Unicode decimal digits and lookalike characters are deliberately unsupported in
this fixture. A product needing another alphabet should define that contract.

Begin captures an attempt ID, challenge generation, channel and code. Pending
disables relevant controls and commands recheck the same admission. Cancellation
discards the current attempt. A later begin has a different ID even with the same
code, so an old result cannot apply to it. Resend/channel/reset create a new
challenge generation. Completion must match the current request and challenge
and still be enabled/unexpired.

## Example and gotchas

Enter 123-456: the field admits 123456 without submitting. Begin a check, cancel
it, then begin again. Finishing the first attempt changes nothing; the second
attempt can apply its response. An incorrect/unavailable response preserves the
draft for correction/retry. Local success clears the code.

The preview advances cooldown/lifetime manually. At expiry it discards pending
and draft; resend starts a fresh generation and resets the timers. Remaining
seconds are demonstration values, not a durable or authoritative deadline. A real
service owns challenge ID, delivery, rate limits, expiration and verification.
Local UI clocks, saved flags or a matching demo string cannot authorize auth.

Android saves nonsecret choices/times/counters but keeps code, pending attempt,
error and success presentation transient. Recreation returns to empty entry
without replaying a check. In-memory route/theme changes retain the draft because
the feature owner stays composed. These are different lifetimes; neither is an
auth-session persistence strategy. A native autofill hint also does not fetch SMS
or request message access in this code.

## Actual use and next questions

Read [Swift Verification flow](../../frontend/swift/notes/modules/apps/FoundryCatalog/README.md#verification-gallery),
[Kotlin Verification flow](../../frontend/kotlin/notes/modules/project/app/README.md#verification-gallery),
[usage](../../docs/blueprints/ui-components.md#verification-and-code-entry) and
[behavior](../../contracts/behavior/ui-components.md#verification-and-code-entry).
Related: [forms/mutation ownership](forms-and-mutation-ownership.md),
[account scope](account-context-and-device-capabilities.md) and
[query presentation](query-state-and-rendering.md).
Next: use provider challenge identity/deadlines and value-based service outcomes;
observe native autofill separately from admitted paste and button/IME behavior.
