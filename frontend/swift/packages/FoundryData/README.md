# FoundryData

Native device-local storage adapters implementing domain ports from FoundryServices.
SQLiteNotesRepository uses the OS SQLite library, actor isolation and scoped
connections/statements; no ORM or UI dependency. Init takes a private file URL
without opening it. List/create/update/delete return domain AppResult values.
Version-one initialization and CRUD are transactional; unknown versions and
corrupt data are refused without resetting storage.

Run `make data-test` for actual Swift file/domain checks and Kotlin state/service
checks. `make ios-test` covers the catalog consumer.
See [behavior](../../../../contracts/behavior/local-notes.md),
[construction](../../../../docs/blueprints/local-notes.md) and
[walkthrough](../../notes/modules/packages/FoundryData/README.md).
Account scoping, migrations beyond version one, observation and synchronization
remain separate capabilities.
