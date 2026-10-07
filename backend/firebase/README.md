# Local Firebase profile

Compose packages Firebase's Local Emulator Suite for Auth, Firestore, Storage,
and the Emulator UI. Firebase's hosted service is a managed platform; this
folder runs its local emulators using `demo-mobile-foundry`, with no cloud
project or Firebase login required.

## Start and stop

Docker and Docker Compose are prerequisites. Node.js, Java 21, Firebase CLI
15.32.1, and emulator binaries are installed in the development image.

```sh
cd /Users/sj/Desktop/dev/builds/mobile-foundry/backend/firebase
make up
make status
```

The first build downloads the tools and binaries. Data is exported to a named
volume on graceful shutdown and imported at the next start:

```sh
make down
```

A forced kill cannot guarantee export of the latest in-memory changes. The
60-second stop grace period allows the CLI to perform an ordinary export.

## Local endpoints

| Service | Host endpoint |
| --- | --- |
| Emulator UI | `http://localhost:4000` |
| Emulator Hub | `http://localhost:4400` |
| Auth | `localhost:9099` |
| Firestore | `localhost:8080` |
| Storage | `localhost:9199` |

Ports bind to loopback on the host, while emulators listen on all interfaces
inside their container. The Android emulator uses `10.0.2.2` to reach the host;
an iOS simulator uses `127.0.0.1`. A physical device needs a reachable host and
an explicit `FIREBASE_BIND_ADDRESS` override.

Native clients must use `demo-mobile-foundry` consistently and explicitly
connect every relevant SDK to its emulator. The provider directories contain
no native Firebase SDK integration yet.

## Rules and future behavior

Firestore and Storage rules initially deny client data access. Define the
account-scoped rules, indexes, and example data with the first connected slice.
Auth account creation can be exercised independently in the emulator.

Functions, push delivery, billing behavior, and provider production guarantees
are outside this initial local profile. Emulator observations must remain
distinct from verified hosted behavior.

[Learning notes](notes/README.md) record setup findings. Official references:
[Emulator configuration](https://firebase.google.com/docs/emulator-suite/install_and_configure),
[Auth connection](https://firebase.google.com/docs/emulator-suite/connect_auth),
[Firestore connection](https://firebase.google.com/docs/emulator-suite/connect_firestore), and
[Storage connection](https://firebase.google.com/docs/emulator-suite/connect_storage).
