# Portable containers and quiesced backups

Claim: portable deployment needs a tested restore of both metadata and private
objects, plus explicit trust and resource ownership at the edge.

Origin: 2026-10-17 operations slice. Evidence: local Docker acceptance using Go
1.27.1-bookworm, PostgreSQL 18.6-alpine, Caddy 2.10.2-alpine and Mailpit 1.31.5.
Read [container](../../Dockerfile), [Compose](../../infra/production/compose.yaml),
[backup driver](../../scripts/backup.py) and
[acceptance driver](../../scripts/operations_acceptance.py).

## What and why

The builder produces static commands; the scratch image includes system CAs and
runs as 65532:65532 with a read-only root filesystem and bounded temporary space.
Caddy is the public edge. API/database/metrics remain private. Only the configured
edge address may supply the single forwarded client address used by rate admission.
Its fixed stream timeout exceeds the API socket lifetime. One API replica matches
the local broadcast hub. Source portability is established by container build and
local execution, not by an external DNS/certificate deployment.

Backup stops the selected API, creates a PostgreSQL custom dump and tar of private
objects, writes checksums/manifest, then restarts only a previously running API.
It creates the destination exclusively, flushes files/directories and retains a
partial failed destination for inspection. This bounds consistency to writers
owned by that API; independent database/object writers need a different policy.
Restore refuses an existing namespace/resource, validates the trusted bundle,
restores both stores, checks migrations and leaves the API stopped for review.

## Observed tool findings

Conceptual example: restoring Notes rows without private objects can pass a
database check while every private download fails. Acceptance therefore reads
both a retained receipt and exact restored object bytes, then consumes a retained
password-reset challenge and observes session revocation.

The first container check caught an overlapping test network. The driver now
inspects existing Docker IPAM and chooses two unused private subnets rather than
modifying existing networks. Compose's tmpfs option contains a comma and must be
quoted in YAML. Alpine's BusyBox tar rejects GNU --same-owner; extraction runs as
root with the archive's validated 65532 ownership and checks subsequent API reads.
Compose file-secret uid/mode remapping is not portable: keep its parent private
and make the explicit read-only SMTP file readable by the container UID.

## Evidence and limits

Local acceptance covers SMTP capture, read-only/non-root container settings,
metrics, API quiescence/resume, unused-project restore, object bytes, retained
receipts/challenge and secret-free owned logs. Existing resources are never reset.
The source suite and disposable PostgreSQL conformance are separate checks.
See [dated execution](../../../../docs/blueprints/backend-connected-foundations.md).

Checksums detect corruption, not an attacker replacing a bundle. Restore only
trusted owned backups; keep them private and encrypt off-host copies. This driver
targets one common database and private-files volume, not split/cloud stores,
point-in-time recovery, failover, an erasure workflow or a general scheduler.
Maintenance prunes only expired challenges, preserving receipts/revocation/Audit.

Primary references: [Caddy reverse proxy](https://caddyserver.com/docs/caddyfile/directives/reverse_proxy),
[Mailpit Docker](https://mailpit.axllent.org/docs/install/docker/).
Next: choose real hosting, external SMTP and backup retention/encryption policy,
then verify those adapters and restore objectives in that environment.
