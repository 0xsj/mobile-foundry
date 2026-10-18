# Private SMTP and the acceptance boundary

Claim: SMTP delivery needs bounded private transport ownership and an explicit
acceptance point; an uncertain response cannot safely become an automatic resend.

Origin/evidence: 2026-10-18 [adapter](../../internal/identity/src/infra/email.rs)
and [local protocol/TLS tests](../../internal/identity/src/infra/email/tests.rs).
Rust 1.86 uses Tokio 1.53.2, tokio-rustls 0.26.6, rustls 0.23.46 and
rustls-native-certs 0.8.5.
The cached crate APIs were inspected and compiled. Primary API references:
[TlsConnector](https://docs.rs/tokio-rustls/0.26.6/tokio_rustls/struct.TlsConnector.html)
and [native certificate loading](https://docs.rs/rustls-native-certs/0.8.5/rustls_native_certs/fn.load_native_certs.html).
This evidence is local; no real mail-provider delivery was tested.

The process reads a private JSON file once, bounds it to 16 KiB, and selects a
Mailer port. Passwords and generated AUTH/DATA buffers use redacted/zeroizing
ownership; this does not erase every library or operating-system copy. TLS uses
system roots, verifies the configured hostname and permits TLS 1.2/1.3. STARTTLS
upgrades the existing stream and repeats EHLO before authentication. There is no
certificate bypass or configurable insecure production transport.

For example, a valid DATA exchange ends with a 250 response after the message
terminator. The adapter treats that as acceptance and closes, without requiring
QUIT acknowledgment. If the server accepted DATA but the response was lost,
delivery remains unknown. Retrying inside the adapter could send a second code
message. Requesting another code explicitly is a separate product action that
replaces the previous challenge.

Four admitted sends, a five-second total deadline, bounded multiline replies
and owned socket drop limit resource use. Recipient responses accept SMTP's 25x
success family; tests return 251. AUTH PLAIN is optional, with credentials
restricted to local hosts in explicit plaintext development mode. Production
refuses that mode. The adapter implements this selected SMTP subset rather than
a general MIME/mail delivery service.

Verification: local tests generate an owned temporary CA/server certificate,
exercise implicit TLS and STARTTLS with hostname localhost, reject an IP-name
mismatch, reject production plaintext, and wait for DATA acknowledgment. The
repository [recovery runner](../../../../scripts/recovery_parity.py) captures mail
only on owned loopback listeners and checks actual Go/Rust workflows and private
logs. Run `make backend-recovery-parity-test`. Tests require OpenSSL for fixtures;
the runtime uses rustls, not that executable.

Gotchas: configured server trust is deployment policy. A successful local handshake
does not prove DNS, live provider credentials, deliverability or inbox placement.
Mail is sent after the database commit and cannot roll that commit back.
Related: [recovery flow](../modules/internal/identity/recovery.md),
[blocking KDF ownership](argon2-and-blocking-lifetime.md) and
[cancellation-safe joining](../language/owned-polling-and-cancellation-safe-join.md).
Next: how should durable mail intents express unknown acceptance without blindly
replaying a potentially accepted send?
