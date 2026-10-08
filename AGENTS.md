# Mobile Foundry development

Follow [Architecture](ARCHITECTURE.md) and [Organization](docs/ORGANIZATION.md).
Swift and Kotlin share behavioral contracts and fixtures while keeping native
implementations idiomatic. Keep expected failures as values at owned boundaries.
The kernels implement [outcomes and failures](contracts/behavior/kernel.md);
HTTP/services and catalog examples implement [HTTP and health](contracts/behavior/http.md).
The read-only notes exemplar implements [service seams and query state](contracts/behavior/notes-query.md).
Reusable query snapshots and async UI implement [query state and presentation](contracts/behavior/query-ui.md).
Native command forms and write state implement [forms and mutations](contracts/behavior/forms-mutations.md).
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
Other app areas remain scaffolds.
Implement capabilities in reviewable slices rather than treating planned
behavior as already available.

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
