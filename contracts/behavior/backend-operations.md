# Portable backend operations

The Go profile supplies an explicit Docker deployment baseline, not a provisioned
host. Runtime remains configuration-driven and defaults to local development.

## Runtime and edge

The multi-stage Dockerfile builds static server, migrate, maintenance and healthcheck
binaries. The final scratch image uses UID/GID 65532, a private files directory and
system TLS roots. Compose selects a read-only filesystem, dropped capabilities,
no-new-privileges, a bounded temporary filesystem and private PostgreSQL/files volumes.
The healthcheck binary uses a fixed loopback readiness endpoint. API startup checks
schema histories; migration is a separate explicit command.

Caddy terminates HTTPS and proxies native WebSockets. Only edge ports are published
in the production profile. The API, metrics and database remain private. The edge
overwrites X-Forwarded-For; the API admits only a single address from explicitly
configured TRUSTED_PROXY_CIDRS peers. Default private subnet/edge IP can be overridden
together. Do not trust arbitrary client forwarding headers. A real domain/certificate
and SMTP configuration must be supplied for an actual deployment.

METRICS_ADDR enables a separate private listener, off by default. Prometheus text
contains bounded method/status counters, active handlers and duration sum/count;
no URL, account, principal, credential or request body becomes a label. Structured
logs retain public error codes and request metadata. Metrics bind failure refuses
startup; later listener failure stops the process. PostgreSQL pool maximum is
configurable from 1..128 per module, defaults to eight, and idle retention is thirty
minutes. Connection/statement/lock budgets remain bounded.

## Backup and recovery

The owned backup command temporarily stops its selected API, dumps the configured
portable PostgreSQL service, archives private objects and resumes the API only if
it was running. Quiescing covers API writers/workers so the DB/object pair cannot
race deletes/uploads. A backup has a versioned manifest and SHA-256 checksums;
files/directories are private and the manifest publishes last. No existing backup
destination is replaced. An incomplete failed destination is retained for inspection.

Restore accepts the owned format, verifies checksums and archive paths/types/owners,
and requires an unused Compose project and unused reserved resource names. It never
resets a retained database/volume. It restores the common PostgreSQL database and
files, checks matching migrations, then leaves the API stopped for review. A clone
needs a separate private env/network. Restoration can retain active credentials;
keep a review clone isolated. Checksums detect damage, not authenticity: restore
only trusted owned backups and protect/encrypt off-host copies.

This portable profile uses one common database service and a private filesystem.
Split module databases, cloud object adapters, replicated delivery, point-in-time
recovery and hosted failover require their own configuration/acceptance. Audit,
revocation history and command receipts are retained. Maintenance only prunes expired
identity challenges; broad retention/export/erasure is an explicit product policy.

## Acceptance

The copied operations driver builds its own image and owns disposable projects,
auto-selects unused networks and uses loopback-only test ports plus local Mailpit.
It tests non-root/read-only startup, actual SMTP verification/reset, private metrics,
quiesced DB+objects backup, restore into a new project, existing-target refusal,
retained Notes receipts/content/contact/challenge and post-reset credential refusal.
It validates Caddy configuration without requesting public certificates. Local
SMTP acceptance is distinct from real mailbox delivery. Production rollout,
DNS/certificate issuance, external SMTP/APNs/FCM delivery and product retention
policy are not inferred from this run.
