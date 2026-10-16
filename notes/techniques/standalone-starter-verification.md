# Standalone starter verification

Claim: a reference catalog is evidence for its reusable behavior; a generated,
renamed product needs its own build/runtime evidence before becoming a starter.

Origin: the user's request to announce new-product readiness, 2026-10-10. This is
a source investigation and acceptance plan, not an implemented generator.

## Evidence and reasoning

Inspection of [Bento's CLI guide](../../../bento/cli/README.md),
[entry point](../../../bento/cli/src/cli.ts),
[project construction](../../../bento/cli/src/project.ts) and
[source filter](../../../bento/cli/src/files.ts) shows explicit registration,
independent copying, metadata, configuration and exclusions. The constructor
refuses existing destinations and cleans up a destination it created if generation
fails. Its current registry accepts Node frontends and Node/Go backends; its mobile
profile is Expo. This turn did not execute or validate Bento's generator.

The native catalog has a different dependency shape. Swift's XcodeGen project
references sibling packages; Kotlin's Gradle app includes reusable core modules.
Tests and resources also have checkout-relative paths. Copying only app source
does not establish a complete product. Extraction must include the chosen module
closure and make all build/test/resource references portable inside the output.

## Useful example

Select a local Swift app with kernel, HTTP, services, query, UI and whichever data
capabilities its feature needs. Generate a new product into a temporary sibling
directory, change its bundle/display identity and follow its own setup guide.
Build, launch and create/read one local record. Include only the dependency closure
and fixtures that those selected checks actually require. Repeat independently for
Kotlin, and then for combined output. These are planned acceptance scenarios.

## Gotchas and use

Readiness is per profile: a local app can be usable without a backend, while a
connected profile must prove real account/auth/data behavior. Demo authority and
in-process observation do not establish server authorization or remote sync.
GPU compilation and catalog interaction do not establish physical-device budgets.
Excluding examples also must not remove resources or fixtures still used by a
selected library's checks.

Record the generated source's provenance and selected capabilities. A successful
catalog build or generator copy operation alone does not establish independent
startup. The practical gate is a small product feature built from the generated
seams, with no dependency on the original checkout.

Actual use: [starter readiness](../../docs/STARTER-READINESS.md) separates current
foundation candidates from verified standalone and connected starters. Future
handoffs should explicitly announce a level change with the generated profile
and evidence. No CLI command, template output or fresh product was produced in
this investigation. Documentation/whitespace checks verify the record's structure.

## Implemented follow-up

On 2026-10-10, the [local CLI slice](local-cli-and-independent-source-copies.md)
implemented registered Daily extraction, copy safety and temporary native feature
probes. The earlier investigation above remains its origin record. Current
combination status and actual generated native runs are in
[starter readiness](../../docs/STARTER-READINESS.md).

Related: [organization](../../docs/ORGANIZATION.md),
[product projects](../../projects/README.md) and
[shared fixtures versus native evidence](shared-fixtures-and-native-adapters.md).
Next: which minimal product shell and optional capability set should the first
independent native starter include?
