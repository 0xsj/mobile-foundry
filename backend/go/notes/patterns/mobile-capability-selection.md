# Selecting domains for connected mobile products

Claim: reusable mobile backends benefit from explicit installation, delivery,
media and purchase ownership, while synchronization rules remain with the data
domain being synchronized.

Origin: 2026-10-17 capability-selection discussion. Evidence combines inspection
of the current [capability catalog](../../CAPABILITIES.md),
[module boundaries](../../../../contracts/behavior/module-boundaries.md) and
[starter readiness](../../../../docs/STARTER-READINESS.md) with the platform
references below. The proposed order is WORKING design guidance, not a claim
that these domains are necessary for every product or already implemented.

## Current baseline and proposed additions

Identity, owner-scoped Account, Audit and the Notes exemplar are implemented.
They do not establish recovery/verification, team membership/invitations,
production operations or connected generated native acceptance. Those existing
owners should gain the relevant workflows when selected by a product.

| Candidate | Rules and data it could own | Selection guidance |
| --- | --- | --- |
| Installations | App-generated installation identity, association with the current principal, app/platform metadata and retirement | Useful when multiple app installations need distinct lifecycle rules; no hardware fingerprint is required |
| Notifications | Inbox/read state, categories/preferences, delivery registrations, attempts and channel adapters | Select for durable inbox or outbound messaging; the current catalog places delivery registrations here |
| Files/Media | Ownership, admitted uploads/downloads, completion, deletion, metadata and processing status | High relevance to the existing camera/editor; object storage and processors stay behind ports |
| Preferences/onboarding | Cross-device user choices and product setup progress | Start with the existing owner; extract only when the rules justify a module |
| Billing and Entitlements | Verified purchase/subscription state separately from product access policy | Select for paid features; keep store/provider protocols in adapters |
| Privacy | Export/erasure request lifecycle and coordination with each data owner | Owners perform their own work and expose progress; Audit is not an unrestricted copy of erased data |
| Authorization | Actions, permissions, roles and grants | Add when owner-only Account access is insufficient; do not duplicate membership truth |

The Installations row is a proposed boundary, not an existing module. Avoid two
owners for push registrations: keep them in Notifications initially, and introduce
an installation contract if installation association/retirement becomes an
independent capability. Installation association must be authenticated and changed
on sign-out/account switching so a later user does not inherit earlier delivery.
An installation identifier is not authentication, and a delivery address is not
a durable person or device identity.

## Platform evidence and its architectural implication

Apple documents that an [APNs token](https://developer.apple.com/documentation/usernotifications/registering-your-app-with-apns)
addresses one app/device pair and can change. Firebase documents
[registration freshness and invalid-registration removal](https://firebase.google.com/docs/cloud-messaging/manage-tokens).
These support an explicit delivery-registration lifecycle; the proposed module
split is our design inference, not a platform-mandated architecture.

Google Play's [purchase integration guidance](https://developer.android.com/google/play/billing/integrate)
requires purchase verification before benefits are granted and distinguishes
pending purchases from completed purchases. Separating purchase state from
entitlement decisions makes that distinction visible behind provider-neutral
application ports. Apple/Google provider adapters still need their own concrete
contracts and acceptance; a paywall component does not implement billing.

## Synchronization and native responsibilities

Sync combines domain rules and reusable mechanics. Notes must define deletion
tombstones, change positions, version/conflict handling and receipt replay;
native owners need durable pending input, account partitions and restart-safe
reconciliation. Another domain may need a different merge policy. A generic
queue/transport does not determine those rules. Push/socket signals can trigger
authoritative reads; neither constitutes the sync history or commit evidence.

OS permission prompts, deep-link parsing, app foreground/background scheduling
and local caches belong primarily in native adapters/application owners. Add a
backend capability only for actual server-owned rules such as a notification's
target, a verified invitation or a shared media object's access policy.

Conceptual example:

```text
Media upload completed → Files state + outbox
  → Notifications receiver → inbox item + delivery work
  → selected push adapter → installation delivery address
  → native notification action → authenticated Files read
```

This is a proposed flow. A notification or deep link does not grant file access.
Producer/consumer commits and external delivery require their own idempotency
and recovery semantics, as described by the existing module boundary policy.

## Suggested next learning slices

First close the recorded [Bento convention gaps](ports-and-module-composition.md),
then verify a generated native/Go connected profile. For new domains, Files/Media
is a useful first vertical because the camera/editor supplies a real consumer.
Notifications with a small registration lifecycle can follow; add a standalone
Installations module only when its association/retirement rules warrant it.
Durable Notes pending work/change tracking can be a separate mobile-foundation
slice before claiming offline synchronization. Select billing and richer roles
when a product needs them.

Verification: source/document inspection and platform reference reading only.
Root `make notes-check` validates local links and notebook structure, not push,
uploads, purchases, sync, privacy compliance or runtime provider behavior.
Next question: should the first connected exemplar exercise media ownership and
uploads, or reliable offline Notes edits across process termination?
