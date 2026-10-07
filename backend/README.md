# Backend profiles

The `go` and `managed` directories reserve candidate profiles; implementations
and providers are not selected yet.

An owned API can follow Bento's module conventions and expose contracts from
the root `contracts/http` directory. A managed profile should contain its
configuration, migrations or policies, and application adapter contracts.

Select the first profile against a concrete session and data workflow. Define
idempotency, versions, and synchronization behavior before connecting durable
client writes. Provider-specific client adapters belong to the relevant native
implementation; server configuration belongs here.

When a profile is implemented, keep its learning notebook beside it under
`notes`, using the same modules, language, patterns, concepts, techniques, and
substrate categories as the native implementations. Follow
[the learning workflow](../docs/NOTES.md) and link its reading order from
[the shared index](../notes/README.md).
