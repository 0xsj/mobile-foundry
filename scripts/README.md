# Repository tooling

Add executable repository tasks here: shared fixture processing, contract
generation, asset conversion, and checks spanning multiple implementations.

Keep native build configuration in the native project and backend commands
with the selected backend. Native setup commands are in [Setup](../docs/SETUP.md).

`check_notes.py` uses Python 3's standard library to check Markdown file links,
mirrored module-note directories, and Swift/Kotlin example labels. Run it with
`make notes-check` from the repository root. Build caches and dependency trees
are excluded. It does not execute examples or check external URLs or anchors;
see [the learning workflow](../docs/NOTES.md) for example verification.
