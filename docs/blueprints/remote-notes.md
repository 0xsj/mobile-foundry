# Build and explore remote Notes

Start the Go API and register a development user using
[the native Go setup](go-native-session.md). For retained data, apply owned
migrations before startup:

```sh
cd backend/go
make db-up migrate
STORAGE=postgres AUTH_MODE=identity make dev
```

`DATABASE_URL` supplies all owned pools; `NOTES_DATABASE_URL` can override Notes.
Tests use a separate disposable database; never reset the development volume.
On a retained database, running the migrator adds the new Notes schema without
resetting Identity/Account/Audit. Startup checks migrations and refuses missing
versions. Memory mode is useful for quick exploration but loses data on exit.

Build/open either native catalog, then Studio → Open catalog → Go identity and
accounts. Connect and sign in. Under Remote notes, create a title, Edit it,
Save, and confirm Delete. Switching accounts gives a separate collection;
renewing/reopening restores the selected account. Refresh is an explicit read.
The card intentionally displays a bounded first page.

The app owns recovery commands. Keep command identity and full admitted input
before dispatch. After an uncertain acknowledgement, use Check write receipt;
if necessary, Retry original write. These controls preserve the key and target.
Current catalog recovery input survives renewal and account switching only while
the owner remains alive. Process-safe pending recovery requires a future native
outbox; committed server receipts already survive PostgreSQL API restart.

Read [the contract](../../contracts/behavior/remote-notes.md) before swapping
providers. Implement RemoteNotesRepository's versions and receipts rather than
mapping it onto a provider's unguarded CRUD API. Keep provider SDKs behind the
native port and Account access behind the Go application port.

From the repository root:

```sh
make http-test
make go-check go-test
make go-native-test
python3 -B scripts/go_native_test.py --ios-device-only
```

The real native target owns a temporary Identity-enabled Go API and refuses
fixture secrets in its logs. The iOS option also runs the catalog/relaunch
workflow; the existing `--devices` option additionally requires a connected
Android test device for the session workflow. PostgreSQL acceptance:

```sh
cd backend/go
make integration-test
# Isolated local-server alternative when Docker is unavailable:
POSTGRES_TEST_MODE=local POSTGRES_BIN_DIR=/opt/homebrew/bin make integration-test
```

Start learning with [command receipts](../../notes/patterns/command-receipts-and-uncertain-writes.md),
then the [Go walkthrough](../../backend/go/notes/modules/internal/notes/README.md)
and native Services/app notes, reading step 52. This is a connected catalog
foundation; CLI extraction and independently built connected products remain
separate readiness milestones.
