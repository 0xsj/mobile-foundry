# Demo projects and emulator persistence

A demo project with explicit emulator endpoints gives local Firebase SDK
testing, while graceful export/import preserves supported emulator data across
container replacement.

## Origin and evidence

Observed 2026-10-08 with Docker Compose 5.5.1 on Docker Desktop's Linux ARM64
engine. The image uses Node 24, Temurin Java 21, and Firebase CLI 15.32.1.
Its setup downloaded Firestore emulator 1.22.0, Storage runtime 1.1.3, and
Emulator UI 1.15.0. Java 21 follows the documented upcoming Firestore runtime
requirement.

The official [emulator configuration guide](https://firebase.google.com/docs/emulator-suite/install_and_configure)
documents configuration and export/import. The
[Auth connection guide](https://firebase.google.com/docs/emulator-suite/connect_auth)
explains demo project isolation and SDK routing. Source inspection and observed
HTTP requests establish the local behavior below.

## What and why

The CLI runs Auth, Firestore, and Storage under `demo-mobile-foundry`. The
`demo-` project has no corresponding provisioned cloud resources. A native app
must use that project ID and explicitly connect each SDK to its emulator.
Running a container does not change SDK configuration on the device.

The emulator services listen on all interfaces inside the container; Compose
publishes their ports on host loopback. Android Emulator uses `10.0.2.2` to
reach the host; iOS Simulator can use `127.0.0.1`. Physical devices need a
reachable host address and an explicit bind override.

The image downloads emulator binaries during its build. Startup then runs the
installed suite, imports an existing export when present, and requests export
on graceful exit. A named volume keeps that export independent of container
lifetime. Compose allows 60 seconds for shutdown.

## Example and observed checks

From the Firebase profile directory:

```sh
make up
make down
make up
```

The image built and Compose reported the service healthy. The hub advertised
all three emulators, the UI responded successfully, and Auth accepted a local
email/password signup. Unauthenticated Firestore reads and Storage listings
returned 403 under the initial rules.

After `make down` and another start, signing in with that account succeeded.
This observed Auth export/import across container replacement. The temporary
account was deleted, and the profile was stopped gracefully afterward.

## Gotchas and limits

Firestore and Storage currently deny all client access. Rules should become
account-scoped with the first real feature. A healthy hub establishes process
discovery; it does not prove every service operation or native SDK behavior.

The restart experiment covered Auth persistence. It did not exercise Firestore
documents, Storage objects, native cache behavior, or crash recovery. Forced
termination can skip export. The emulators do not reproduce every production
limit, index behavior, or IAM rule; see the official
[Firestore](https://firebase.google.com/docs/emulator-suite/connect_firestore)
and [Storage](https://firebase.google.com/docs/emulator-suite/connect_storage)
connection guides for service differences.

## Used in and related

Use this profile for local session and data workflow exploration. Define
application ownership and repository contracts when connecting the native SDKs.
The [reading order](../README.md) links setup and the next learning questions.
