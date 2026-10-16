# Mobile Foundry development

Follow [Architecture](ARCHITECTURE.md) and [Organization](docs/ORGANIZATION.md).
Swift and Kotlin share behavioral contracts and fixtures while keeping native
implementations idiomatic. Keep expected failures as values at owned boundaries.
The kernels implement [outcomes and failures](contracts/behavior/kernel.md);
HTTP/services and catalog examples implement [HTTP and health](contracts/behavior/http.md).
The read-only notes exemplar implements [service seams and query state](contracts/behavior/notes-query.md).
Reusable query snapshots and async UI implement [query state and presentation](contracts/behavior/query-ui.md).
Durable local Notes CRUD implements [local data](contracts/behavior/local-notes.md);
keep disk adapters behind domain ports and never reset corrupt stores implicitly.
Remote Notes implements [commands and receipts](contracts/behavior/remote-notes.md).
Preserve the original command/input/scope across uncertain acknowledgement. Commit
receipt and record changes atomically, check replay before CAS/existence, and keep
local unversioned CRUD separate from remote receipt/version guarantees.
Identity/session lifecycle implements [session behavior](contracts/behavior/session.md).
Keep credential storage behind its port, gate work with current scope leases,
and distinguish the catalog demo authority from real authentication.
Session-bound HTTP and account Notes implement [account access](contracts/behavior/account-access.md).
Keep one-attempt HTTP separate from renewal/replay, bind work to an exact lease,
and retain the original account target for accepted writes. Guest data stays separate.
Native live readers implement [Notes observation](contracts/behavior/notes-observation.md).
Keep observation separate from CRUD, snapshots bounded and owned, notifications
after commit, and subscription cleanup tied to native lifecycle/lease owners.
Native command forms and write state implement [forms and mutations](contracts/behavior/forms-mutations.md).
App-owned product compositions implement [product shells](contracts/behavior/product-shells.md).
Keep navigation, drafts and fixture values above preview/layout branches; shared
shells remain passive layouts. Catalog examples and independently extracted
products have separate owners and acceptance evidence.
Clean native product entries implement [product entry behavior](contracts/behavior/product-entry.md).
Keep product configuration/screens separate from catalog examples. Maintain the
candidate inventory in `templates` when its selected source/dependency closure
changes; static inventory checks do not establish independent starter readiness.
Local source-copy generation implements [project CLI behavior](contracts/behavior/project-cli.md).
Keep registration explicit, copying independent, output ownership exclusive and
source unchanged. Go extraction is verified and registered; preserve its owned
module rewrite/import ordering, fixtures/migrations and Compose namespace.
Generated native shells and Go remain independently composed until a connected
capability passes its own acceptance. Follow [CLI usage](cli/README.md).
Optional product data implements [local-data starter behavior](contracts/behavior/local-data-starter.md).
Keep capability combinations explicit, validate effective overlaid build files,
and preserve the UI-only default. Local-data selection includes guest SQLite
and its independent reader, not account/auth/HTTP providers. Verify generated
native workflows and process relaunch before promoting a combination's readiness.
Workspace/Browse generation implements [shell starter behavior](contracts/behavior/shell-starters.md).
Keep app identities/state/screens above adaptive and material branches. Their
inventories select only UI navigation; register data combinations explicitly.
Native tokens and scoped themes implement [UI tokens](contracts/behavior/ui-tokens.md),
with source placement in [Styles](STYLES.md).
Optional native renderers and the procedural gallery implement
[GPU effects](contracts/behavior/gpu-effects.md).
Reusable image editing and mesh product examples implement
[graphics previews](contracts/behavior/graphics-previews.md). Keep admitted CPU
assets/edit values separate from native GPU resource ownership. Canonical assets
and provenance live in `assets`; run `make assets-check` after changing copies.
Transparent-layer previews and multipass ownership implement
[compositor studio](contracts/behavior/compositor.md); keep linear premultiplied
pixels, separate pass outputs, and honest timing/payload labels. Use
[the profiling protocol](docs/GRAPHICS-PROFILING.md) before making device budgets.
The [physics playground](contracts/behavior/physics-playground.md) keeps bounded
2D integration/contacts in pure Physics modules. Features own input, scoring
presentation, frame scheduling and pause baselines; numeric state has no renderer
handles. Do not claim CLI readiness or 3D/general physics from a catalog example.
The app-owned [camera slice](contracts/behavior/camera-photo.md) admits bounded
photos into the existing editor. Keep native camera sessions separate from CPU
photo ownership and GPU resources; stop capture when its viewfinder is inactive.
Other app areas remain scaffolds.
Implement capabilities in reviewable slices rather than treating planned
behavior as already available.

