# Account conformance fixtures

Observed 2026-10-18 against actual Go and Rust memory APIs. Names reuse the
[existing corpus](../account/names.json), executed through each domain policy.
[HTTP cases](http.json) establish forty expected wire outcomes. The owned
[runner](../../../scripts/account_parity.py) creates fresh processes for core,
wire and default-deny phases and checks each language against expected behavior.

Each case has name/path/status, optional method (default POST), raw wire JSON,
headers and auth (default true). `{id}` is the phase's bootstrapped account ID.
code/allow/etag assert the problem/header value; account_name asserts the created
resource's canonical name, owner, schema, ETag, timestamps and Location. Name
errors additionally assert exact public validation fields. HEAD asserts no body.

Core acceptance separately exercises eight concurrent personal bootstraps,
ordinary-account separation, rename/readback, two competing CAS writes, foreign
owner refusal, cursor traversal/tampering, renewed access and revoked access.
The runner checks no credential/private sentinel appears in logs, clean SIGTERM
exit and listener closure. Its processes inherit no host provider credentials.

Run `make backend-account-parity-test` from the repository root. This is the
[bounded memory subset](../../behavior/rust-account.md); it does not establish
Audit delivery, database durability, arbitrary parser parity or native/CLI readiness.
