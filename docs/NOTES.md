# Learning notes workflow

Keep learning material alongside each implemented slice so it can be read
between development sessions. This follows Bento's separation of source
walkthroughs from language mechanics, architectural patterns, domain concepts,
verification methods, and tool behavior.

Start at [the shared notes index](../notes/README.md), then follow the platform
reading order. [AGENTS.md](../AGENTS.md) makes this workflow part of future work.

## Locations and ownership

```text
notes/                       # Shared mobile learning
  README.md                  # Entry point and cross-platform reading order
  concepts/
  patterns/
  techniques/
frontend/swift/notes/         # Swift notebook
  README.md
  modules/<source-path>/
  language/
  patterns/
  concepts/
  techniques/
  substrate/
frontend/kotlin/notes/        # Kotlin notebook, same six categories
backend/go/notes/             # Go notebook, same six categories
backend/supabase/notes/       # Supabase notebook, same six categories
backend/firebase/notes/       # Firebase notebook, same six categories
```

Module paths are relative to the owning implementation root. For example,
`frontend/swift/packages/FoundryKernel/Sources/FoundryKernel/` maps to
`frontend/swift/notes/modules/packages/FoundryKernel/Sources/FoundryKernel/`.
Kotlin's `frontend/kotlin/project/core/kernel/` maps to
`frontend/kotlin/notes/modules/project/core/kernel/`.

Create a module directory when it has a finding or walkthrough. Notes can cover
a whole package or app, or a narrower source directory, as long as the path
matches that scope. Backend notebooks follow the same rule relative to their
profile root, such as `backend/go/notes/modules/internal/identity/` for
`backend/go/internal/identity/`. The provider notebooks start with substrate
notes about actual configuration and observed local behavior.

## Classify findings by lifespan

| Category | What the note is true of | Revisit when |
| --- | --- | --- |
| `modules` | This implementation and its callers | Source, callers, or checks change |
| `language` | Syntax, type system, or language runtime | Compiler or runtime assumptions change |
| `patterns` | A reusable architectural approach | Its constraints or tradeoffs change |
| `concepts` | A domain or systems distinction | Understanding of the domain changes |
| `techniques` | A method of investigating or verifying | New evidence changes the method |
| `substrate` | A framework, SDK, dependency, or tool configuration | Its version or configuration changes |

An actor or sealed interface belongs in language; SwiftUI task lifetime or
Compose lifecycle collection belongs in substrate. Who owns background work
belongs in patterns; cancellation versus an uncertain remote write belongs in
concepts. A deterministic way to reproduce a race belongs in techniques. Their
use in a particular session or sync implementation belongs in module notes.

Use root notes for findings that apply to both platforms. Platform-specific
findings stay in that platform's notebook. Keep transferable explanations free
of local source paths; module walkthroughs supply the concrete usage links.

## Write alongside the slice

1. Read existing notes and relevant contracts before changing a capability.
2. Record questions and observations while implementing or investigating.
3. Explain the actual control and data flow in the mirrored module notes. Link
   source, tests, contracts, and the language or framework mechanics it uses.
4. Extract new transferable findings into their category, or revise an existing
   note. Explain syntax at its first use, with a small useful example.
5. Record the checks actually performed and what each establishes. Run complete
   examples and check note links with `make notes-check`.
6. Update the platform index with a short reading sequence, current coverage,
   and the next questions worth studying. Update the shared index when a new
   capability or shared finding changes its reading order.

Keep related behavior and language explanations in one learning handoff. A
reader should be able to follow the slice without reconstructing the chat.
Revise notes when code changes; date a correction when it changes a conclusion.
Preserve superseded conclusions only when their correction teaches something.

## Note shape and evidence

Use a descriptive kebab-case filename, a clear title, and a one-sentence claim.
Start from [the finding template](templates/LEARNING-NOTE.md) or
[the module walkthrough template](templates/MODULE-WALKTHROUGH.md). Adapt the
sections to the subject rather than filling them mechanically.

The useful sequence is Origin, What and why, Example, Gotchas, Used in, and
Related. Module notes add a source reading order and verification limits.
Substrate notes record relevant versions, configuration, observation date,
and primary references. Keep questions and provisional interpretations labeled
WORKING, with a concrete way to settle them.

| Evidence | What it can establish |
| --- | --- |
| Primary documentation | The documented contract for the referenced version |
| Source inspection | The implementation's visible structure and code paths |
| Static or compiler check | Properties covered by that particular check |
| Observed execution | The outcome of the stated example or scenario |
| Planned work | An intended next step; no completed behavior |

Name important limits beside the claim: a debug build proves the app compiles
and packages, while screen behavior requires execution. A cancellation example
does not establish that a remote write was undone. A stubbed repository does
not establish persistence or offline synchronization.

## Examples and checks

The first line of each fenced `swift` or `kotlin` example identifies its scope:

- `// Runnable`: a complete example with its execution command, prerequisites,
  and expected outcome recorded beside it. Run it before calling it verified.
- `// Excerpt`: selected source that depends on omitted context; link that source.
- `// Conceptual`: an illustrative sketch that is not a complete program.
- `// Compile-fail`: an intentional compiler rejection; record the command,
  compiler version, and expected diagnostic, and check that rejection.

Prefer examples backed by the existing package or Gradle project's tests when
they need framework or library dependencies. Commands in `sh` fences are
instructions; they run only when explicitly invoked.

```sh
make notes-check
```

This Python 3 check validates inline relative file links, mirrored module
directories, and Swift/Kotlin example labels. It ignores URL destinations and
heading fragments. It does not execute snippets, validate external URLs, or
establish the notes' technical claims; run the native commands those claims need.

## Reading between sessions

Use each platform index's current reading order: tool configuration, the actual
module flow, then the linked language and pattern explanations. Walkthroughs
finish with a few questions that can be answered from the linked code or a
small experiment. At the next session, bring those questions back into the
slice and revise the notes with the resulting evidence.

The first kernel slice introduces behavior, language explanations, and shared
fixtures together; follow the native notebook indexes for that handoff.
Use the same workflow for each subsequent capability. Category directories reserve a place for findings;
they do not contain advance tutorials for unimplemented capabilities.

## Reference workflows

- [Bento Python instructions](../../bento/backend/python/AGENTS.md) and
  [notes index](../../bento/backend/python/notes/README.md).
- [Bento Rust instructions](../../bento/backend/rust/AGENTS.md) and
  [notes index](../../bento/backend/rust/notes/README.md).
