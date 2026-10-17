# Firebase token collection and callback ordering

Claim: a Firebase Task result and onNewToken callback need an explicit ordering
rule before they can share one current address candidate.

Origin: catalog provider slice, 2026-10-17. Evidence: Android 16 adapter tests with
injected requests and a merged manifest check; SDK dependency is Messaging
26.0.0. This is not cloud-issued token or cold remote-message acceptance.

The Firebase Task is bridged with suspendCancellableCoroutine. Cancellation
stops the app's waiter; it cannot cancel the provider's Task. A thirty-second
withTimeoutOrNull bounds that wait without swallowing a parent timeout. The service callback posts admitted data through
the main Looper, keeping the Services store on the UI dispatcher. The adapter
captures the store revision before the request and publishes its result only
if no newer callback changed that revision.

This avoids the sequence callback(B), completion(A) replacing B with an older
address. It does not create a durable monotonic provider version or prove cloud
ordering across process death. The native SDK owns its own persisted identity.

The catalog generates standard public Firebase resources from four optional
Gradle properties. A configured FirebaseInitProvider can construct the default
SDK on launch, which also gives cold service startup a default Firebase app.
Empty resources leave the default build unconfigured. Manifest policy disables
token auto-init, Analytics collection, notification delegation and FID mode.
The explicit collection action also turns off any persisted auto-init override.
Adding Analytics or changing provider setup requires rechecking that policy.

SDK 25.1 introduced FID registration and deprecated the earlier token APIs;
26.0.0 retains a legacy-token path when FID mode is disabled. This catalog uses
that compatibility path because its registry v1 represents registration tokens.
An FID is not silently interchangeable with a legacy send address. The choice
is isolated in the app adapter; Services does not depend on Firebase. Next:
choose a sender API/address contract before migrating registration mode.

Related: [provider ownership](../../../../notes/patterns/provider-addresses-and-registration-intents.md),
[construction](../../../../docs/blueprints/push-tokens.md),
[official API](https://firebase.google.com/docs/reference/android/com/google/firebase/messaging/FirebaseMessaging)
and [release notes](https://firebase.google.com/support/release-notes/android).
