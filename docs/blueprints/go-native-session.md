# Go native session construction and setup

The Swift/Kotlin adapters implement [session/account integration](../../contracts/behavior/go-native-session.md)
behind authority/directory ports. The catalog is the current runnable consumer;
generated connected-product profiles remain unregistered.

## Run the backend

From `mobile-foundry/backend/go`:

```sh
AUTH_MODE=identity make dev
```

This uses memory and `127.0.0.1:8080`. For persistence, use the existing local
PostgreSQL setup, apply `make migrate`, then run `AUTH_MODE=identity STORAGE=postgres
make dev`. [Go setup](../../backend/go/README.md) documents environment selection
and mutually exclusive development credentials.

Register a local account in another terminal:

```sh
curl -i http://127.0.0.1:8080/v1/identity/register \
  -H 'Content-Type: application/json' \
  -d '{"login":"alex","display_name":"Alex","password":"a long local passphrase"}'
```

Use that login/password in **Go identity and accounts**, reached through Studio →
Open catalog on iOS and the corresponding Android catalog. Set API base to
`http://127.0.0.1:8080/v1`, enable loopback HTTP, choose Device or Memory, and
Connect. First sign-in resolves the personal account. Additional owner accounts
created through `POST /v1/accounts` appear in the owned-account list.

Android's loopback is the device. For a local emulator/USB workflow:

```sh
adb reverse tcp:8080 tcp:8080
```

Remove that mapping when finished with `adb reverse --remove tcp:8080`. A physical
iOS device needs a reachable HTTPS API; its loopback is not the Mac. The library
does not admit arbitrary LAN cleartext or disable certificate validation.

## Source owners

| Owner | Responsibility |
| --- | --- |
| SessionAuthority / CredentialVault | Swappable provider and owned durable credential boundary |
| GoSessionAuthority | Endpoint identity, owned auth HTTP, account composition and checkpoint ordering |
| GoSessionWire | Canonical response admission, UTC/token/version/owner rules |
| AccountSelectingAuthority / SessionController | Guarded selected account, persistence before activation and lease replacement |
| AccountDirectory / HTTPAccountDirectory | Bounded discovery through an injected lease-bound HTTP client |
| Native catalog composition/model | Endpoint vault namespace, native transport, transient drafts and directory lifetime |

GoSessionAuthority's direct renewal overload leaves persistence to its caller.
Features should use SessionController, which supplies the rotation checkpoint.
Read [the learning pattern](../../notes/patterns/deployment-authorities-and-rotation-checkpoints.md)
for why an Account outage must not discard a known rotated refresh token.

## Verify

From the repository root:

```sh
make session-test
make go-native-test
make go-native-device-test
```

The host integration target owns a temporary memory Go API; the device target
also requires Xcode's simulator and one connected Android test device. It owns
temporary fixture users, its ADB mapping and test endpoint environment. iOS tests
observe Keychain restoration after process relaunch; Android currently observes
Keystore restoration after route removal, not process-kill restoration.

[Remote Notes and receipts](remote-notes.md) now implement connected data behind
the current account lease. Offline sync, production identity features and
independent backend/native extraction remain separate milestones.
