# Repository tooling

`go_native_test.py` owns a temporary Go Identity API and verifies real
URLSession/OkHttp session/account flows. Run `make go-native-test`, or
`make go-native-device-test` with an iOS simulator and connected Android device
for catalog/native-vault checks. It owns an ADB reverse mapping and verifies iOS
execution without skips. `--android-device-only` supports a focused rerun. Fixture
users exist only in its API; no existing backend/database is reset. See
[setup](../docs/blueprints/go-native-session.md).
It now also verifies remote Notes CRUD, CAS and lost-acknowledgement receipt
recovery on both native transports. `--ios-device-only` adds iOS remote Notes
controls/relaunch without requiring Android. Failed iOS results are retained
under `.cache/go-native-ios/Failures` for diagnosis; passing temporary results
are checked before cleanup. Read [remote setup](../docs/blueprints/remote-notes.md).

Add executable repository tasks here: shared fixture processing, contract
generation, asset conversion, and checks spanning multiple implementations.

Keep native build configuration in the native project and backend commands
with the selected backend. Native setup commands are in [Setup](../docs/SETUP.md).

`check_notes.py` uses Python 3's standard library to check Markdown file links,
mirrored module-note directories, and Swift/Kotlin example labels. Run it with
`make notes-check` from the repository root. Build caches and dependency trees
are excluded. It does not execute examples or check external URLs or anchors;
see [the learning workflow](../docs/NOTES.md) for example verification.

`check_starter_profile.py` checks the Daily inventory against current
Swift/Kotlin source: existence, dependency closure, fixture selection, product-only
settings overlay and product source/catalog separation. Run `make starter-check`.
It delegates to the CLI's shared validator and does not generate files; see
[extraction ownership](../templates/README.md).

`install_cli.py` links the repository executable into PREFIX/bin, refusing another
existing command. Run `make cli-install`, optionally with `CLI_PREFIX`; the default
is ~/.local. [CLI usage](../cli/README.md) covers PATH and terminal commands.
