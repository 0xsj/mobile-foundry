# Push token collection construction

The native catalogs can collect a provider address, show its route without
revealing its token, and explicitly hand it to the existing session-bound Go
installation registry. Read [behavior](../../contracts/behavior/push-tokens.md),
[ownership reasoning](../../notes/patterns/provider-addresses-and-registration-intents.md),
then the Services/app module notes. This does not implement a push sender.

## Try the default catalog

Studio → Open catalog → System notifications → Provider token → Collect device
token. With the repository defaults this shows a recoverable missing-setup state;
it never prompts for OS permission or fabricates a token. Local notification
previews still work independently.

After provider setup, collection shows Token ready and a local revision. The
token value stays hidden. In Go identity and accounts, establish a real session
and refresh device registration, then use Register collected token. The existing
registry handles create/replacement/rebinding and uncertain acknowledgement.
Its sanitized read view cannot prove whether the candidate equals its private
saved token. Rotation only changes the candidate; another explicit write is needed.

## APNs setup

Use an Apple app identifier/provisioning profile with Push Notifications enabled.
The catalog does not alter the developer account or enable signing capabilities.
Copy [the unselected entitlement example](../../frontend/swift/apps/FoundryCatalog/Config/Push.entitlements.example)
to Config/Push.entitlements in the catalog app directory. Add
CODE_SIGN_ENTITLEMENTS: Config/Push.entitlements to the app settings in project.yml.
For a development-signed build the signing entitlement is development and
CATALOG_APNS_ENVIRONMENT is sandbox. For a distribution/TestFlight build the
actual signed entitlement is production and CATALOG_APNS_ENVIRONMENT must be
production. Configure these per build configuration/product; do not infer APNs
environment from a compiler debug flag or assume an exported signature retains
the input entitlement. Regenerate the project after changing its source settings.

Runnable from the repository root to regenerate after editing project.yml:

```sh
make ios-generate
```

Verify the exported app's signed aps-environment and bundle identifier against
the declared route before a live collection/send check. Collection waits up to
thirty seconds and can fail for signing/network/provider availability. A late
APNs callback updates only the app/process candidate. The source tests inject
that callback and do not contact APNs or prove signing is correct.

## FCM setup

Register the catalog's Android application ID in a Firebase project and provide
its public Android app configuration. No service-account key belongs in the app.
The catalog uses Gradle properties and standard generated resources, so it does
not need the Google services Gradle plugin or a committed google-services.json.
Place product-specific values in your private Gradle properties, or supply the
four properties for your local build. Example configuration (Conceptual; replace
all values with the registered Android app's configuration):

```properties
catalogFcmApplicationId=1:123456789:android:0123456789abcdef0123456789abcdef
catalogFcmProjectId=your-firebase-project
catalogFcmSenderId=123456789
catalogFcmApiKey=YOUR_PUBLIC_ANDROID_FIREBASE_API_KEY
```

Use a physical device with compatible Play services or a Google APIs/Play Store
emulator. The local Firebase Emulator Suite does not issue real FCM tokens.
Default values are empty; ordinary builds/tests need no Firebase project.
Configured SDK construction may occur at launch to support cold native service
startup. Auto token initialization remains disabled; Collect explicitly asks
the SDK. SDK internal persistence/retries remain provider behavior.

Firebase Messaging 26.0.0 is app-only. Its FID registration feature is disabled:
the existing backend route stores registration tokens and has no FID sender.
The deprecated token API is isolated as a deliberate compatibility adapter.
Treat a move to FID registration as a new end-to-end provider contract, not a
method rename. No remote message handler, cloud credential or sender is added.

## Checks

Runnable from the repository root with the standard simulator/emulator available
and no live provider configuration selected:

```sh
make http-test
make push-tokens-ui-test
make starter-check notes-check
```

Native selection covers the missing-setup catalog UI, APNs callbacks/hex/late
completion/cancellation/timeout with an injected registration closure, Android
request/rotation races, invalid targets, cancellation and merged SDK policy.
They are not live-cloud acceptance. Evidence below records only observed checks.

Primary sources: [Apple registration](https://developer.apple.com/documentation/usernotifications/registering-your-app-with-apns),
[Firebase Android client](https://firebase.google.com/docs/cloud-messaging/android/get-started),
[Messaging API](https://firebase.google.com/docs/reference/android/com/google/firebase/messaging/FirebaseMessaging)
and [SDK release notes](https://firebase.google.com/support/release-notes/android).

## Readiness

The adapters are a catalog foundation. Signed/cloud-issued token collection,
provider delivery, remote scoped opens, physical/older devices and independent
connected CLI selection remain unverified. Native/Go standalone starter readiness
is unchanged. A live-device check should collect, explicitly register, rotate or
recollect, reconcile, explicitly replace, and sign out while observing the
backend's existing retirement behavior. Sending requires its own provider setup.

## Observed evidence — 2026-10-17

Both catalogs build through make push-tokens-ui-test. The iPhone 17 Pro/iOS 26.2
result `.cache/ios/Logs/Test/Test-FoundryCatalog-2026.10.17_14-35-08-+0300.xcresult`
records four passed, zero failed/skipped: three native adapter cases and one
actual catalog missing-setup UI case. API36_Test/Android 16 records six passed,
zero failed/errors/skipped: five adapter/policy cases and one catalog UI case.
Android checks include callback rotation winning an older Task, invalid callback
invalidation preventing resurrection, cancellation cleanup and merged opt-in
policy. APNs checks inject registration/callback bytes and cover leading zeros,
variable token lengths, rotation, timeout/cancellation and late callback handling.

make http-test passes: Swift HTTP fourteen cases, Services thirty cases with
three real-Go environment-gated cases skipped; Kotlin HTTP thirteen cases and
Services twenty-nine with the same three real-Go checks skipped. The new shared
checks cover explicit collection, redaction, local revision deduplication,
closed-screen behavior, defect hooks and registry input preservation/readback
before a rotated address can be sent under a new intent. No backend source
changed, and the real-Go acceptance harness was not selected for this slice.

CLI twenty-eight tests and starter inventory checks pass. Documentation/link
checks pass. These local checks do not issue a provider token or verify a remote
notification. No cloud project, signing capability or backend sender was enabled;
standalone and connected readiness levels are unchanged.

The existing make system-notifications-ui-test regression also passes after
these additions: two iOS cases (including warm/cold local opens) and two Android
cases (owned PendingIntent/open/recreation/clear and admission), with no failures
or skips. The iOS result is
`.cache/ios/Logs/Test/Test-FoundryCatalog-2026.10.17_14-37-11-+0300.xcresult`.
Final notes-check reports 333 documents, 3,878 local links and zero failures;
git diff --check also passes. These remain simulator/emulator observations.
