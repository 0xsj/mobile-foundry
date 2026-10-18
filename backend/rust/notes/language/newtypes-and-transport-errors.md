# Newtypes and transport-owned errors

Claim: private Rust wrapper fields make admitted IDs/versions unforgeable through
safe public APIs while keeping HTTP-specific failure policy out of domain values.

Origin/evidence: 2026-10-18 implementation and executed identifier/header corpora.
Primary reference: [Rust newtype idiom](https://doc.rust-lang.org/rust-by-example/generics/new_types.html).

`Id(String)` and `Version(u64)` are distinct types with private inner fields.
Their constructors validate once; callers can read the admitted representation
without being able to replace it with unchecked input. An ID checks canonical
lowercase/nonzero UUID form and intentionally accepts versions other than v4.
Generated IDs separately set v4/variant bits. Version is an HTTP-safe integer
bounded to 2^53-1; this bound does not choose a database/domain counter type.

```rust
// Excerpt: private state plus a validating constructor protects the invariant.
pub struct Version(u64);
impl Version {
    pub fn value(self) -> u64 { self.0 }
}
```

See [the full constructor/parser](../../pkg/httpx/src/admission.rs). `self` moves
the wrapper; Copy is appropriate for this small immutable integer. `Id` instead
owns its String and supports cheap explicit cloning at caller boundaries.

AdmissionError owns both a Failure and a status override behind private fields.
Only HTTP admission constructs 413/415/428. A business Failure with an identically
spelled code still uses its Kind mapping, so string spelling is not transport
authority. Implementing From<Failure> lets `?` carry a request deadline through
the helper without erasing its timeout kind.

The JSON helper's `T: DeserializeOwned` means the returned DTO does not borrow
the temporary request buffer. Option fields deliberately express absent/null
input; requiredness and semantic validation still belong to the endpoint/domain.
`FnOnce` on the entropy helper permits one owned fill attempt without requiring
a retryable callback. The production adapter uses a fallible OS source.

Gotchas: a private field does not establish authentication, durable replay or CAS.
Do not expose an unchecked constructor or derive public deserialization that
bypasses validation. The business Clock returns SystemTime; it is not monotonic
and must not replace HTTP elapsed/deadline clocks. Related:
[Result/async ports](results-and-owned-async-ports.md),
[kernel use](../modules/pkg/kernel/README.md) and
[HTTP use](../modules/pkg/httpx/README.md). Next: which domain timestamps should
be captured once per command rather than requested repeatedly during retries?
