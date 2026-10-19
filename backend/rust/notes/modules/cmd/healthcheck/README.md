# Fixed bounded readiness probe

Claim: a container probe needs a small bounded observation, without accepting
an arbitrary target or leaking response bodies into diagnostics.

Origin/evidence: 2026-10-18 [the Rust probe](../../../../cmd/healthcheck/src/main.rs)
and actual Docker health startup under read-only/non-root constraints.

It connects only to 127.0.0.1:8080, writes a fixed readiness GET, and reads at most
128 status-line bytes under a two-second total timeout. HTTP/1.1 200 OK is success;
other status, early EOF, malformed line, connect failure and timeout fail silently.
The owned socket drops on return. No shell, URL environment variable or credential
configuration is required.

Example (Conceptual): PostgreSQL history is missing, so the API never starts and
the fixed probe fails. After explicit migration, selected readiness checks pass
and Compose marks the container healthy. Health does not establish hosted uptime
or native connected-product behavior.

Verification: the built binary is the actual Compose healthcheck used by the
[operations driver](../../../../scripts/operations_acceptance.py). Read
[portable runtime](../../infra/production/README.md).
Next: which dependency should remain readiness-only rather than affect liveness?
