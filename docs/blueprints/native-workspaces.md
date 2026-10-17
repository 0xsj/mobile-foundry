# Native workspace management

Claim: a connected starter can manage owner accounts through a replaceable
command/read boundary without changing the current data scope implicitly.

CLI 0.18.0 selects the Swift/Kotlin Daily + go-connected forms and AccountManagement
ports. Go already owns create/read/rename and their atomic version/ownership rules.
Read [the behavior contract](../../contracts/behavior/native-workspaces.md) and
[why a read is not a receipt](../../notes/patterns/current-state-is-not-a-command-receipt.md).

## Use and source order

Runnable from a directory containing the installed CLI:

```sh
mobile-foundry init canopy --platform both --with go-connected --backend go \
  --app-id com.example.canopy --go-module github.com/your-org/canopy/backend
cd canopy
make go-dev
```

Register/sign in from Account, then open Manage workspaces. Refresh loads the
selected account's current name/version. Rename uses that observation; conflicts
require another read and review. Create leaves the current selection alone.
Close management and choose Use <name> explicitly. After a missing create reply,
Review workspaces, inspect pages and deliberately acknowledge duplicate risk.
The warning is process-local; after a restart, review existing workspaces before
submitting another possibly duplicate create. Generated SETUP.md carries this workflow.

Read reusable [Swift](../../frontend/swift/packages/FoundrySession/Sources/FoundrySession/AccountManagement.swift)
and [Kotlin](../../frontend/kotlin/project/core/session/src/main/kotlin/dev/mobilefoundry/session/AccountManagement.kt)
values/ports first. Then read app-owned
[Swift state](../../templates/overlays/go-connected/swift/WorkspaceModel.swift),
[Kotlin state](../../templates/overlays/go-connected/kotlin/WorkspaceModel.kt),
[Swift form](../../templates/overlays/go-connected/swift/WorkspaceView.swift) and
[Kotlin form](../../templates/overlays/go-connected/kotlin/WorkspaceScreen.kt).
The [selected inventory](../../templates/profiles/native-daily-go-connected.json)
copies those owners into ProductApp/product, independently of catalog examples.

## Verification and limits

Boundary checks use the existing shared Account name vectors plus scalar/Unicode
edge cases, exact strong preconditions, no replay keys, canceled dispatch and
malformed/foreign/mismatched observations. Native Go port checks create, rename,
refuse stale versions and foreign owners, explicitly select and reject old leases.
Six state cases per platform exercise duplicate-submit prevention, fresh-read
gates, retained create uncertainty, explicit review/acknowledgement and late results.

The generated acceptance driver owns a loopback Go API, fault proxy and test
identities. Actual native screens create/select, retain Notes under their account,
rename, face a real competing rename, read back a committed rename with a lost
reply and review a committed create with a lost reply. Proxy counters require one
attempt per named operation and no implicit replay. Selected-account secure
restore and data isolation are checked across process termination.

Host execution, 2026-10-17: untouched CLI 0.18.0 output at
`/private/tmp/mobile-workspaces-final-2e2xbabf/workspaces-ready`, identity
`com.example.workspacesready`, module `github.com/example/workspaces-ready/backend`
and Debug base `http://127.0.0.1:18376/v1`, passes 11 Swift Session / 3 Services /
7 Data host cases, plus 11 Kotlin Session / 3 Services / 27 product-state cases.
There are zero failures/skips in those selected groups. Reports are in the
output's Gradle test-results directories and
`/private/tmp/mobile-workspaces-ready-final.log`. The 51 selected source/overlay/
fixture hashes match both the checkout and unchanged output after execution.

Full independent acceptance exits 0. On iPhone 17 Pro/iOS 26.2, **36 checks pass
with zero failures/skips**: 31 state/presentation and five UI cases. Pixel 9 Pro /
Android 15 passes **all ten install-once UI stages**, zero failures/skips, plus
seven native SQLite instrumentation cases. These exercise secure/pending process
restore, registration, identity lifecycle, live reads, scoped cache recovery and
workspace management together. The iOS result is
`.cache/connected-ios/Results/connected-1792269824439184000.xcresult`; Android's
per-stage reports remain under `.cache/connected-android`. CLI's 32 checks,
starter inventory and learning-note validation pass separately.

Both owned proxies verify one attempt for each named Account operation, strong
rename preconditions, one commit for the two lost acknowledgements, a real stale
rename refusal and no implicit mutation replay. Existing pending Notes identity/
input/receipt and registration uncertainty checks also pass. Native UI shows
current workspace names; wire versions stay behind the service/presentation seam.
Host suites and screen fixtures use separate owned API processes to keep their
rate budgets independent, preserving server limits and one-attempt clients.
The first iteration's test input/toolbar findings are recorded in
[the Swift form note](../../frontend/swift/notes/substrate/swiftui-form-focus-and-submit.md).
An additional owner regression ensures a second uncertain create requires another
review, and unconfirmed rename metadata yields to fresh directory readback.

The independently tested Daily + Go starters remain ready for new connected
Notes-shaped products through CLI 0.18.0. Previous 0.17.0 registration evidence
remains documented separately.

No invitation/membership/role/deletion flow, Account receipt journal, offline
startup or cloud deployment is claimed. Other shells/providers need their own
registered source closure and independent acceptance. Next: which additional
product capability should be selected, Files/media or an account inbox?
