# Generated-product feature probe

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
