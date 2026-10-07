# Supabase learning notes

This notebook explains the local self-hosted profile and later application
integration. Follow [the shared workflow](../../../docs/NOTES.md); modules,
language, patterns, concepts, techniques, and substrate keep Bento's meanings.

## Reading order

1. [Profile setup](../README.md) for commands, credentials, and native endpoints.
2. [Compose snapshot and local ownership](substrate/local-compose-stack.md)
   for the pinned toolchain, data boundaries, and verification limits.
3. [Upstream provenance](../UPSTREAM.md) before updating the stack.

## Current coverage

The first slice records configuration and local infrastructure. Compose
startup and HTTP smoke checks passed, and credential setup was checked for
private file permissions and repeat-run retention. Native SDKs,
application schemas, account ownership, row-level policies, and sync behavior
are future slices; they are not provided by this notebook.

## Next questions

- What account scope should the first feature's rows and storage objects carry?
- Which behavior is owned by a native repository, and which comes from the SDK?
- Which idempotency and version checks must be supplied by our own schema?
