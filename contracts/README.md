# Shared native contracts

- `http`: Public wire specifications for owned APIs.
- `behavior`: Platform-independent behavioral specifications for kernel,
  transport, session, sync, and graphics boundaries.
- `fixtures`: Portable inputs, expected outcomes, and failure scenarios that
  both Swift and Kotlin implementations can consume.

Keep application behavior separate from backend wire details. Managed SDKs
may use different wire protocols while satisfying an application's repository
or session interface.

[Placeholder app shell](behavior/app-shell.md) defines four peer destinations,
catalog presentation/back navigation, material ownership and feature lifetime.

Generated Swift and Kotlin wire models live inside their platform service
boundaries. The canonical specification and generator inputs live here.
[Kernel outcomes and failures](behavior/kernel.md) is the first behavioral
contract, with [canonical failure fixtures](fixtures/kernel/failures.tsv)
consumed by both native test suites. HTTP specifications remain reserved for
the first owned transport or API workflow.

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
