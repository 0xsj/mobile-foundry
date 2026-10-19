# Portable ingress and private runtime

Claim: a portable profile is reviewable only when image constraints, ingress
trust and actual recovery execution are verified together.

Origin/evidence: 2026-10-18 Docker 29.8.2, Rust 1.86.0/bookworm image build,
PostgreSQL 18.6-alpine, Caddy 2.10.2-alpine and Mailpit v1.31.5 local acceptance.
Read [operational behavior](../../../../../../contracts/behavior/rust-operations.md).

Source reading order:

1. [Dockerfile](../../../../Dockerfile) and [restricted context](../../../../.dockerignore):
   multi-stage build, bounded compiler jobs and non-root runtime with system roots.
   Cargo release artifacts are copied out of its cache mount into the final stage.
   Notes, local data, operations secrets and unrelated assets stay out of the context.
2. [Compose](../../../../infra/production/compose.yaml) and
   [private env example](../../../../infra/production/.env.example): one common DB,
   private Files directory and separate API/metrics ports, with only edge published.
   Migrate/maintenance are explicit tools, not hidden startup side effects.
3. [Caddyfile](../../../../infra/production/Caddyfile): overwrite X-Forwarded-For,
   proxy native upgrades and bound stream lifetime. Caddy documents
   [upgrade/stream handling](https://caddyserver.com/docs/caddyfile/directives/reverse_proxy#streaming).
4. [Runtime](../../../../pkg/config/src/lib.rs),
   [proxy admission](../../../../pkg/httpx/src/proxy.rs) and
   [server supervision](../../../../internal/server/src/lib.rs): trust is selected
   from the actual peer; monitor failure drains API and all owned listeners join.
5. [Operations acceptance](../../../../scripts/operations_acceptance.py) and
   [backup](../../../../scripts/backup.py): isolated namespaces and real DB/object
   restoration, with the API stopped for the pair snapshot.

Example (Conceptual): a direct client supplies a fake forwarding chain. The API
ignores it unless the socket is the configured edge. The actual edge overwrites
that chain before a native upgrade; ready and changed pass through Caddy, while
Notes itself remains authoritative over HTTP.

Observed correction: starting the actual edge initially failed because an earlier
automatic container took its fixed .2 address. The Rust profile now limits automatic
allocation to the upper /25 while the edge remains below it. Configuration-only
Caddy validation did not expose that collision. Read
[Compose allocation](../../../substrate/compose-address-allocation.md).

Verification: release image, non-root/read-only health, actual local edge/native
socket, SMTP verification/reset, private metrics and fresh-project recovery pass.
The test edge uses loopback HTTP and separately validates the public-domain config;
public DNS/certificates and hosted delivery are not claimed. Keep one API replica
for the local hub. Native/CLI Rust selection remains unregistered.
Related: [paired backups](../../../patterns/quiesced-database-and-object-backups.md).
Next: what independently generated source closure must include these operations?
