# Connected Daily starter

CLI 0.20.0 also exposes [Rust-connected Daily](rust-connected-starter.md) through
the same native ports/composition and a separate backend selection/acceptance.
The Go-connected capability continues to require Go; co-location remains separate.

CLI 0.18.0 selects [native owner workspace management](native-workspaces.md):
create without switching, strong-version rename and explicit current-state
read/review after uncertainty. Account writes have no receipt/replay contract.

CLI 0.15.0 additionally selects [native identity lifecycle and live reads](native-identity-live.md)
through separate service ports and product-owned transient forms/reconnect policy.
This extends the selected scope without changing credentials or receipt formats.
CLI 0.16.0 additionally selects [read-only cached first pages](notes-read-cache.md)
behind a separate storage port, with journal checkpoints and current-session gating.
CLI 0.17.0 adds [native registration](native-registration.md), with explicit sign-in
and personal-account bootstrap through the existing ports.

Daily admits `--with go-connected --backend go` for Swift, Kotlin or both. This
selects real owned Identity sign-in/renewal/revocation, Account discovery/selection
and remote Notes CRUD/receipts. `--backend go` alone remains co-location; local-data
and other shells do not implicitly acquire this capability. Their unregistered
combinations are refused before output creation.

## Configuration and boundaries

The CLI admits `--api-base-url` only for an explicit connected capability. Its default is
`http://127.0.0.1:8080/v1`. Inputs are bounded ASCII HTTPS URLs or loopback HTTP;
credentials, query, fragment, invalid ports, escaped/empty/dot path segments and
whitespace are refused. Canonical host/default-port/trailing-slash normalization
matches the native deployment authority. Metadata and app-owned configuration
record this public endpoint. Credentials are never product configuration.

Runtime Release requires HTTPS. Debug may use loopback HTTP. Android admits only
reviewed loopback domains through Debug resources, keeps main cleartext disabled
and declares Internet permission. Apple uses local networking ATS policy without
an arbitrary-load exemption. Physical devices need a reachable HTTPS deployment;
Android emulator/USB loopback needs an explicitly owned ADB reverse mapping.

Composition selects GoSessionAuthority and device CredentialVault implementations,
then binds HTTPAccountDirectory/HTTPRemoteNotesRepository to the exact current
SessionLease. Product screens depend on owned state and service ports. There are
no imports, routes or resource requirements from the catalog application. Selected
HTTP/Session/Services and selected Data source, tests and fixtures are local copies with complete
native build closure. Existing package/module names are independent of branding.

## Lifecycle and presentation

Account includes Solid/Glass selection, sign-in, explicit saved-session restore,
renewal, sign-out, bounded owned-account pages and guarded workspace selection.
First sign-in bootstraps Personal. Restoring validates the stored account rather
than falling back to Personal. The existing session/rotation checkpoint contract
governs secure persistence and refusal. Password drafts are transient and cleared
on submit/session transitions; they are never saved as navigation state.

Library shows only the current account's remote Notes, with explicit refresh,
bounded paging, create/update forms and delete confirmation. One pending command
blocks new writes. Unconfirmed acknowledgements retain original input/ID/scope and
offer receipt lookup or an explicit retry of that same command. Confirmed receipts
are kept through a failed follow-up read. A read or foreground transition never
automatically replays a write. The [pending Notes journal](pending-notes.md) persists these commands and terminal
results before/after HTTP, with explicit recovery after process restart. Unsaved
drafts remain transient.

Session transitions immediately detach old feature owners and cancel their reads.
Every new lease rebinds repositories, including same-account renewal. Late replies
must not populate a replacement account. Scene/Activity inactivity detaches feature
work; foreground revalidates expiry and refetches without automatic renewal.
Android's Activity-scoped ViewModel retains the controller across recreation;
fresh process restoration uses the native vault. Today/Activity stay placeholders.
This first connected shell selects one iOS scene and a single-task Android entry
so one controller writes the deployment's credential record. Multiwindow support
requires a deliberate application-wide session owner rather than extra independent
controllers restoring the same rotating credential.

## Acceptance and limits

Copied products have their own generated build/run documentation and
`make connected-test`. The driver owns an unused loopback memory API, temporary
users and any ADB mapping; it refuses existing processes/mappings and clears
inherited database/provider selection. It exercises real URLSession/OkHttp calls
and actual generated native screens. Selected UI cases must execute without skips.
iOS termination/relaunch and Android's ten instrumentation runs separated by
force-stop establish secure restore separately from Activity recreation.

Readiness is promoted only after independent generated acceptance, as recorded
in [starter readiness](../../docs/STARTER-READINESS.md). Ordinary app tests may skip
the opt-in external-API scenario. Multi-item native queues/automatic sync, conflict merging,
remote Files/inbox UI, system push, alternate providers, production auth lifecycle,
release deployment/signing and physical-device performance are separate capabilities.
Server restart durability still requires explicit PostgreSQL selection/migrations.
