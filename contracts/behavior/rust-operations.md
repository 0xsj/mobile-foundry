# Rust portable operations

Established 2026-10-18. Rust now supplies an explicit portable Docker profile
mirroring [Go's operational contract](backend-operations.md). It does not provision
a host or register Rust as a CLI/native starter.

`METRICS_ADDR` is optional/off by default, accepting a literal socket address
with nonzero port. Bind failure refuses startup before module workers are started.
A selected private listener serves GET /metrics, without mounting /metrics on
the public API. Other methods/paths are 404. Only fixed method categories and
status classes label request counters; active handler count and duration sum/count
contain no path, query, principal, account, credential or body. Scraping does not
count itself. A monitor failure triggers API drain; shutdown joins both listener
owners before module resources/pools close. Canceling the server owner aborts its
owned task sets rather than detaching a monitor.

`TRUSTED_PROXY_CIDRS` defaults empty. The accepted socket supplies the initial
PeerAddress. Only an explicitly trusted socket peer can replace it, using exactly
one X-Forwarded-For value that parses as an IP, without port, zone, list or hostname.
Missing/malformed/repeated values from trusted peers are owned 400 problems;
untrusted peers' forwarding headers are ignored. Identity gates use the admitted
effective address. IPv4-mapped peers/CIDRs are normalized for the shared Go cases.
The configured edge must overwrite client-supplied forwarding headers.

The Rust multi-stage image builds server/migrate/healthcheck/maintenance under
Rust 1.86.0. Its Debian bookworm-slim runtime supplies system TLS roots and runs as
65532:65532. Compose selects read-only root, dropped capabilities, no-new-privileges,
bounded tmpfs, private database/files volumes and one API replica. Only Caddy's
edge ports are published. It proxies native WebSockets and supplies HTTPS when
an actual public domain/certificate is configured. Push/providers/jobs remain off.
Default private subnet is 10.203.241.0/24; dynamic IPs use 10.203.241.128/25, outside
the edge's fixed .2. Change PRIVATE_SUBNET, PRIVATE_IP_RANGE, EDGE_IP and matching
TRUSTED_PROXY_CIDRS together, keeping the edge outside automatic allocation.

The fixed loopback healthcheck has a two-second deadline and bounded status-line
read; it accepts readiness 200 only and prints no raw response. API startup only
checks histories. Migrations remain an explicit tool. Maintenance requires owned
PostgreSQL Identity, checks its history, admits --limit 1..1000 (default 128),
and prunes only expired challenges under a fifteen-second scope. It starts no
mailer, KDF worker or schedule; failures print only public classifications.

The standalone backup script retains Go's mobile-backup.v1 format. It quiesces
the selected API, captures the common database plus private objects, validates
archive members/ownership, publishes checksums/manifest last and resumes only a
previously running API. Existing destinations are refused. Restore validates
format/checksums/archive before changes, refuses existing project/resource names,
restores only an unused namespace, checks matching histories and leaves API stopped.
Protect backups as credential-bearing private data. Checksums detect damage, not
malicious replacement; only trusted owned archives are admitted.

Verification: backend-operations-parity-test runs 17 shared proxy cases and real
Go/Rust source gates, private metrics, startup bind refusal, privacy and joined
shutdown. Rust tests additionally cover bounded labels, active/canceled observation,
monitor failure and canceled-owner listener cleanup. The operations-test driver
builds/uses an owned image and disposable Docker projects, exercises non-root/
read-only startup, actual Caddy forwarding/WebSockets, local SMTP reset/verification,
metrics, quiesced backup, restored receipts/bytes/contact/challenge, damaged backup
and existing-target refusal, maintenance and private logs. It validates the public
Caddyfile separately without requesting certificates.

Limits: the acceptance edge uses loopback HTTP; live DNS/public TLS issuance and
external SMTP/APNs/FCM delivery are not observed. Rust metrics time HTTP handler/
upgrade admission, while Go's hijacked handler stays active for the socket lifetime.
Do not compare socket duration/active values across profiles. Metrics connections
have two-second header and three-second total lifetimes with an 8 KiB Hyper read
buffer, rather than Go's independent write/idle policy. Main API routing/network
differences remain in the parity inventory. Split-database backups, cloud objects,
PITR/failover, multi-replica fanout, retention/erasure and generated Rust/native
products require their own selected adapters and acceptance.
