# Local project CLI construction

## Status and scope

The initial source-copy slice established Swift/Kotlin Daily products. Current
CLI 0.4.0 additionally registers Workspace/Browse, Daily local-data, and independently
verified [Go extraction](go-starter.md), including API-only output. Native/backend
co-location remains independent; generated native auth/data wiring follows next.
Python 3.11+ supplies generation without builds/network installation; reusable
component names remain stable.

## Exemplar workflow

From a terminal in an existing products directory, initialize a named Swift,
Kotlin or both-platform app, set its installed identity/label, follow its generated
guide and build/run using selected native tools without referencing the source
checkout. Existing output/source must survive all refused or failed attempts.

## Source documents and authority

[CLI behavior](../../contracts/behavior/project-cli.md),
[entry behavior](../../contracts/behavior/product-entry.md),
[readiness](../STARTER-READINESS.md), [inventory](../../templates/README.md),
repository learning-note rules and Bento's registered list/init/source-copy model.

## Nouns, states and invariants

Registry entries select inventories; a profile declares source and closure;
product inputs declare name/label/identity/team; a copy plan contains admitted
regular source files/overlays; owned output progresses from absent to created to
configured to complete, or is removed on failure. Existing output never becomes
owned. Toolchain observations are read-only evidence. Provenance identifies the
actual copied source without embedding its machine location.

## Workflow and side-effect ordering

Admit CLI/product inputs; load explicit registrations and selected inventory;
resolve admitted paths/closure; prune excluded files and preflight regular files;
refuse conflicting destination/source; exclusively create destination; copy;
apply overlays and product configuration; write portable guides/provenance;
report completion. Cleanup is restricted to the newly owned destination.
List/doctor/install operate separately from generation. Install exclusively
creates a link and refuses another existing command.

## Expected file tree and file specifications

| Required file | Symbols / owner / verification |
| --- | --- |
| bin/mobile-foundry | Executable repository-anchored Python entry; works from another cwd and through an install symlink. |
| mobile-foundry.starters.json | Explicit native profile registrations and planned Go backend, not directory autodiscovery. |
| cli/mobile_foundry/__init__.py | Generator version. |
| cli/mobile_foundry/profiles.py | ProfileError, discover/load/validate, safe relative paths, closure and source selection; static and negative inventory tests. |
| cli/mobile_foundry/generation.py | ProductInputs admission, source exclusion/preflight, copy planning, create_project, configure identity and provenance; copy/refusal/cleanup/source-preservation tests. |
| cli/mobile_foundry/documents.py | Portable generated README/SETUP/Makefile/gitignore; selected-platform commands and product-specific inputs; generated guide checks/native runs. |
| cli/mobile_foundry/doctor.py | Selected tool probes and JDK/SDK reporting, bounded subprocesses; mocked tool cases and actual toolchain report. |
| cli/mobile_foundry/cli.py | argparse command projection, explicit platform choice in noninteractive use, init/list/doctor and concise errors. |
| cli/tests/test_cli.py; test_generation.py | Real subprocess entry and filesystem mutation scenarios; isolated temporary output and unchanged source. |
| cli/acceptance/{swift,kotlin} | Small app-owned memory service/query/mutation probe and native tests, installed only into temporary generated verification apps. |
| scripts/install_cli.py | Deliberate command link installation, no overwrite of another command; tests/idempotent local installation. |
| scripts/check_starter_profile.py | Delegate to shared profile validation, retaining existing static checker command. |
| cli/README.md; Makefile; root setup/architecture/readiness/templates docs; shared/native notes | Usage, extension seam, actual independent evidence and pending Go/connected capabilities. |

Deferred: backend extraction/adapters, other product shells, additive data/session/
graphics profiles, published package distribution, durable settings and release
signing assets. Forbidden: placeholder backend copied as a working integration,
source mutation, replacing existing destinations, source-checkout links in output.

## Cross-file dependency map

Entry → CLI → profile validation / generation / doctor. Generation consumes the
profile copy plan and product inputs, then delegates guide rendering. Inventory
checker imports the same profile validator. Native source stays owned by native
apps/libraries; acceptance probes exercise it in output only.

## Transport and persistence mapping

Transport is terminal arguments/exit codes; persistence is exclusively owned
local output and its provenance. No server, credential port or backend store.

## Verification plan

Use real CLI invocations for three platform combinations and alternate cwd/source;
verify registered Go copies; reject planned/unknown backends, invalid inputs and existing/escaping/overlapping output,
symlinks, missing dependencies and drifted identity locations. Inject copy/config
faults and interruptions to verify cleanup and source preservation. Probe selected
tools without installing anything. Generate outside the checkout, audit file and
dependency paths, build/run renamed native apps, run included fixture/native checks
and the temporary memory service/query/mutation feature. Update readiness only
for actually verified combinations.

## Construction order

Contract and registry; shared validation; admitted copy/identity/provenance;
portable guides; CLI/doctor/install; safety tests; independent native verification;
learning notes and readiness handoff.

## Open decisions and unknowns

Installed command is mobile-foundry. Initial profile is native-daily with Swift,
Kotlin and both; backend none. Go now has real extraction/acceptance through the
backend inventory. Generated connected native provider profiles remain the next gate.
Native output verification can expose missing fixture/resource/tool assumptions;
record and fix them in the owning inventory/build rather than using checkout links.

## Deviation record

Completed 2026-10-10. The implementation follows the planned owner/file tree.
Doctor initially conflated the Gradle launcher with the JDK 17 compilation
toolchain; actual Studio JDK 21.0.6 exposed that assumption. It now reports both
selections separately. App IDs use the common letters/digits/dots subset of
native identifiers. The temporary feature probe requires explicit Swift package
link dependencies and Kotlin's explicit product.R import; these steps are in its
guide rather than added to the empty product's direct dependencies.

Three platform copies build/run independently; 13 CLI checks, selected library
checks and native entry/feature checks pass. A fresh Kotlin build also passes
without task-cache reuse. Exact counts/limits and generated identity evidence are
in readiness/native notes. No backend/other-shell/capability or release/device
performance claim was added. The installed command is a checkout symlink, while
products themselves contain independent source copies.

## Completion criteria

Follow-up 2026-10-10: optional local-data registration, effective overlay validation
and native product acceptance are specified by
[the local-data construction document](local-data-starter.md). The original
0.1.0 acceptance above remains the UI-only milestone.

CLI passes admission/copy/cleanup tests, works from another terminal directory,
produces portable renamed apps and documents selected combinations. Announce
standalone readiness only after independent native runs and capability/probe
checks; explicitly keep backend/connected profiles deferred.
