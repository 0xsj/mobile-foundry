# FoundryServices

Implemented health plus a provider-independent notes port with memory and HTTP
adapters. See [HTTP and health](../../../../contracts/behavior/http.md) and
[notes/query behavior](../../../../contracts/behavior/notes-query.md), and
[Module walkthrough](../../notes/modules/packages/FoundryServices/README.md) for source flow, language mechanics, checks and limitations.

Run `make http-test` from the repository root. Runtime libraries do not load
checkout fixtures. The catalogs inject responses and need no running backend.

CreateNote admits input before the separate NoteCreator write port.
InMemoryNoteCreator provides instance-local append/list snapshots; HTTPNoteCreator
maps POST notes to an admitted receipt. See [forms/mutations](../../../../contracts/behavior/forms-mutations.md).
