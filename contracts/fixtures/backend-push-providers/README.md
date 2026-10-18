# Provider and preview HTTP corpus

[Provider scope](../../behavior/rust-push-providers.md) is intentionally narrower
than full Go/Rust backend parity.

- [responses.json](responses.json): 32 APNs/FCM response cases with independent
  expected dispositions/public failure codes, fixed registration/current times
  and selected FCM project. Stale/ambiguous evidence must preserve the token.
- [http.json](http.json): 21 guarded manual-preview cases. Omitted values select
  POST, owner credentials, correct proof, strong revision 1 and valid preview JSON.
  Explicit null if_match means absent. $PREVIEW$/$PROOF$ are replaced by owned
  harness values. Successful cases require one send; refused cases require none.

Go and Rust execute these cases against their own expected values. The provider
tests also traverse local verified TLS with ephemeral test-only keys; production
endpoints/trust remain fixed. The runner tests compiled default/selected API
startup and shutdown without making any cloud send. PostgreSQL acceptance uses
separate owned Identity/Notifications databases and injected gateways at HTTP.

Run `make backend-push-providers-parity-test` and
`make backend-postgres-parity-test`. These checks establish no real provider-issued
token, live delivery, device animation/performance, native connection or generated
Rust starter readiness. Go production code and migration artifacts are unchanged;
the CLI explicitly excludes these repository-fixture test files from source copies.
