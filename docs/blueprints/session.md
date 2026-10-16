# Session foundation construction

Authority: [session behavior](../../contracts/behavior/session.md),
[architecture](../../ARCHITECTURE.md), [organization](../ORGANIZATION.md).
The demo lifecycle and [Go authority](../../contracts/behavior/go-native-session.md)
are separate implementations of the same owned ports.

## Files and responsibilities

| Swift / Kotlin file | Responsibility and important symbols |
| --- | --- |
| FoundrySession/SessionModels.swift / core/session/SessionModels.kt | Principal, Account, SessionScope, SignIn admission, secret grant/credential admission, public snapshot and lease |
| SessionPorts.swift / SessionPorts.kt | SessionAuthority and CredentialVault async result boundaries; owned memory vault |
| DemoSessionAuthority.swift / DemoSessionAuthority.kt | Explicit local fixture accounts, short access lifetime, rotation and instance-local revocation |
| GoSessionAuthority.swift / GoSessionAuthority.kt | Deployment-bound issuer; sign-in/account composition; rotation checkpoint; guarded selection and revocation |
| GoSessionWire.swift / GoSessionWire.kt | Strict Go wire admission and secret-safe projection |
| HTTPAccountDirectory.swift / HTTPAccountDirectory.kt | Owner page through an injected lease-bound HTTP client |
| SessionController.swift / SessionController.kt | Restore/sign-in/renew/sign-out admission, scope epochs, result generations, save/clear ordering and public projection |
| KeychainCredentialVault.swift / KeystoreCredentialVault.kt | Native device credential storage and typed platform refusals |
| app Composition/SessionComposition + Session gallery | App-selected authority/vault, transient form and demo controls; route-owned lifecycle |
| contracts/fixtures/session/admission.json | Shared command/credential/expiry refusals |
| native Session tests and app gallery checks | Actual admission, races, storage and consumer behavior |

Create FoundrySession/core:session only for these implemented responsibilities.
Both depend on Kernel; the Kotlin module is Android-native for Keystore but
its controller/domain tests run on the JVM. Framework/API objects do not escape
vault adapters. Go HTTP is implemented; managed SDK authorities can satisfy the
same ports without changing the controller.

## Construction order

1. Admit domain values and secrets; keep secrets out of snapshots/descriptions.
2. Define authority and serialized vault interfaces with an owned memory adapter.
3. Implement controller admission before adding native persistence. Mark an
   operation busy before scheduling, close scope on renewal/replacement, verify
   saved issuer/scope and persist rotated credentials before activation.
4. Specify sign-out's cancellation barrier before clearing so a late native save
   cannot follow the clear. Distinguish local clearing from authority revocation.
5. Implement Keychain and Keystore adapters with isolated test namespaces. Preserve
   malformed bytes, refuse unknown versions and verify native replacement/clear.
6. Compose a labeled demo gallery using existing form/card/action APIs. Keep
   Notes guest storage independent and controller shutdown separate from sign-out.
7. Run shared fixtures, race tests, actual native vault checks, consumer builds
   and native gallery interactions. Record limits and learning step 42.

Go now supplies real authentication and device-owned account selection, with
rotation checkpoints before account lookup. Remote Notes and independently
generated connected products still require their own acceptance.
