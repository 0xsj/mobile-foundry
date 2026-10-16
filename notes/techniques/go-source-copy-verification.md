# Verifying Go source copies

Claim: compiling a renamed backend outside its checkout exposes identity,
fixture, migration and runner assumptions that catalog tests cannot establish.

Origin: Go CLI extraction, 2026-10-13. Evidence: generated github.com, a.example
and example.com modules; Go checks/race/smoke, real auth/account/Notes recovery,
disposable PostgreSQL crash recovery and co-located native builds pass.

## What and why

Keep source depth where tests/embedded resources depend on it. Copy the required
fixture and numbered migrations alongside code. Change both imports and the
boundary check's own-module prefix; otherwise a copied policy can silently treat
its new modules as external packages. Validate the generated runner's environment,
not just a binary manually configured by the acceptance harness.

Module renaming can change import ordering. A prefix beginning with example.com
sorts before fmt; the original github.com import sorted after it. A second,
short prefix exposed this defect. The generator now sorts ordinary import groups
and refuses unsupported syntax without requiring Go installed during generation.

## Example — Conceptual

```text
generate outside checkout with a.example/project/backend
go-check → detects import-order/policy drift
real runner → sign in → scoped record → lost acknowledgement → receipt lookup
isolated PostgreSQL → migrations → committed write → kill API → recover
```

## Gotchas and limits

Successful copy/unit checks establish safety and selection, not independent
runtime behavior. Compose validation establishes configuration, not a running
database. Co-located native builds establish retained source/build compatibility,
not backend-connected screens. Existing toolchain/dependency caches do not prove
fully cold installation. Declare these evidence levels separately.

The acceptance driver uses only the generated API and Make runner for behavior;
it retains no checkout imports in the output. Scoped fixtures and an owned
process group avoid contacting or stopping development services. PostgreSQL
14.24 local mode was observed because Docker's VM is read-only; 18.6 remains the
pinned Compose configuration, with no new runtime claim in this batch.

## Used in and related

[Go starter evidence](../../docs/blueprints/go-starter.md),
[CLI walkthrough](../modules/cli/README.md), and
[standalone starter reasoning](standalone-starter-verification.md).
Next: repeat independent acceptance with an explicit connected native profile,
including actual device credential namespaces and account-scoped remote data.
