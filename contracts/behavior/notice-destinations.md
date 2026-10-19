# Account inbox file destinations

CLI 0.26.0 adds a read-only file destination to the explicit Daily Files + Inbox
combination on Swift/Kotlin with either connected backend. Files-only and
Inbox-only inventories do not acquire the destination owner or screen.

The app admits an Open file action only for a notice in the currently bound
inbox snapshot while its exact session lease is current. The destination owner
also checks the notice account/principal against its bound scope. A notice is
a target hint, never a current file snapshot or access grant. Opening neither
marks read nor changes account. Mark read remains a separate explicit action.

RemoteFileDetailReader exposes current metadata by canonical file ID and verified
downloads, without upload/delete/receipt operations. HTTP uses the existing
GET /accounts/A/files/F endpoint. Success must match the requested ID, account,
nondeleted status and strong version ETag. Downloads retain size/digest/type/ETag
verification. Expected not-found, denied and dependency failures stay distinct.
Missing/deleted targets show an unavailable detail; no cached receipt, list scan
or notification payload substitutes for a successful current metadata read.

Opening reads metadata only. Download preview and Refresh file are explicit
reads. A pending file has no download action. The screen shows admitted metadata
and optional bounded image preview; bytes are not downloaded automatically.
Every new open, close or bind cancels old work and invalidates its generation.
All exact-lease rebinds, including renewal, close and clear the destination;
noncooperative old completions cannot republish metadata or bytes. Binding and
process restoration never reopen a route. Closing does not mutate the inbox,
Files recovery journal or backend.

This is app-owned inbox navigation, not a URL/deep-link router or OS notification
integration. It does not add implicit account switching, mark-on-open, persistent
navigation, background transfers or remote push delivery. Read
[combined composition](connected-media.md) and
[construction/evidence](../../docs/blueprints/connected-media.md).
