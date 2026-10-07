# Go backend learning notes

Follow [the shared notes workflow](../../../docs/NOTES.md) when the first Go
implementation begins. The backend has architecture placeholders and module
initialization instructions; no Go runtime behavior is implemented.

Use `modules/<source-path>` for walkthroughs relative to the Go root, such as
`modules/pkg/errors/` or `modules/internal/identity/app/command/`. Put
transferable Go mechanics in `language`, architectural approaches in `patterns`,
domain distinctions in `concepts`, verification methods in `techniques`, and
versioned tool or dependency findings in `substrate`.

## First reading

1. [Backend layout and module initialization](../README.md).
2. [Bento backend conventions](../../../../bento/backend/CONVENTIONS.md).
3. [Bento module authoring](../../../../bento/backend/MODULE-AUTHORING.md).

When code is introduced, add its source walkthrough and language explanations
to this reading order together. Record build, static-check, and runtime evidence
separately. Empty category directories reserve space for actual findings.
