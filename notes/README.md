# Mobile Foundry learning notes

Use this index to study the native foundation between implementation sessions.
Swift and Kotlin each keep the six Bento note categories; shared mobile
concepts, patterns, and verification techniques live here.

## Start here

1. Read [the notes workflow](../docs/NOTES.md) for classification and evidence.
2. Follow [the Swift reading order](../frontend/swift/notes/README.md) through
   its build configuration, language mechanics, and catalog shell.
3. Follow [the Kotlin reading order](../frontend/kotlin/notes/README.md) through
   its toolchain, UI state types, and starter screen's data flow.
4. Compare what each shell owns before implementing the first shared behavior.

## Current coverage

Started 2026-10-07 after native initialization. The current notes explain the
build wiring and existing application scaffolds. Swift package and simulator
builds, Android debug assembly, and two Android starter unit tests passed during
initialization. The walkthroughs state the limits of those checks.

Both kernel libraries are empty. Identity, account, transport, persistence,
sync, reusable components, and GPU integration remain planned capabilities.
Their learning notes will arrive with actual implementation or investigation.

## Shared findings

| Directory | Responsibility |
| --- | --- |
| `concepts/` | Mobile and systems distinctions that apply to both platforms |
| `patterns/` | Approaches shared by the native implementations |
| `techniques/` | Methods for investigating or verifying shared behavior |

These categories currently reserve space for findings. Add a linked entry and
a useful reading position here when a shared note is written. Source contracts
remain in [contracts](../contracts/README.md), and accepted boundaries remain in
[Architecture](../ARCHITECTURE.md).

## Questions for the next slice

- Which success, absence, expected failure, and cancellation distinctions must
  be consistent between Swift and Kotlin?
- What can the current app builds establish, and which behaviors require
  runtime examples or device checks?
- Which kernel behavior can be expressed in a shared fixture before either
  native implementation is introduced?

Run `make notes-check` after changing learning notes. Complete code examples
also need the execution commands described in their notes.
