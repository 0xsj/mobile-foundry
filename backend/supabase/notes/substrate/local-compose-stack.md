# Compose snapshot and local ownership

A pinned self-hosted Supabase snapshot provides local provider infrastructure,
while named volumes and generated local credentials keep runtime state out of
versioned configuration.

## Origin and evidence

Configured 2026-10-08 from Supabase `self-hosted/v0.8.2`, commit
`564eab8ad7840b13324f68b1bfac074ef8d51c21`. The
[official Docker guide](https://supabase.com/docs/guides/self-hosting/docker)
and [release snapshot](https://github.com/supabase/supabase/tree/self-hosted/v0.8.2/docker)
are the primary references. Compose 5.5.1 accepted the adapted configuration
with `docker compose config --quiet` on Docker Desktop's Linux ARM64 engine.

The snapshot pins PostgreSQL 17.6.1.136, Auth 2.196.0, PostgREST 14.17,
Realtime 2.134.10, Storage 1.74.0, Meta 0.99.0, Edge Runtime 1.76.2,
Supavisor 2.9.12, Envoy 1.39.1, and Studio's 2026.09.07 build. Local mail
capture uses Mailpit 1.31.1. Image versions and their support files must be
reviewed together when updating.

## What and why

This runs self-hosted Supabase services rather than the CLI's project format.
Envoy exposes the provider APIs and authenticated Studio entry point.
PostgreSQL stores application and provider schemas; Auth, REST, Realtime,
Storage, functions, and the pooler connect over Compose's internal network.
Mailpit receives local verification and recovery mail.

Internal database clients still use port 5432. Host ports are a separate
choice: 56321 for the API, 56322 for session pooling, 56329 for transaction
pooling, and 56324 for mail UI. Loopback bindings and the Compose project name
let this profile coexist with other local stacks.

The bootstrap requires OpenSSL and Node.js. It stages the upstream generators
in a temporary directory and publishes `.env` only after both succeed. An
existing `.env` is retained. Generator output is suppressed because it contains
credentials; setup prints only whether the file was created or retained.

Database data, storage objects, snippets, database configuration, and Deno
cache live in named Docker volumes. Versioned gateway and SQL initialization
files remain configuration inputs.

## Example and checks

From the Supabase profile directory:

```sh
make check
make up
make down
```

`make check` generates credentials once and validates Compose interpolation and
configuration. `make up` waits for configured service health checks. `make down`
removes containers and the project network while retaining data volumes.

The stack started successfully and all 12 containers reached healthy status.
HTTP smoke checks passed for Auth health, REST routing with the publishable key,
admin OpenAPI discovery, Storage bucket discovery, password-protected Studio,
mail UI, and the sample Edge Function using a verified asymmetric JWT. No
application schema was created. The profile was stopped after verification.

A separate bootstrap check generated a private `.env` with mode 0600, parsed
the generated public key set, and confirmed repeat setup retains credentials
without modifying the Compose source.

## Gotchas and limits

Initialization SQL runs against a fresh database volume; editing it later is
not a migration. Changing credentials after initialization requires a rotation
procedure that updates the affected services and database roles together.

Native clients use public client keys. Service and secret keys belong to
trusted server configuration. A reachable stack does not supply account
ownership policies, application schemas, or an offline synchronization
contract. Device routing and native development network settings must be
verified when the first adapter is added.

The upstream gateway intentionally restricts `/rest/v1/` OpenAPI discovery to
admin keys; a publishable key receives 403 there. That is separate from access
to application REST routes, which still requires appropriate database grants
and row-level policies. The smoke check reached PostgREST's expected missing-
table response through a publishable key; it did not test application data
authorization. Mail delivery, Realtime subscriptions, native SDKs, and database
restart recovery were not exercised.

## Used in and related

Use this profile for a workflow that benefits from PostgreSQL and Supabase's
provider APIs. The [reading order](../README.md) links setup and provenance;
record application policies and adapter behavior with their implementation.
