# Provider HTTP defaults are part of the attempt contract

Claim: “one send attempt” requires reviewing the HTTP library's transport behavior
as well as counting application calls.

Origin/evidence: 2026-10-18 source inspection and local execution with Reqwest
0.12.28, Hyper 1.11.1, Rustls 0.23 and Ring 0.17.14. Read the pinned
[ClientBuilder API](https://docs.rs/reqwest/0.12.28/reqwest/struct.ClientBuilder.html),
[Apple token authentication](https://developer.apple.com/documentation/UserNotifications/establishing-a-token-based-connection-to-apns),
[Apple requests](https://developer.apple.com/documentation/usernotifications/sending-notification-requests-to-apns),
[FCM v1](https://firebase.google.com/docs/cloud-messaging/send/v1-api) and
[FCM error evidence](https://firebase.google.com/docs/cloud-messaging/error-codes).
Those sources define protocol behavior; local tests establish only this adapter's
execution, not real cloud delivery.

Reqwest's defaults include retries for selected protocol errors. The
[provider builder](../../internal/notifications/src/infra/push/mod.rs) explicitly
selects retry::never, redirect::Policy::none, HTTPS only, TLS 1.2 minimum,
no ambient proxy and bounded connect/request time. The APNs client requires
HTTP/2; FCM negotiates normally. Separate clients retain their pools under one
composition owner. Closing drops their idle pool ownership, and retained gateway
handles refuse new work. Internal library connection drivers are not application
JoinHandles; the code does not claim to join them directly.

Example (Conceptual): if a provider accepts a POST and the acknowledgement is
lost, sending the same bytes again may create a second notification. An HTTP
library retry would violate the use case even if application code invoked send
only once. The preview UUID is correlation metadata, not an idempotency receipt.

Reply size is checked while consuming chunks, including unrecognized errors.
Provider URLs/bodies/errors are discarded instead of retained as Failure causes.
Authorization headers are marked sensitive, but private buffers and parsed signing
keys still exist in process memory. Zeroizing explicitly owned buffers is useful;
it is not evidence of total process-memory erasure.

[Local TLS tests](../../internal/notifications/src/infra/push/tests/protocol.rs)
use generated certificates trusted only by test-local builders and validate
hostname/trust failures. Test server tasks own and join their connection set;
production configuration exposes no endpoint or trust override. Actual cloud,
proxy, OS trust-store variation and provider outage behavior need separate
deployment evidence. Related: [provider walkthrough](../modules/internal/notifications/providers.md),
[known rejection](../patterns/known-rejection-and-unconfirmed-cleanup.md).
