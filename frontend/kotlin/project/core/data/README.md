# Data core

SQLiteNotesReadCache implements the optional [account read cache](../../../../../contracts/behavior/notes-read-cache.md) in a separate backup-excluded database. NotesReadCache remains swappable with Memory; cache expiry/eviction cannot remove pending commands. Generated connected Daily selects this adapter explicitly.

Android device-local storage adapters implementing domain ports from core:services.
SQLiteNotesRepository uses native SQLiteOpenHelper, Dispatchers.IO and a mutex,
with helper/cursor handles scoped to an operation. No ORM or UI dependency.
Init takes app context and a database filename without opening it. CRUD returns
domain AppResult values. Version-one initialization and statements are transactional.
The custom corruption handler preserves damaged files rather than deleting them.

`make data-test` runs value/state-owner checks; `make data-ui-test` requires a
booted Android device/emulator for real SQLite and form/navigation checks.
See [behavior](../../../../../contracts/behavior/local-notes.md),
[construction](../../../../../docs/blueprints/local-notes.md) and
[walkthrough](../../../notes/modules/project/core/data/README.md).
Account scoping, later schema migrations, observation and sync remain separate.
