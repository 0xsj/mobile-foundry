# Local CLI and independent source copies

Claim: a starter is a selected, configured dependency closure with its own build
evidence; a copy command alone does not establish a usable new product.

Origin: the user's terminal workflow request, 2026-10-10. The implementation uses
Python 3.11+ standard library and Bento's registered source-copy approach, with
native-specific build/fixture selection. Update, 2026-10-13: Go now has independent
extraction and acceptance; read [Go source-copy verification](go-source-copy-verification.md).

## What and why

The [registry](../../mobile-foundry.starters.json) names admitted profiles rather
than discovering arbitrary directories. The [Daily inventory](../../templates/profiles/native-daily.json)
keeps native source relative depth and a product-only Kotlin settings overlay.
The [validator](../../cli/mobile_foundry/profiles.py) is shared by list/init and
the static inventory command. It checks the repository's declared dependency
syntax, source/fixture coverage and absence of catalog dependencies; it is not
a general Gradle/Swift build parser.

[Generation](../../cli/mobile_foundry/generation.py) admits product inputs,
snapshots regular source bytes, prunes machine/private artifacts and refuses
source overlap or existing output before exclusively creating PARENT/NAME.
Identity edits happen only in owned output. Failure/interrupt cleanup checks the
root's identity before removing it. This makes source preservation and destination
ownership explicit; it does not provide a concurrent filesystem transaction.
Included symlinks and special files are refused rather than silently followed.

[Guides](../../cli/mobile_foundry/documents.py) are generated for selected platforms
with portable build commands. Product label/installed identity change independently
of reusable package names, source namespace and scheme. Provenance records copied
bytes, profile, inputs and optional Git state without requiring the source location.
An installed command links to the checkout, while generated products are copies.

## Useful example

After installing the command, run from an existing products parent:

```sh
mobile-foundry init canopy --platform both --app-id com.example.canopy
cd canopy
make ios-build swift-test
make android-build android-test
```

Configure local native tools and follow SETUP.md before invoking the builds.
`--output` selects an existing parent, not the final project directory. Initialization
does not download tools, build, initialize Git or connect a backend. A terminal
may prompt for the platform; automation must select one explicitly.

## Evidence and actual use

Observed 2026-10-10 with Python 3.14.8: 13 CLI checks pass for real subprocess
entry, all three platform selections, identities, registered source override,
safe destination refusal, cache/secret exclusion, source preservation, symlink/
special-file rejection, fault/interrupt cleanup and install/doctor behavior.
The real command was installed in ~/.local/bin and listed registrations from an
unrelated directory. Go was reported as planned and refused before writing.

Three products were generated under `/private/tmp/mobile-foundry-cli.nQJrnN`:
Orbit Lab (`both`, com.example.orbitlab), Swift Sprout (`swift`) and Kotlin Sprout
(`kotlin`). The combined output builds independently and its included package/
module checks pass: Swift Kernel 7, Query 3, UI 9; Kotlin Kernel 9, Query 3, UI 8.
The copied wrappers and portable fixture paths are exercised by those commands.
Swift-only and Kotlin-only builds also pass two native entry cases each and
their configured launches were captured/inspected. A fresh Kotlin-only build
executes all 58 tasks without task-cache reuse. Source/build-input audit finds
no required paths back to the checkout; cached build reports are distinct artifacts.

A [temporary feature probe](../../cli/acceptance/README.md) replaced Today only
in the combined output. Its injected memory service returns Kernel results;
the state owner settles reads/writes through QueryState/MutationState. Three
hosted iOS cases and three actual Android Activity cases pass with no failures/
skips. Android taps Increment twice and observes current content and the receipt;
Swift verifies injected provider sharing and isolation. Native branding checks
use the built product inputs. These are local synchronous probes, not durable
storage, async cancellation or provider acceptance.

See [readiness](../../docs/STARTER-READINESS.md) and the native product walkthroughs
for final single-platform launch evidence and current supported combinations.
Verification logs/results/captures live in the temporary parent; they are not
copied as starter source.

## Gotchas and related reading

App IDs are admitted as a shared native subset: lowercase letter-led segments
containing letters/digits, separated by dots. Apple's
[bundle ID character rules](https://developer.apple.com/documentation/BundleResources/Information-Property-List/CFBundleIdentifier?changes=late_5)
exclude underscores that Android permits; Android excludes iOS's hyphens.
The CLI consistently uses the common subset for all combinations.

The feature probe first failed because Swift imported a transitive Query package
without linking its product, and Kotlin's same-named resource classes made a
wildcard R import ambiguous. Adding explicit package links and the explicit
product.R import fixed those failures. This is why a first-feature check adds
evidence beyond a blank shell build. The default profile stays minimal.

Doctor initially exposed a stale JAVA_HOME. Android Studio's actual launcher was
JDK 21.0.6; the declared compilation toolchain is 17. Doctor now reports those
separately and accepts the pinned Gradle launcher's compatible range. A tool
report is not proof of compilation-toolchain resolution or native execution.

Copies do not receive updates automatically. New shell/backend/capability
profiles need closure and native acceptance; changing a status alone cannot
establish availability. Release signing/icons, physical-device behavior and
connected auth/data remain separate gates. The source is a trusted local
checkout, not an arbitrary hostile template input.

Read [standalone verification](standalone-starter-verification.md),
[product extraction](../patterns/product-entry-and-extraction-closure.md) and
[CLI usage](../../cli/README.md). Next: which additional shell or optional data
capability should be extracted, and how should a future Go combination prove
client/backend behavior independently?
