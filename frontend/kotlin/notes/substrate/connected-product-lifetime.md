# Connected product lifetime and Debug networking

Claim: retaining the connection owner through Activity recreation prevents duplicate
restoration, while process restoration still requires the native credential vault.

Origin/evidence: CLI 0.12.0's product overlay uses an Activity-scoped AndroidViewModel,
and its independent driver separates recreation from a force-stop between two
instrumentation runs. Actual results are recorded in
[construction](../../../../docs/blueprints/go-connected-starter.md).

## What and why

The factory receives Application only to construct KeystoreCredentialVault and
read Debug metadata; it never retains an Activity. ConnectedSessionModel remains
a plain injectable class. Screens collect lifecycle-aware flows; the root forwards
foreground/inactive state. A retained ViewModel keeps one SessionController and
rotation checkpoint through configuration changes. New processes create a new
controller and restore through the vault.

The [official ViewModel guide](https://developer.android.com/topic/libraries/architecture/viewmodel)
documents configuration-change retention and owner cleanup. That retention does
not establish process durability. The separate native vault supplies the refresh
credential; app-owned pending commands remain memory-only.

## Example

```kotlin
// Excerpt: the product's Activity owner, with setup omitted.
override fun onCleared() { connection?.close() }
```

Read [the owner](../../../../templates/overlays/go-connected/kotlin/ConnectedSessionModel.kt)
with [the lifecycle consumer](../../../../templates/overlays/go-connected/kotlin/ProductRoot.kt).
Closing cancels state binding, detaches feature jobs and shuts down the controller.

## Test process ownership

Observed 2026-10-17: AGP 9.0.1's UTP log explicitly selected
`uninstall_after_test: true` for both app and test APKs. A second Gradle connected
invocation therefore started with erased private data; that failure could not
measure credential restoration. The driver now assembles/installs once, invokes
AndroidJUnitRunner twice through ADB and force-stops only the installed app
between stages. It requires one successful non-skipped test status per run and
retains each instrumentation report. This keeps the actual vault file and app
Keystore identity rather than manufacturing a credential for the second process.

## Gotchas and configuration

Internet permission belongs in the selected app manifest. Cleartext remains false
in main; the Debug manifest selects a network policy admitting only loopback domains.
Release also disables loopback admission at the session authority. This follows
[Android's network configuration mechanism](https://developer.android.com/privacy-and-security/security-config),
without overriding certificate validation. Dependencies are Lifecycle 2.10.0,
Compose BOM 2026.03.01 and AGP 9.0.1 in the copied version catalog; execution versions
and limits belong beside the independent evidence, not inferred from these pins.

Study next: which data must survive owner replacement, and why does an encrypted
refresh credential not recover a process-local pending Notes command?

The initial connected shell also selects a single-task main Activity, so repeated
launcher admission reuses that entry. Read [Android's launch-mode contract](https://developer.android.com/guide/topics/manifest/activity-element#lmode).
The product's native metadata test checks the selected mode. Multiwindow/multiple
independent session owners need deliberate credential coordination rather than
separate controllers loading the same rotating refresh record.
