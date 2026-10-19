# Verify Cargo closure before promoting a source-copy starter

Claim: a Rust starter is independent only when package identity, local dependencies,
embedded resources and test execution survive extraction together.

Origin/evidence: 2026-10-18 CLI 0.19.0 and a renamed generated Rust 1.86 workspace,
with memory, disposable PostgreSQL and fresh Docker recovery acceptance.

What and why: a Cargo workspace is more than its production .rs files. Local path
dependencies need selected manifests, and tests can embed fixtures outside a crate
through include_str/include_bytes or path attributes. Preserve the admitted source
depth and include every required input. Validate effective overlaid TOML rather
than trusting the repository's larger graph. Explicitly refuse unsupported computed
inputs; a compiler check remains necessary after static closure validation.

Example (Conceptual): rename the API package while retaining an explicitly named
server binary and reusable crate identities. Update only its local lock entry,
preserve registry checksums/versions and keep lock ordering stable. Reject names
that collide with workspace or locked dependencies so package selection does not
become ambiguous. Do not replace protocol/schema identifiers as product branding.

Create an owned output outside the checkout. Build/check/test it without sharing
the source target directory, then exercise its actual Make runner. Use only its
copied drivers for fresh database, process-kill and image/backup tests. A prior
source build or syntax-only inventory cannot establish those behaviors. Only
then promote the registration, retaining evidence of the earlier candidate output.

Gotchas: generation must not need Cargo or install SDKs. Doctor version probes can
otherwise activate Rustup's missing-toolchain installation; the
[Rustup environment contract](https://rust-lang.github.io/rustup/environment-variables.html)
documents disabling that with RUSTUP_AUTO_INSTALL=0. That proves probe policy,
not compiler availability or application correctness. Exclude target/.cargo/private
state and never use an application's configured DSN for integration acceptance.
Co-locating native source with an API does not connect its session/data owners.

Used in: [the CLI walkthrough](../modules/cli/README.md) and
[Rust construction/evidence](../../docs/blueprints/rust-starter.md).
Related: [independent copies](local-cli-and-independent-source-copies.md).
Next: which native composition/acceptance is needed before selecting Rust remotely?
