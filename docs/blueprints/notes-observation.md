# Native observation construction

Authority: [Notes observation](../../contracts/behavior/notes-observation.md),
[local Notes](../../contracts/behavior/local-notes.md),
[account access](../../contracts/behavior/account-access.md) and [architecture](../../ARCHITECTURE.md).

| Owner / files | Symbols and responsibility |
| --- | --- |
| Services/NotesObserver.swift / .kt | NotesObserver native stream port; ObservableNotesRepository composed capability |
| Services/NotesRepository.swift / .kt | Memory current snapshots and successful mutation publication |
| Data/NotesChanges.swift / .kt | Process-local, path-keyed invalidations; bounded signals, registration before first read, cancellation cleanup |
| Data/SQLiteNotesRepository.swift / .kt | Observe snapshots through scoped reads; notify after successful commit |
| Data/AccountNotesRepositories.swift / .kt | Expose the composed capability while keeping CRUD/session decorators unchanged |
| Catalog LiveNotesStore / LiveNotesReader | Query snapshots, independent subscription generation, pause/resume/restart/terminal close and lease admission |
| Catalog LiveNotesCatalogModel / LiveNotesCatalogView / LiveNotesScreen | Retain per-route sources, two readers, CRUD writer, storage selection and native lifecycle ownership |
| Catalog AccountAccessPanel | Add one current-account live projection without leaking closed-lease content |
| Android CatalogPresentationModel / AppShell | Retain the nested catalog ViewModelStore across configuration changes; clear on dismissal/final destruction |
| Shared observation scenarios / native checks | Current state, coalescing, multiple consumers, independent pause, native SQLite notifications and cleanup races |

Construction order: define observation independently of CRUD; implement Memory
publication; adapt SQLite through a process-local invalidation registry; preserve
commit notifications despite caller cancellation; build generation-owned readers;
compose two readers and an existing writer with native lifecycle ownership; add
account admission; verify convergence/cleanup with controlled races and native
storage/UI; record native reading step 44 and actual limits. No shared request
runtime, provider integration or new database version is part of this plan.
