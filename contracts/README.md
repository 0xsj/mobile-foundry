# Shared application contracts

[Go starter extraction](behavior/go-starter.md) defines available API-only or
co-located native/Go source copies, module/Compose identity, portable commands and
independent backend acceptance. Native connected-profile composition remains separate.

[Remote Notes and receipts](behavior/remote-notes.md) defines account-scoped
versioned CRUD, retained command identity, atomic replay/receipt storage and
uncertain-write recovery. Memory/PostgreSQL and native HTTP adapters are
implemented; device outboxes, Notes facts/live hints and generated connected
profiles remain separate capabilities.

[Go module boundaries](behavior/module-boundaries.md) establishes business data
ownership, synchronous capabilities, committed integration events and recoverable
realtime hints. [Capabilities](capabilities/README.md), [events](events/README.md)
and [realtime](realtime/README.md) own their respective contracts; the Account/Audit
memory/PostgreSQL subset is implemented, while remote/live runtimes remain planned. See the
[backend catalog](../backend/go/CAPABILITIES.md) for selectable responsibilities.

[Account/Audit behavior](behavior/account-audit.md) implements the first Go
business workflow with [HTTP](http/account-audit-v1.md),
[account access](capabilities/account-access-v1.md),
[integration facts](events/account-v1.md) and [name fixtures](fixtures/account/names.json).
[PostgreSQL persistence](behavior/postgres-persistence.md) adds explicit schema
deployment, atomic durable stores, leases, replay and committed cursor behavior.
[Go Identity/sessions](behavior/identity-sessions.md) adds owned password/session
authentication, with [HTTP](http/identity-sessions-v1.md),
[credential resolution](capabilities/identity-credential-v1.md) and
[principal-scoped lifecycle facts](events/identity-v1.md).
[Account bootstrap/discovery](behavior/account-bootstrap.md) adds a stable personal
account, owner-only listing and device-owned selection through guarded lookup.
[Native Go integration](behavior/go-native-session.md) composes those contracts
behind authority/directory ports, with deployment-bound issuer IDs and rotation
checkpoints before account lookup.

[Go runtime](behavior/go-runtime.md) defines shared-package/module ownership and
process lifecycle; [runtime HTTP v1](http/runtime-v1.md) specifies the implemented
health endpoints and problem/metadata projection.

[Physics playground](behavior/physics-playground.md) defines bounded 2D motion,
contact/scoring rules, input admission and caller-owned scheduling. Both native
numeric suites consume [shot fixtures](fixtures/physics/shots.json).

- `http`: Public wire specifications for owned APIs.
- `behavior`: Platform-independent behavioral specifications for kernel,
  transport, session, sync, and graphics boundaries.
- `fixtures`: Portable inputs, expected outcomes, and failure scenarios that
  both Swift and Kotlin implementations can consume.
- `capabilities`: Versioned synchronous module request/response specifications.
- `events`: Versioned integration facts, independent of internal domain types.
- `realtime`: Authorized client live-update protocol and recovery specifications.

Keep application behavior separate from backend wire details. Managed SDKs
may use different wire protocols while satisfying an application's repository
or session interface.

[App shell](behavior/app-shell.md) defines five peer destinations,
catalog presentation/back navigation, material ownership and feature lifetime.
[Camera and photo preview](behavior/camera-photo.md) defines explicit camera
permission, native capture lifetime, bounded photo admission, sample fallback
and reuse of Image studio's editing controls.

Generated Swift and Kotlin wire models live inside their platform service
boundaries. The canonical specification and generator inputs live here.
[Kernel outcomes and failures](behavior/kernel.md) is the first behavioral
contract, with [canonical failure fixtures](fixtures/kernel/failures.tsv)
consumed by both native test suites. Owned HTTP specifications live under
`http`; the Go runtime implements Health and the Account/Audit memory/PostgreSQL workflow.

[HTTP and health behavior](behavior/http.md) owns request admission, native
cancellation/deadlines, problem decoding, metadata and health admission. Its
[response](fixtures/http/responses.json), [path](fixtures/http/paths.json), and
[retry timing](fixtures/http/retry-after.json) fixtures are shared by both platforms.

[Notes service and query state](behavior/notes-query.md) defines the first
provider-independent domain port, memory/HTTP implementations, and feature
refresh/cancellation behavior. Both service suites consume its [admission fixtures](fixtures/notes/responses.json).

[Query state and async presentation](behavior/query-ui.md) owns reusable snapshots,
pure transformations and native rendering. Both query suites consume its
[state matrix](fixtures/query/states.json). Feature orchestration stays outside
the query and UI libraries.

[Native UI tokens and theme](behavior/ui-tokens.md) defines the V1 semantic
roles, native scales, scoped appearance, and reduced motion. Both UI suites
consume the same [token fixtures](fixtures/ui/tokens.json); the fixture is test
input rather than a runtime theme file.

[Reusable UI components](behavior/ui-components.md) defines the unbranded
component APIs, customization slots, caller-owned state and everyday gallery.

[Forms and mutations](behavior/forms-mutations.md) owns admitted create commands,
the narrow write port, pure mutation phases and feature draft/lifetime rules.
Both native suites consume [title](fixtures/notes/create-titles.json),
[response](fixtures/notes/create-responses.json), and
[mutation-state](fixtures/query/mutations.json) fixtures.

[GPU effects](behavior/gpu-effects.md) owns bounded native settings, quality,
active animation time, surface/context lifetime and honest submission statistics.
Both policy suites consume [graphics fixtures](fixtures/graphics/effects.json).

[Graphics previews](behavior/graphics-previews.md) adds owned opaque raster/mesh
admission, bounded editing/camera values, comparison/filtering and a mesh product
viewer. Both native suites consume [preview fixtures](fixtures/graphics/previews.json);
Metal execution and Android Surface readback check real output and interaction.
Native Metal execution and Android Surface pixel/lifecycle tests supplement the
fixtures; cross-platform pixel identity and device performance are not promised.

[Compositor studio](behavior/compositor.md) adds owned straight RGBA overlays,
linear premultiplied filtering, masks, blend modes, blur/glow and optional profile
observations through the existing preview adapter. Both pure suites consume
[compositor fixtures](fixtures/graphics/compositor.json); native tests exercise
actual pixels, resource reuse, resizing and lifecycle. Physical-device cost is
explicitly unmeasured; follow [the protocol](../docs/GRAPHICS-PROFILING.md).
