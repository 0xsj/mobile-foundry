# Shared operation boundaries

`proxy.json` supplies 17 cases consumed directly by Go's parity tests and Rust's
httpx proxy tests. Both compare against expected effective addresses/refusals:
trusted and untrusted sockets, missing/duplicate/list/port/hostname/zone values,
IPv6, host-bit CIDRs and mapped IPv4 behavior. Tests do not expose an IP echo route.

Run `make backend-operations-parity-test` for these cases plus actual isolated
Go/Rust APIs. Source-gate behavior proves forwarding selection affects Identity;
private metrics check fixed method/status labels, missing API /metrics, scrubbed
inputs, startup bind refusal and listener closure.

Rust's `make operations-test` is a separate Docker/SMTP/backup/Caddy acceptance.
See [scope and limits](../../behavior/rust-operations.md). Neither suite claims
full HTTP parser equivalence, public TLS issuance or generated Rust readiness.
