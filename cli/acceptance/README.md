# Generated-product feature probe

The copied [connected driver](../../scripts/connected_acceptance.py) selects the
actual Go/Rust API from generated product metadata. Rust-connected reuses the
native ports and scenarios, with its own locked package build and no Go backend.
Read [Rust connected evidence](../../docs/blueprints/rust-connected-starter.md).

CLI 0.26.0 expands the combined media workflow with current file destinations.
Actual screens open a notice, explicitly download verified bytes without marking
read, then open a target deleted by an independent authorized client. The proxy
requires exactly two metadata reads, one success and one real backend 404, while
retaining the existing explicit read/preference mutation guards. Four added app
owner cases cover scope/lifetime and adverse responses on each native platform.
Read [the exact generated matrix](../../docs/blueprints/connected-media.md#cli-0260-notice-destination-verification--2026-10-19).

Optional inbox selection additionally owns real upload-completion fixtures and
committed lost-read/settings replies, with exact attempt counts across native
restoration. Read [inbox acceptance](../../docs/blueprints/connected-inbox.md).
CONNECTED_SCENARIO=inbox selects that added workflow; full includes it alongside
the established connected checks when the capability is selected.

The Files workflow also commits an owned competing delete before forwarding the
product's original command, so its 404 comes from the actual selected backend.
Native screens exercise Keep, then Forget local recovery and authoritative
readback. The proxy asserts one original attempt and one competing commit;
review/forget must never replay or replace the command. Existing completion-loss
and original-delete cleanup retries remain in the same workflow. Read
[Files execution evidence](../../docs/blueprints/connected-files.md).

[Rust verification](rust/verify.py) checks an already-generated Rust output's
package/lock identity and build-input independence, then exercises its actual
rust-dev Make runner and default Identity through scoped APIs. Its own copied
rust-integration-test and rust-operations-test supply database/process-loss and
Docker/backup evidence separately. Read [Rust starter evidence](../../docs/blueprints/rust-starter.md).

[Go verification driver](go/verify.py) accepts an independently generated project,
checks module identity/absence of checkout paths, builds its own API and exercises
the generated root Make runner with real auth/account/Notes/CAS/receipt, Files
binary transfer, Notifications inbox/read/preferences and renewal/
sign-out flows. It owns a temporary binary, narrow go-run shim and process group;
no development API/database is contacted. Run it after Go generation, then use
the output's go-integration-test for disposable PostgreSQL acceptance. See
[Go starter evidence](../../docs/blueprints/go-starter.md).

These files verify that a generated Daily app can grow a first feature without
editing foundation libraries. They are not included in the extraction profile.
Generate a disposable product outside the source checkout, then copy the selected
platform's CounterFeature into its app source, replace TodayScreen with the
supplied screen and add the supplied native test to its existing test folder.

For Swift, edit the generated `project.yml`: declare local FoundryKernel and
FoundryQuery packages using `../../packages/FoundryKernel` and
`../../packages/FoundryQuery`, then add both package products to application and
test target dependencies alongside FoundryUI. Imported modules need explicit
link dependencies. Run the generated `make ios-test` to regenerate/build/run.

For Kotlin, copy CounterFeature into `dev/mobilefoundry/product`, TodayScreen
into its `screens` package and CounterFeatureTest into the product's androidTest
package. The UI module exports Query/Kernel APIs; declare direct dependencies on
`:core:query` and `:core:kernel` in the app when using their APIs directly. Keep
the explicit `dev.mobilefoundry.product.R` import in the screen; the installed
application ID may differ from the source resource namespace. Run the generated
`make android-build android-ui-test` with your configured JDK/SDK and a booted device.

The small synchronous memory service returns Kernel results. Its injected model
settles query/mutation snapshots; successful increments reread current content.
The Swift test checks service sharing and a distinct new provider. The Android
test taps the actual Increment action twice and observes content/confirmation.
These probes do not establish persistence, async cancellation or backend behavior.
See [starter readiness](../../docs/STARTER-READINESS.md) for recorded execution.

CLI 0.25.0 Files acceptance additionally terminates/force-stops with a withheld
completion, an original delete needing retry, and content refused before reaching
the backend. Two iOS workflows and six Android stages require the same original
command/specification/bytes without choosing the photo again. The proxy compares
request-upload JSON by original field values because process-random dictionary
order is not command input; content remains byte-exact. PendingFiles disk checks
exercise the full 10 MiB limit and fail-closed removal/schema/corruption paths.

The owned startup gate has a 30-second deadline and preserves pre-fixture logs
in .cache/connected-runtime. CONNECTED_SWIFT_DISABLE_SANDBOX=1 explicitly disables
only nested SwiftPM manifest sandboxing for host tests on constrained build hosts;
normal manifest sandboxing remains the default. Read the Files guide for actual
execution and limits, including the initial host/proxy failures.
