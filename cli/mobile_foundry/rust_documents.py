"""Self-contained Rust project guides and root command projection."""
def append_documents(destination, inputs, backend, native, connected=False):
    ignore=destination/".gitignore"
    ignore.write_text(ignore.read_text()+"\n# Rust output, private state and provider credentials\nbackend/rust/target/\nbackend/rust/.data/\n.cargo/\n.push-private/\n*.p8\npush.local.json\n*service-account*.json\nserviceAccountKey.json\n*.private.json\n*.private.toml\n")
    children=["dev","build","check","test","smoke","db-up","db-down","db-check","migrate","migrate-check","integration-test","operations-test","prod-config","prod-build","prod-db","prod-migrate","prod-up","prod-down","prod-maintenance","prod-backup","prod-restore"]
    make=destination/"Makefile"
    make.write_text(make.read_text()+"\n.PHONY: rust-help "+" ".join("rust-"+c for c in children)+"\nhelp: rust-help\nrust-help:\n"+"".join(f"\t@echo 'make rust-{c}'\n" for c in children)+"\n"+"".join(f"rust-{c}:\n\t$(MAKE) -C backend/rust {c}\n\n" for c in children))
    scope=("Native sign-in, accounts and remote Notes select this Rust API through the shared HTTP contract." if connected else "Native shells and this API are independently composed; native auth/data connection is not selected.") if native else "This selection needs no native toolchain."
    guide=f"""
## Rust API

API package: `{backend.package}`. The explicitly named `api-server` binary and
reusable crate names stay stable. Native commands require macOS/Linux and
Rust/Cargo {backend.profile['rustVersion']}+,
rustfmt and Clippy are required for checks. Python 3.11+ runs the portable drivers;
OpenSSL is required by local TLS tests. No generator or original checkout is needed.
{scope}

From this generated project root:

```sh
make rust-check rust-test rust-smoke
make rust-dev
```

The dev runner explicitly defaults AUTH_MODE=identity; the bare binary defaults
to deny. Memory needs no external service and loses data on exit. Register a
development user, then sign in:

```sh
curl -X POST http://127.0.0.1:8080/v1/identity/register \\
  -H 'Content-Type: application/json' \\
  -d '{{"login":"developer","display_name":"Developer","password":"replace with a long local passphrase"}}'
curl -X POST http://127.0.0.1:8080/v1/identity/sign-in \\
  -H 'Content-Type: application/json' \\
  -d '{{"login":"developer","password":"replace with a long local passphrase"}}'
```

Use the returned access token as a bearer. Bootstrap a personal account at
POST /v1/accounts/bootstrap, then use /v1/accounts/{{id}}/notes. Notes is a
replaceable example: preserve command UUIDs, exact input and original scope;
update/delete require the observed strong If-Match. Read receipts before deciding
how to recover uncertain acknowledgements. Never infer rollback from a timeout.

Health, Identity/core recovery, Account, Audit, Notes, Files, Notifications,
installations and Realtime are included behind their native Rust ports/adapters.
HTTP query seams and committed outbox/inbox reactions remain separate. Native
bearer /v1/accounts/{{id}}/realtime supplies lossy refetch hints, not write authority
or durable replay. Refetch on ready/reconnect/foreground; renewal closes the old
connection. One API process is supported by the selected local hub.

Optional cloud gateways/jobs stay off. PUSH_CONFIG_FILE plus owned Identity selects
private APNs/FCM routes. PUSH_JOBS_ENABLED=true additionally requires PostgreSQL;
only a confirmed sending marker permits a provider call. Unknown sends remain
uncertain. Explicit replacement retains the source and requires duplicate-risk
acknowledgement where applicable. No keys/cloud account are provisioned.

Retained local PostgreSQL (Docker), from the generated root:

```sh
make rust-db-check
make rust-db-up rust-migrate rust-migrate-check
STORAGE=postgres make rust-dev
```

Each output owns its Compose project/volumes. POSTGRES_PORT defaults to 58433;
choose another free port for another project. Make's default DATABASE_URL follows
that port. Module URLs can override it explicitly. Private Files bytes live under
backend/rust/.data/files. Startup checks histories but never migrates. db-down
retains volumes; backups must keep database and private objects together.

The disposable integration suite never reads a supplied application DATABASE_URL:

```sh
make rust-integration-test
POSTGRES_TEST_MODE=local POSTGRES_BIN_DIR=/path/to/postgres/bin make rust-integration-test
```

It owns a fresh cluster/container, runs copied transaction/job fault/SIGKILL tests
and restarts APIs with retained receipts/bytes/pending delivery. Runtime tests
include ignored cases that only this owner may run. Do not run ignored database
tests against a retained application database.

Private METRICS_ADDR enables a separate monitor; it is not a public API route.
TRUSTED_PROXY_CIDRS admits one forwarded address only from configured socket peers.
HTTP metrics settle at upgrade admission, not at the separately owned socket's
close. Main HTTP parser/routing/network behavior is the captured Rust subset;
full Go wire equivalence is not claimed.

The portable Docker profile uses non-root/read-only API, private PostgreSQL/files/
metrics, and Caddy edge. Set an absolute private PROD_ENV_FILE outside this source,
based on backend/rust/infra/production/.env.example, then from backend/rust run:

```sh
make prod-config prod-build prod-db prod-migrate prod-up
make prod-maintenance
make prod-backup BACKUP_DIRECTORY=/absolute/private/backup
```

Pass that PROD_ENV_FILE to each command. Restore requires an unused RESTORE_PROJECT
and leaves its API stopped for review. Protect bundles as active credential-bearing
data; checksums do not authenticate an untrusted archive. Keep PRIVATE_SUBNET,
PRIVATE_IP_RANGE, EDGE_IP and TRUSTED_PROXY_CIDRS aligned, with the fixed edge
outside automatic allocation. Private SMTP is explicit; live hosting/domain/TLS,
external mail/push delivery, split-database/cloud backups, multi-replica fanout
and product retention/erasure need deliberate setup and independent acceptance.

```sh
make rust-operations-test
```

Operations acceptance builds an owned image and tests local Caddy/WebSockets,
Mailpit, private metrics and paired backup/fresh-project restore, with only owned
disposable resources. It does not publish a public deployment.
"""
    setup=destination/"SETUP.md";text=setup.read_text()
    if native:
        text=text.replace("Backend: none.","Backend: rust (connected)." if connected else "Backend: rust (independent).")
        text=text.replace("account/auth/session providers, backends and synchronization are not selected.","account/auth/session providers and synchronization are not selected in the native shell.")
    setup.write_text(text+guide)
    readme=destination/"README.md"
    text=readme.read_text().replace("Backend: none.","Backend: rust (connected)." if connected else "Backend: rust (independent API).")
    readme.write_text(text+f"\nRust API package `{backend.package}`. Run `make rust-dev`; checks are\n`make rust-check rust-test rust-smoke`. Read SETUP.md for persistence and operations.\n"+(scope+"\n" if native else ""))
    (destination/"backend/rust/README.md").write_text(f"# {inputs.display_name} API\n\nRust package `{backend.package}` with independent local crates, locked dependencies,\nfixtures, migrations and portable operations. Read [project setup](../../SETUP.md)\nfor the selected toolchain, commands and operational limits.\n")
