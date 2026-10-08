# Mobile Foundry learning notes

Use this index to study the native foundation between implementation sessions.
Swift, Kotlin, and each backend keep the six Bento note categories; shared mobile
concepts, patterns, and verification techniques live here.

## Start here

1. Read [the notes workflow](../docs/NOTES.md) for classification and evidence.
2. Follow [the Swift reading order](../frontend/swift/notes/README.md) through
   its build configuration, language mechanics, and catalog shell.
3. Follow [the Kotlin reading order](../frontend/kotlin/notes/README.md) through
   its toolchain, UI state types, and starter screen's data flow.
4. Read [expected failures and diagnostics](concepts/expected-failures-and-diagnostics.md),
   then each native kernel walkthrough to compare their common behavior and
   language differences.
5. Follow the new native HTTP and health walkthroughs, then compare
   [transport/service/screen](patterns/transport-service-and-screen.md),
   [deadline ownership](concepts/deadlines-and-owned-cancellation.md), and
   [fixture versus native evidence](techniques/shared-fixtures-and-native-adapters.md).
6. Read the backend setup notes for the profile you are exploring:
   [Go](../backend/go/notes/README.md),
   [Supabase](../backend/supabase/notes/README.md), or
   [Firebase](../backend/firebase/notes/README.md).
7. Follow the native notes feature walkthroughs and [provider seams](patterns/transport-service-and-screen.md#provider-seams-and-query-state)
   to trace screen → state owner → domain port → memory/HTTP adapter.
8. Read [query state and rendering ownership](patterns/query-state-and-rendering.md),
   then each native query/UI walkthrough to compare generic snapshots, native
   content slots, and the feature orchestration that remains outside them.

## Current coverage

Started 2026-10-07 after native initialization. The current notes explain the
build wiring and existing application scaffolds. Swift package and simulator
builds, Android debug assembly, and two Android starter unit tests passed during
initialization. The walkthroughs state the limits of those checks.

The first kernel slice, completed 2026-10-08, adds typed outcomes, the shared
failure vocabulary, owned validation fields, retry timing, and public projection.
Both platforms consume one canonical fixture set. Their indexes and walkthroughs
record tests, the Swift compiler example, and verification limits.

The HTTP slice, completed 2026-10-08, implements native transports, request
admission, problem decoding, cancellation/deadlines and health domain admission.
Both catalogs expose six injected scenarios without a backend. Notebook indexes
record host, build and runtime evidence. Follow their updated reading orders.

The 2026-10-08 [provider seam review](patterns/transport-service-and-screen.md#provider-seams-and-query-state)
separates implemented transport injection from the planned domain service and
query-state boundaries. The subsequent notes/query slice implements those
boundaries for a read-only list with memory and HTTP adapters. Platform indexes
record shared admission, state-owner and device evidence; managed SDK adapters,
shared caching, writes and persistence remain separate work.

The 2026-10-08 query/UI slice extracts generic value state and native async
presentation, used by notes and a scalar state gallery. Both platforms pass
shared state fixtures and existing feature regressions. Platform indexes record
the ten Android device tests and manual iOS state/action/layout checks.

Identity, account, persistence, sync, broader UI controls, and GPU integration
remain planned capabilities.
Their learning notes will arrive with actual implementation or investigation.

Backend profiles were scaffolded 2026-10-08. Go has reserved module layers and
no `go.mod`; the provider notebooks record local stack configuration and the
checks performed. Provider setup alone does not establish native session,
repository, or synchronization behavior.

## Shared findings

| Directory | Responsibility |
| --- | --- |
| `concepts/` | Mobile and systems distinctions that apply to both platforms |
| `patterns/` | Approaches shared by the native implementations |
| `techniques/` | Methods for investigating or verifying shared behavior |

Read [expected failures and private diagnostics](concepts/expected-failures-and-diagnostics.md)
for the first shared finding, then [request ownership](concepts/deadlines-and-owned-cancellation.md),
[service boundaries](patterns/transport-service-and-screen.md), and
[parity verification](techniques/shared-fixtures-and-native-adapters.md).
Add a linked entry and useful reading position when a shared note is written. Source contracts
remain in [contracts](../contracts/README.md), and accepted boundaries remain in
[Architecture](../ARCHITECTURE.md).

## Questions for the next slice

- Which additional controls should exercise keyboard, focus and validation behavior?
- Which form and mutation should exercise field validation and submission state next?
- What additional contract is needed before the notes feature can safely write or persist data?
- How should a feature own repeat/retry work and suppress obsolete results?
- Which second asynchronous feature would justify extracting request orchestration?
- What evidence is required before a connected write can be safely replayed?

Run `make notes-check` after changing learning notes. Complete code examples
also need the execution commands described in their notes.
