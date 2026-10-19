# Connected products require a composition owner

Claim: copying a native app and an API together establishes a connected starter
only when the generated app selects the real ports and independently exercises
their lifecycle against that copied API.

Origin: the native catalogs already proved Go session and Notes adapters, while
CLI output deliberately selected only local shell behavior. This slice closes
that composition gap without turning every backend selection into remote UI.
Evidence comes from source inventories, CLI refusal/copy checks and the independent
acceptance recorded in [construction](../../docs/blueprints/go-connected-starter.md).

Rust follow-up, 2026-10-19: an explicit backend selection can reuse those native
ports and the same product composition when it implements the same admitted
wire contract. Keep backend identity in generation/acceptance instead of forking
native lifecycle policy. Read [Rust execution evidence](../../docs/blueprints/rust-connected-starter.md);
native source equality is a useful guard, but only executing the selected API
establishes compatibility for the tested workflows.

## What and why

A service port makes provider substitution possible. It does not select an
implementation, choose a deployment, bind credentials or decide when work stops.
The product composition owns those decisions. The adapter owns HTTP admission;
the session owns the current credential lease; the screen owner owns drafts,
receipts and presentation. None of these responsibilities belongs in TabBar.

A selected account ID is insufficient as a lifetime key: renewing the same account
replaces its lease. Detach immediately before session transitions, then bind the
new exact lease. On a late completion, cancellation/current-scope guards protect
the replacement owner. Readback/retry still belongs to the original command scope.

## Example

Conceptual sequence: sign in → resolve Personal → bind lease A → create command C →
lose acknowledgement → renew → invalidate A → bind lease B for the same account →
look up C's receipt. Do not construct a new command ID or switch its account while
recovering. A successful refresh is a read; it is not permission to resend C.

## Gotchas

- Activity recreation can retain a ViewModel. Process termination cannot retain
  that object. Test native vault restoration through a separate killed process.
- An app-only emulator call can work while its generated dependency/fixture closure
  is incomplete. Run the copied build/test targets outside the source checkout.
- A server copied beside an app still requires an explicit reachable base URL.
  A phone's loopback addresses that phone, not its development computer.
- Historical adapter/test names can outlive their first provider. Assert the
  actual built package and selected process; a method named Go is not evidence
  that a Go backend ran, and it does not prove the complete Rust wire surface.
- Durable server receipts do not retain a native command that existed only in
  memory. The [pending follow-up](persist-intent-before-external-effect.md) supplies
  a scoped disk journal; scheduling and multi-item ordering remain separate.

## Used in and related

Read [Swift composition](../../templates/overlays/go-connected/swift/ConnectedSessionModel.swift),
[Kotlin composition](../../templates/overlays/go-connected/kotlin/ConnectedSessionModel.kt),
[the registered selection](../../templates/profiles/native-daily-go-connected.json)
and [Rust selection](../../templates/profiles/native-daily-rust-connected.json),
then [the driver](../../scripts/connected_acceptance.py).
Continue with [deployment authorities/checkpoints](deployment-authorities-and-rotation-checkpoints.md),
[product extraction ownership](product-entry-and-extraction-closure.md) and
[readiness](../../docs/STARTER-READINESS.md).
