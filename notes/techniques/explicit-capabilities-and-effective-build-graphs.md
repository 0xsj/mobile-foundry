# Explicit capabilities and effective build graphs

Claim: a starter capability is a complete, verified source/build/test selection;
its name alone does not establish that arbitrary combinations will work.

## Origin and evidence

Added 2026-10-10 while extracting local data for mobile-foundry 0.2.0. Source
inspection showed that full Data depends on Session/account adapters and full
Services depends on HTTP. The local exemplar only needs guest CRUD/observation.
The CLI's 19 checks pass for base and local-data platform combinations, selection
refusals, effective manifests, fixtures, required source, safe copying and cleanup.
Native acceptance evidence is recorded in
[readiness](../../docs/STARTER-READINESS.md) and the native product walkthroughs.
All three generated selections build outside the checkout. Combined and
Swift-only outputs pass eight native cases each; combined and Kotlin-only pass
26 unit/eleven device cases each. Selected Swift packages pass 30. Actual process
termination/relaunch retains records on both platforms; captures were inspected.
Full reference Services pass eleven regressions each after the source split.

## What and why

An optional capability is not just a folder. It includes the domain ports,
selected adapter, build dependencies, resources, test fixtures, product wiring
and instructions needed to use it. Checking the original full package manifests
would incorrectly require HTTP/Session for a local-only output. Checking only
the source list would miss the opposite error: an overlaid build that references
an omitted dependency.

This slice registers a complete Daily/UI/local-data inventory. The validator
views build files through the overlay map before checking graph reachability.
Module source can be selected file by file; required capability files and local
fixtures must still be covered. Duplicate overlay destinations are refused.
Generation snapshots selected bytes, applies the same overlays and fingerprints
the complete inventory. There is no implicit merging or dependency guessing.

The local module names remain stable. Domain ports and SQLite/notification code
stay single-source in their native modules. The HTTP Notes implementation moves
to a separate file, preserving its API while allowing local domain source to be
copied without transport imports. Product state/presentation is an editable
exemplar, separately owned by the product overlays. Full reference modules
retain their existing dependencies and regression checks.

## Useful example

From an existing directory outside the checkout:

```sh
mobile-foundry init field-notes --platform both --with local-data \
  --app-id com.example.fieldnotes
cd field-notes
make help
```

Library injects one ObservableNotesRepository into a CRUD owner and an independent
reader. SQLite is chosen by composition. The writer's success receipt describes
the admitted commit; the reader's count describes current state. A failed reload
does not invalidate a receipt, and a paused reader does not keep an audit log.
Replacing the composition factory with Memory retains consumer APIs while
deliberately losing durable storage.

## Gotchas

- A Swift import needs an explicit linked package product; the local app overlay
  declares UI, Query, Kernel, Services and Data for both app and hosted tests.
- Kotlin snapshot properties such as QueryState.value/failure are extensions;
  a screen needs their imports even when Query is already on the classpath.
- A native Activity recreation is not process death. Separate termination and
  relaunch evidence is needed before claiming saved records survive a process.
- Existing generated apps remain independent snapshots. Updating the CLI does
  not modify them, and output directories are never overwritten.
- Local-only manifests intentionally exclude account/HTTP implementations. If
  a future capability needs them, it needs an explicit accepted combined graph.
- These checks parse known local build syntax. They are not a Swift/Gradle
  interpreter or a guarantee about arbitrary third-party templates.

## Used in

Read [registration](../../mobile-foundry.starters.json),
[selection](../../cli/mobile_foundry/capabilities.py),
[effective validation](../../cli/mobile_foundry/profiles.py),
[the inventory](../../templates/profiles/native-daily-local-data.json),
[product overlays](../../templates/overlays/local-data) and
[capability checks](../../cli/tests/test_capabilities.py).

## Related and next questions

Read [source copies](local-cli-and-independent-source-copies.md),
[commit and observation lifetime](../patterns/local-commits-and-observation-lifetime.md),
[extraction closure](../patterns/product-entry-and-extraction-closure.md),
[behavior](../../contracts/behavior/local-data-starter.md) and
[construction](../../docs/blueprints/local-data-starter.md).
Next: which shared files would collide when adding a second capability, and
which combination-specific tests establish that the assembled product works?
