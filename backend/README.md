# Backend profiles

Keep each backend profile independent so an idea can use the capabilities it
needs without starting every service in the repository.

| Profile | Current scope | Entry point |
| --- | --- | --- |
| Go | Memory/PostgreSQL Identity/Account/Audit/Notes, native HTTP seams and verified standalone CLI extraction | [Go setup](go/README.md), [starter generation](../docs/blueprints/go-starter.md) |
| Rust | Runtime/Health, memory/PostgreSQL Identity recovery/Account/Audit/Notes/Files/Notifications/installations, live Notes hints, optional APNs/FCM previews and retained jobs, private SMTP/objects and shared Go conformance; CLI selection planned | [Rust setup](rust/README.md), [parity inventory](rust/PARITY.md) |
| Supabase | Pinned self-hosted stack with PostgreSQL, Auth, Storage, Realtime, functions, Studio, and local mail capture | [Supabase setup](supabase/README.md) |
| Firebase | Auth, Firestore, and Storage emulators with UI and graceful export/import | [Firebase setup](firebase/README.md) |

The Go profile follows Bento's module conventions and will expose contracts
from the root `contracts/http` directory. Supabase and Firebase own their
configuration, migrations or policies, and development tooling here.

Select the first profile against a concrete session and data workflow. Define
idempotency, versions, and synchronization behavior before connecting durable
client writes. Provider-specific client adapters belong to the relevant native
implementation; server configuration belongs here.

Every profile has a `notes` notebook with modules, language, patterns,
concepts, techniques, and substrate categories, matching the native notebooks.
The provider notebooks explain the local stack and its verification; Go notes
explain runtime ownership, application ports and shared failure/HTTP packages. Follow
[the learning workflow](../docs/NOTES.md) and link its reading order from
[the shared index](../notes/README.md).
