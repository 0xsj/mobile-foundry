# Native provider token collection

Swift and Kotlin expose an optional catalog capability for APNs/FCM address
collection. This is separate from [OS notification permission](system-notifications.md),
[installation proof/registration](installations.md), account inbox preferences,
authentication and sending a push.

## Ownership

Services owns PushTokenPort, PushTokenStore and the screen-owned request
controller. Native applications own provider SDKs/delegate bridges. Every store
access is confined to the native UI executor. No provider SDK enters Services,
HTTP, Session, UI or the backend domain.

Collection happens on explicit action only, with one active request per adapter,
a thirty-second native wait limit and no application retry. Known missing setup
and provider failures are values; cancellation propagates; defects reach the
injected reporting hook and publish a generic message. Provider SDKs/OS may have
their own transport retries. Closing a screen cancels its waiter and prevents
late UI request state, but cannot undo a provider operation already started.

The process-owned store holds only the latest admitted DeliveryTarget and a
local revision. Equal targets do not advance it; explicit invalidations do, even
while empty, so an older completion cannot resurrect a refused address. Revisions are ephemeral change
markers, not server versions or receipts. Tokens are opaque and bounded by the
existing registry contract; APNs bytes become lowercase hex without assuming a
fixed byte count. Diagnostics/UI redact token values. The application does not
persist or expose a copy-token action; the native provider SDK may persist its
own credentials/address. Rotation is process state, not durable native sync.

The store can outlive a screen and receive callbacks after a waiter ends. It is
app/provider configuration owned, never user/account/session owned. Sign-out
does not delete a provider token. Sign-out retirement remains the backend's
Identity revocation workflow. A later token rotation neither rebinds nor
unregisters an installation automatically.

## Native selection

iOS keeps a single APNs adapter beside the retained application delegate.
It registers only after Collect device token. Missing CatalogAPNsEnvironment
configuration is unconfigured; it never guesses from Debug/Release. The chosen
sandbox/production route must match the actual signed aps-environment and app
identifier. No entitlement or provisioning profile is enabled by default.
Registration failure/timeout exposes no native error details. APNs callbacks
carry no request/session ID: a late callback can update the process candidate,
but cannot acknowledge an auth-bound backend write.

Android uses the official Firebase Messaging main SDK in the catalog app only.
Four optional public app configuration properties generate standard Firebase
resources. With empty defaults, collection returns unconfigured. A configured
FirebaseInitProvider may construct the SDK at launch, including cold service
startup; token auto-initialization, Analytics collection and notification
delegation remain disabled. Explicit collection also disables any previously
persisted auto-init preference. It checks Play services before requesting an
address. onNewToken transfers the admitted target to the UI executor. A newer
callback revision wins over an older collection Task result.

Registry v1 uses FCM registration tokens. SDK 26.0.0's FID registration mode is
explicitly disabled, and the supported legacy token methods are isolated in
the adapter. FIDs must not silently enter that route; changing sender/address
semantics requires a separate integration contract. This slice does not add a
remote-message presentation/navigation handler.

## Backend handoff

System notifications offers collection without login. Go identity and accounts
also offers Register collected token. That explicit action snapshots the latest
candidate into the existing InstallationController, whose exact lease, proof,
CAS and uncertain-write readback rules still apply. A provider callback has no
reference to the registry. An in-flight registration retains its original target;
a newer address needs another deliberate registration after reconciliation.

Manual registry probes remain separate controls and never establish a genuine
provider address. Neither a collected token nor a saved registry record proves
provider delivery, notification display or current permission.

## Verification boundary

Host tests establish explicit ownership, rotation/deduplication, cancellation,
failure/defect handling and separate registry writes. Native tests establish
APNs byte/callback admission and timeout cleanup with injected native requests,
FCM collection races with an injected request, merged Android policy and the
default unconfigured UI. Cloud-issued tokens, signed device collection, external
sending, remote opens and generated connected CLI profiles require separate
acceptance. Follow [setup and evidence](../../docs/blueprints/push-tokens.md).
