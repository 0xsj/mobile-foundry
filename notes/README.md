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
5. Read the backend setup notes for the profile you are exploring:
   [Go](../backend/go/notes/README.md),
   [Supabase](../backend/supabase/notes/README.md), or
   [Firebase](../backend/firebase/notes/README.md).

## Current coverage

Started 2026-10-07 after native initialization. The current notes explain the
build wiring and existing application scaffolds. Swift package and simulator
builds, Android debug assembly, and two Android starter unit tests passed during
initialization. The walkthroughs state the limits of those checks.

The first kernel slice, completed 2026-10-08, adds typed outcomes, the shared
failure vocabulary, owned validation fields, retry timing, and public projection.
Both platforms consume one canonical fixture set. Their indexes and walkthroughs
record tests, the Swift compiler example, and verification limits.

Identity, account, transport, persistence,
sync, reusable components, and GPU integration remain planned capabilities.
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
for the first shared finding. Patterns and techniques still reserve space.
Add a linked entry and useful reading position when a shared note is written. Source contracts
remain in [contracts](../contracts/README.md), and accepted boundaries remain in
[Architecture](../ARCHITECTURE.md).

## Questions for the next slice

- How should the first HTTP adapter admit response bodies and preserve the
  kernel's failure, defect, and cancellation distinctions?
- What can the current app builds establish, and which behaviors require
  runtime examples or device checks?
- Which shared HTTP fixtures can establish behavior before connecting a backend?

Run `make notes-check` after changing learning notes. Complete code examples
also need the execution commands described in their notes.
