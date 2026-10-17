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
Account Files/Media implements [file lifecycle](contracts/behavior/files-media.md).
Keep private domain state and named command/query handlers; metadata, receipts
and required facts commit atomically. Objects remains an application-owned port.
Verify immutable content against declared size/digest; commit inaccessibility
before external deletion and preserve the same command on cleanup uncertainty.
Native binary requests stay bound to the exact session lease. Catalog retry
bytes are scoped, bounded and ephemeral; they do not establish durable sync.
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
capability passes its own acceptance. Daily's explicit
[go-connected selection](contracts/behavior/go-connected-starter.md) composes
Identity/accounts/remote Notes through native ports. Preserve exact-lease binding,
deployment-bound device credentials and [durable pending Notes](contracts/behavior/pending-notes.md).
Persist exact input before HTTP, settle receipts before clearing pending and never
automatically replay on restore. Keep unreadable stores; require reload before
new writes and guard terminal replacement by original command ID. Never infer
Files/inbox/push/sync selection from backend co-location; independently exercise
generated screens and process restoration before promoting readiness.
Follow [CLI usage](cli/README.md).
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

The Go [Realtime](contracts/behavior/realtime.md),
[core identity lifecycle](contracts/behavior/identity-recovery.md) and
[portable operations](contracts/behavior/backend-operations.md) contracts extend
the backend starter. Keep live hints separate from durable events/sync and
preserve versioned revocation vocabularies. SMTP configuration stays private and
out of generated copies. Native socket consumers and recovery forms require
their own selected source closure and independent product acceptance.

Daily go-connected selects [native registration](contracts/behavior/native-registration.md).
Keep profile confirmation separate from session issuance and personal-account
bootstrap. Clear transient passwords, make sign-in explicit and never replay
registration automatically after uncertainty. Check both owner generation and
session epoch before publishing late results.

Daily go-connected selects [native workspace management](contracts/behavior/native-workspaces.md).
Keep versioned account observations separate from session grants. Preserve exact
leases and strong rename preconditions, require fresh readback after conflicts
or uncertainty, and never invent Account receipts or replay non-idempotent creates.
Creating/renaming does not silently switch scope or reset pending Notes intent.

Daily go-connected selects [read-only Notes caching](contracts/behavior/notes-read-cache.md).
Keep cache storage separate from pending intent, restore/compare journal checkpoints
before fallback, label saved pages and require fresh reads before new writes.
Cache failures are optional warnings; they cannot bypass session or journal gates.

Daily go-connected now selects [native lifecycle/live ports](contracts/behavior/native-identity-live.md).
Keep public recovery separate from exact-lease authenticated operations, clear
password/code drafts on submit/background/dismissal and never replay mutations.
Treat interrupted password changes as credential uncertainty. Live hints must
match the deployment/account and queue reads behind active writes/settlement;
never cancel or replay a pending write in response to a hint.

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

The backend [Notifications slice](contracts/behavior/notifications.md) owns in-app
delivery decisions, first-read state and media preferences. Commit source markers
with both accepted and suppressed decisions; do not resurrect muted events on replay.
Keep audience mapping explicit and external push separate from in-app transactions.
Native inbox consumers use the same contract through account/principal-bound
ports and exact session leases. Invalidate owner generations on every bind,
including renewal. Require authoritative readback after unconfirmed settings;
do not invent receipt or automatic replay semantics for settled inbox mutations.

The backend installation registry follows [installation behavior](contracts/behavior/installations.md). Keep installation proof separate from auth/delivery tokens; authenticate session evidence through credential v2. Preserve proof/CAS/target uniqueness and retirement/inbox/tombstone atomicity. Old cleanup must never unlink a rebound session. Native token collection and actual push are separate capabilities.

Native installation owners use the same behavior through secure load-or-create
vaults and exact-lease registry ports. Keep proof app/deployment-scoped across auth
changes. Rebind/read on lease changes, require readback after unconfirmed writes,
and never reset unreadable proof or replay a registration implicitly. Sign-out
uses Identity revocation; do not queue old-session device DELETE operations.

Native [system notification previews](contracts/behavior/system-notifications.md)
keep OS permission, account preferences and provider registration separate.
Prompt only on explicit action; re-read settings on foreground return. Keep
payloads admitted and non-authoritative, opens bounded and app-owned, clear only
owned notification identifiers, and never infer delivery from OS acceptance.
Do not add implicit account switching or inbox mutations to preview taps.

Native [provider token collection](contracts/behavior/push-tokens.md) keeps app/process
addresses separate from permission, installation proof and session-bound writes.
Collect explicitly; callbacks update only a redacted ephemeral candidate. Keep
SDKs in app adapters, provider setup optional and route/signing selection explicit.
Do not silently replace the registry's FCM registration-token contract with FIDs,
infer delivery from collection or promote connected CLI readiness from adapter tests.

The [backend push dispatch foundation](contracts/behavior/push-dispatch.md) is an
optional internal use case. Keep Identity eligibility behind the consumer-owned
capability port and recheck the exact target after that call. Never hold a store
lock across a provider call. Retire only the rejected owner/version; uncertain
provider errors never erase tokens. No automatic send/worker or exactly-once
claim follows from this foundation or its injected gateway tests.

[Optional provider adapters](contracts/behavior/push-providers.md) require explicit
PUSH_CONFIG_FILE with owned Identity, exact route allowlists and private server
keys. Default runtime is off. Keep fixed provider endpoints, TLS/no redirects,
bounded replies, redacted transport errors and strict invalid-address evidence.
Never copy provider keys into generated products or infer live cloud delivery
from local protocol tests. Acceptance processes must clear host PUSH_CONFIG_FILE.

Native [remote preview admission](contracts/behavior/remote-notifications.md) keeps
transport envelopes, foreground receipt and default/tap opens distinct. Preserve
strict application fields and reviewed bounded SDK metadata. Kind plus ID guards
deduplication/consumption; receipt never navigates or grants account authority.
Clear accepted Android launch extras before recreation. Simulator/fixture opens
do not establish live APNs/FCM delivery or connected CLI readiness.

Optional [queued push previews](contracts/behavior/push-jobs.md) retain immutable
original owner/revision intents. Preserve atomic replay, random claim fencing,
the committed sending marker and conservative uncertain outcomes. Never recover
a post-marker error/crash by blindly resending. Keep jobs Notifications-owned and
generic polling free of business policy; stop workers before pools. Acceptance
processes must also clear inherited PUSH_JOBS_ENABLED.

[Owned job recovery](contracts/behavior/push-job-recovery.md) cancels only before
the send marker and replaces eligible terminal jobs with a new linked intent.
Preserve original outcomes, one direct child per source and immutable duplicate-risk
acknowledgement. Never reset uncertain work or silently affect descendants. Keep
current installation/session proof at the edge, exact acknowledgement admission
and atomic memory/PostgreSQL rules; this is not cross-session administration.