Reusable UI components and their component-local types use descriptive names
without a `Foundry` prefix, such as `ActionButton`, `LabeledTextField`, `Card`,
`SurfaceRole` and `SelectionCard`. Keep native widget names available rather
than shadowing `Button` or `TextField`. Package, token/theme and app names are
separate from this component naming convention.

## New-product readiness

The user wants an explicit handoff when a native/backend profile is ready to
start new products, with a CLI workflow analogous to Bento. Maintain
[starter readiness](docs/STARTER-READINESS.md) as relevant slices change it.
Assess Swift, Kotlin and backend profiles separately. Announce milestone changes
in the handoff with actual evidence; a catalog build is not independent starter
verification. Local-only readiness does not require real auth, sync or every UI
component. CLI/template extraction and an independently built/run generated app
are required before claiming standalone starter readiness.

## Learning notes

The user requested Bento's learning notes workflow so implementation can be
studied between sessions. Follow [the notes workflow](docs/NOTES.md) on every
meaningful implementation or investigation slice.

- Maintain [notes/README.md](notes/README.md) as the shared entry point and each
  platform's `notes/README.md` as its reading order and current coverage.
- Put Swift notes in `frontend/swift/notes` and Kotlin notes in
  `frontend/kotlin/notes`. Module notes mirror paths from the owning platform
  root, including Kotlin's `project/` prefix. Link the actual source and checks.
- Classify transferable findings into `language`, `patterns`, `concepts`,
  `techniques`, or `substrate`, following Bento's definitions. Shared mobile
  concepts, patterns, and techniques belong in root `notes`; backend notes live
  in `backend/<profile>/notes`. Follow [backend rules](backend/AGENTS.md) for
  Go boundaries and provider configuration.
- Record a one-sentence claim, origin and evidence, what and why, a useful
  example, gotchas, actual use, and related notes. Explain new Swift or Kotlin
  mechanics when the first slice uses them, including the reason for the choice
  and relevant alternatives. Update existing explanations instead of copying them.
- Keep promised behavior in contracts and source API documentation. Learning
  notes explain reasoning and limits rather than becoming a second contract.
- Distinguish documented behavior, source inspection, static checks, observed
  execution, and planned work. Use primary references, date observations, record
  relevant versions in substrate notes, and mark unsettled conclusions WORKING.
- Create notes for actual findings. Empty category directories are allowed;
  placeholder tutorials, invented verification, and undocumented guarantees are not.
- Label code examples as Runnable, Excerpt, Conceptual, or Compile-fail. Run
  complete examples using the documented native command; state what an
  intentional compiler failure demonstrates. `make notes-check` checks links,
  module paths, and example labels; it does not execute code.
- At the end of a slice, update the reading order, actual verification and
  limits, and next reading questions. Mention the changed notes in the handoff
  so the user can learn asynchronously.

## Verification

Use the relevant targets in [Makefile](Makefile) for native code changes. Run
`make notes-check` for learning-note changes. Do not repeat native builds for
prose-only edits. Run additional examples or checks when the claims being added
require them; a compile or build result alone does not establish runtime behavior.
