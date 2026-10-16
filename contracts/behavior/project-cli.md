# Local project CLI

The `mobile-foundry` command creates independent projects by copying an explicitly
registered native profile and its dependency/test closure. Supported combinations
are Swift, Kotlin and both using Daily, Workspace or Browse. Daily optionally
selects `local-data`. Backend `none` remains default; Go is independently verified
and available, including API-only output. Its [extraction contract](go-starter.md)
defines module identity, source closure, migrations, namespace and acceptance.
Native shells with Go remain unconnected until a native provider capability is selected.

## Commands and inputs

| Command / input | Behavior |
| --- | --- |
| `list [--source PATH]` | List registered profiles, platform choices, selected capabilities and planned backends. Read-only. |
| `init NAME --platform swift\|kotlin\|both` | Copy the selected combination into `OUTPUT/NAME`; OUTPUT is an existing parent and defaults to the terminal's current directory. |
| `--profile native-daily\|native-workspace\|native-browse` | Select a registered inventory; Daily remains the default. Workspace/Browse select UI-only placeholder navigation with their own tests. |
| `--with local-data` | Select the registered Daily/UI/local-data combination. Omission keeps the UI-only starter. Unknown, repeated, planned or unregistered combinations are refused before output creation. |
| `--backend none\|go` | Default frontend-only mode or a separately configured Go API. Planned/unknown backends are refused before writing output. |
| `--platform none --backend go` | Backend-only output; refuses native profile/capability/app-ID/signing inputs. |
| `--go-module DOMAIN/PATH` | Go module identity; requires --backend go. Default example.com/NAME/backend; conservative lowercase path admission. |
| `--display-name TEXT` | Optional product label; otherwise title-case the project name. Limited to a single line, 1–60 characters; xcconfig comment/substitution syntax is refused. Android XML/Android string escaping is applied. |
| `--app-id ID` | Optional installed identity for selected apps; default `dev.example.` plus the project's alphanumeric name. Valid shared Swift/Android identifier segments; source module/R/component names stay unchanged. |
| `--ios-team ID` | Optional deliberate ten-character Apple team input, valid only when Swift is selected. Default blank. No secrets or signing keys are copied. |
| `--source PATH` | Another local checkout containing the registry, selected profile and admitted source. Symlinked source selections are refused. |
| `doctor --platform swift\|kotlin\|both` | Check selected local toolchains without installing dependencies or building apps. Report missing tools/configuration and return a failure exit code when requirements are missing. |

CLI source discovery is anchored to the executable, not the caller's working
directory. A local install links that executable into a chosen `bin` directory;
the install refuses to overwrite another executable. Initialization requires no
Xcode, Android SDK, network, Go runtime or package-manager installation.

## Admission and copy ordering

Project names are 1–80 lowercase letters/digits/hyphens, starting with a letter
and ending with a letter/digit. Reject escaping/absolute paths and invalid product
inputs. Load and validate the registered profile, select platform(s), validate
source/overlay closure, preflight all admitted files and check the destination
before creating anything. Capability combinations have explicit complete
inventories; the CLI does not merge arbitrary overlays or guess compatibility.
Validation reads effective overlaid native manifests/settings, selected partial
source/test trees and required capability files. Output/source trees must not
contain one another.
App IDs use at least two lowercase dot-separated segments starting with letters
and containing letters/digits, at most 180 characters. This shared subset excludes
underscores permitted by Android and hyphens permitted by iOS.
Existing destinations are always refused, including empty directories and
dangling symlinks. Generation does not write into the current directory itself.

Create the destination exclusively; only a destination owned by this invocation
may be removed if copying/configuration fails or generation is interrupted.
Source files are never changed. Preserve executable mode for wrappers, apply
selected overlays, set installed identity/labels/team, then write portable root
build/setup documentation and provenance. Unexpected dependency/I/O failures
produce a concise CLI error and nonzero exit code, without reporting success.

Copy regular source files only. Omit caches/build outputs, VCS/editor/user state,
local SDK settings, real environment files, credentials, keystores and signing
files. Source and nested symlinks/special files are refused; excluded caches are
pruned before inspection. Preserve selected fixtures, Gradle wrapper/JAR/version
catalog and relative source depth. The generated product contains no reference
back to the checkout; build and run commands work without the generator installed.

## Output and evidence

Output includes selected native source/tests/resources, shared fixtures,
README/SETUP/Makefile/.gitignore and `mobile-foundry.json`. Provenance records
generator version, selected profile/platforms/backend, product inputs, source
revision/dirty state, source content/profile digests and creation time; no absolute
source checkout or SDK path is embedded. Source module/component names are not
branding replacements. SDKs and downloaded dependency caches remain external
machine prerequisites.

CLI safety/copy tests cover all three combinations, invalid identities/selections,
destination preservation, symlinks, exclusions, dependency omissions, source
preservation and cleanup on failure. Starter readiness additionally requires
actual renamed output built/run outside the checkout, included capability tests
and a small app-owned service/query/mutation feature. Static inventory or toolchain
checks alone do not promote readiness. Go extraction has passed its independent
gate; generated connected native profiles remain deferred to their own acceptance.
