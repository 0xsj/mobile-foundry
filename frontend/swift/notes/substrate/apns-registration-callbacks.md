# APNs registration callbacks

Claim: APNs registration callbacks describe the app's provider address and do
not carry the UI request or authenticated session that happened to be active.

Origin: catalog token adapter, 2026-10-17; Apple UserNotifications/UIKit
registration documentation, Swift 6 mode and iOS 26.2 simulator callback tests.
Evidence is an injected registration closure plus real delegate wiring/build,
not an APNs-issued simulator/device token.

The retained UIApplicationDelegate receives device-token Data or an error. Keep
that bridge on the main actor with its process store. Encode each byte as two
lowercase hex digits, preserving leading zeroes; do not parse Data.description
or assume a permanently fixed token length. A callback with zero/oversized bytes
is refused. The registry's bounded opaque-address contract remains separate
from provider authenticity.

A CheckedContinuation bridges an explicit registration action to async code.
Store it before invoking UIKit, then remove it before resuming. One owned timer
bounds waiting; both cancellation and timeout remove the matching waiter, so
a late callback cannot double-resume it. The callback may still update the
process candidate. Canceling a Task cannot cancel an already-started APNs
registration, and callback order does not establish a backend acknowledgement.

The closure/continuation bridge is used by the catalog; an AsyncStream could
model a broader provider event history, but this feature needs only the latest
address and one explicit waiter. Main-actor isolation handles publication; it
does not invent callback correlation.

Signing is another boundary. The declared route must match the signed
aps-environment, which may differ after archive export. Never guess sandbox
from DEBUG or enable a capability merely to make an unsigned simulator test
appear configured. See [setup](../../../../docs/blueprints/push-tokens.md) and
[Apple's registration guide](https://developer.apple.com/documentation/usernotifications/registering-your-app-with-apns).
Next: verify a correctly signed physical-device token before any delivery claim.
