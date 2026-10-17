# Provider adapters and the guarded preview route

Claim: a selected provider can execute a preview without giving native input
authority over routing credentials, another session's target or cleanup policy.

Origin: optional provider slice, 2026-10-17. Evidence/limits are recorded in
[setup](../../../../../../docs/blueprints/push-providers.md). Local TLS providers
and injected HTTP gateway results do not establish live cloud delivery.

## Read in order

1. [Gateway/configuration](../../../../internal/notifications/infra/push/gateway.go).
2. [APNs](../../../../internal/notifications/infra/push/apns.go) and
   [FCM](../../../../internal/notifications/infra/push/fcm.go).
3. [Root loader](../../../../internal/notifications/push_provider.go),
   [module wiring](../../../../internal/notifications/module.go) and
   [HTTP route](../../../../internal/notifications/transport/http/v1/push.go).
4. [Protocol checks](../../../../internal/notifications/infra/push/gateway_test.go)
   and [Identity/proof/version HTTP checks](../../../../tests/push_http_test.go).

## Control and data flow

cmd reads PUSH_CONFIG_FILE from the startup snapshot and calls the owning module
facade. Empty selects no gateway; selected setup requires Identity. Bounded config
and credential files become a route map with fixed provider endpoints. Loading
does not contact either provider. Memory/PostgreSQL builders add the eligibility
requirement and preview route only when a gateway exists.

The route requires current Identity auth, installation proof, If-Match and a
preview UUID. It resolves session ownership from the edge, never request JSON.
GetInstallation proves possession; a separate owner/version check prevents a proof
holder from sending under another session. The existing command then performs
session eligibility and exact target rechecks before the provider call.

PushAddress now carries RegisteredAt from the aggregate's last binding update.
This detached metadata lets APNs 410 admission refuse evidence older than a newer
registration. It does not replace revision/owner guards. Both stores still erase
a definitive rejected token only under the original lease, outside provider work.

Provider errors never carry the raw response or native URL cause. APNs sends a
fixed alert over HTTP/2; FCM authorizes via OAuth and sends registration-token
HTTP v1 with an exact package restriction. Both carry only generic text and the
versioned preview hint. Public success contains preview ID/disposition/retired,
not a registry ETag or receipt. A second identical preview request can send twice.

The local protocol suite generates its keys and verifies signatures, requests,
outcomes, timestamps, caching, redirects and cancellation. HTTP tests exercise
the selected route with real Identity credentials and an injected gateway; the
disposable DB suite selects PostgreSQL builders for that path. Existing dispatch
conformance still verifies no provider call occurs under the registry guard.

The CLI includes the example and adapter closure, prunes .p8/private credential
files, and generates matching ignore/setup guidance. No key is provisioned and
all cloud behavior remains unverified. Native remote presentation/navigation and
durable jobs are still separate. Next: admit the remote preview in native apps.
Related: [provider evidence](../../../patterns/provider-errors-and-address-retirement.md)
and [protocol mechanics](../../../substrate/provider-http-and-jwt.md).
