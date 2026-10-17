# Provider HTTP, signing and credential caches

Claim: provider-specific signing, HTTP policy and response admission belong in
the adapter while application orchestration retains ownership/version policy.

Origin: Go preview providers, 2026-10-17. Primary references:
[APNs token authentication](https://developer.apple.com/documentation/usernotifications/establishing-a-token-based-connection-to-apns),
[APNs responses](https://developer.apple.com/documentation/usernotifications/handling-notification-responses-from-apns),
[FCM authorization](https://firebase.google.com/docs/cloud-messaging/send/v1-api)
and [FCM errors](https://firebase.google.com/docs/cloud-messaging/error-codes).
Observed evidence uses Go's crypto/net/http, generated test keys and local TLS
HTTP/2 endpoints, not a real provider account.

## Mechanics and choices

PEM is an envelope around DER bytes; PKCS8 identifies the private-key structure.
Parsing these layers does not make a key suitable for every protocol. APNs needs
a P-256 ECDSA key; service-account OAuth uses RSA. The standard library performs
the cryptographic signing. Protocol code supplies fixed headers/claims and
base64url encoding rather than inventing a cryptographic primitive.

ES256 JWT signatures contain fixed-width raw R followed by S. An ASN.1 ECDSA
signature is not the same wire representation. Local verification splits those
64 bytes and verifies the signature over the actual JWT signing input. OAuth
assertions use RS256 and the messaging scope; the fixed Google endpoint exchanges
them for a short-lived bearer token before a message is submitted.

APNs caches its locally signed authorization value under a short mutex. FCM can
wait on a network exchange, so a channel semaphore plus select on context.Done
coalesces refresh while keeping waiting callers cancellable. Neither is a store
transaction or a cross-module lock. Caches use injected time and avoid reuse
after clock rollback/expiry. They are process-only, not a new credential vault.

The HTTP client uses TLS, HTTP/2 negotiation, finite timeouts and no redirects.
Responses have a bounded read and native errors/bodies are replaced with owned
failure values. This prevents an APNs token embedded in an error URL from leaking
through ordinary Go error formatting. There is no automatic send retry after a
401, 429, connection failure or uncertain acknowledgement.

Used by the optional Notifications gateway. Local tests verify request shape,
signatures, cache reuse/expiry/concurrency, response decisions, redirect refusal
and in-flight cancellation. Physical devices and cloud-issued tokens remain
unverified. Next: choose durable attempt semantics before background delivery.
Related: [provider evidence](../patterns/provider-errors-and-address-retirement.md)
and [module walkthrough](../modules/internal/notifications/providers.md).
