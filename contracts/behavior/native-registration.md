# Native registration and first sign-in

CLI 0.17.0 selects IdentityRegistration in Daily + go-connected for Swift/Kotlin.
The Go registration endpoint already exists; this slice adds native ports,
admission, presentation and independent generated acceptance. Catalog and other
shells do not implicitly select registration UI.

## Service boundary

RegisterIdentity is an admitted, transient command with redacted descriptions.
IdentityRegistration returns RegisteredIdentity, not SessionGrant or Account.
The profile ID uses the shared Principal admission policy, so a Memory/provider
adapter can use an opaque admitted identifier. It has no credential-vault
dependency. The selected HTTP adapter additionally requires the Go UUID wire
format. HTTPIdentityRegistration makes one public
POST to identity/register without bearer credentials or an idempotency key.
Configuration uses the same canonical deployment policy as GoSessionAuthority:
HTTPS, with explicitly admitted Debug loopback HTTP. No automatic retry or
registration-to-sign-in chain is added.

Native inputs use an explicit subset of the Go contract:

- Login trims Go's Unicode whitespace set, accepts only ASCII before lowercase
  normalization, then requires `[a-z0-9][a-z0-9._@+-]{2,127}`. Unicode simple-case
  aliases accepted by Go are deliberately outside this native login input policy.
- Display name trims the same whitespace, requires 1–80 Unicode scalars and
  rejects control characters. No canonical Unicode normalization is performed.
- Password preserves exact input, including spaces/NUL. Require at least twelve
  Unicode scalars and at most 256 UTF-8 bytes. Kotlin also rejects unpaired UTF-16
  surrogates; Swift String already provides valid scalar values.

Invalid fields are values and are refused before HTTP. Shared fixture inputs
define both platforms' normalization/counting policy. Successful replies require
201, exactly id/login/display_name, a canonical nonzero UUID and canonical admitted
profile fields matching the command. Compare display-name UTF-8 exactly on Swift;
semantic String equality alone can admit a different normalization. Unexpected
status/fields/input produce an internal failure with safe request metadata.

## Product ownership and lifecycle

Account → Create account opens a separate native form. Password drafts live only
in transient view state and the submitted operation, never saved navigation,
device storage, the pending Notes journal or read cache. Clear drafts on submit,
dismissal, background/inactivity and session changes. Use a separate sign-in form;
continuing may prefill only the admitted login. Password must be entered again.

Only the fully signed-out phase permits submission. Guard one in-flight request,
and prevent another submit after confirmed success until the user opens a new
form. Track owner generation and session epoch; cancellation/dismissal/background
invalidate publication. A late reply cannot issue credentials, replace the current
session, navigate a replacement owner or claim success in a different epoch.

Registration success does not automatically sign in. Continue to sign in is a
navigation action. Explicit sign-in uses the existing SessionAuthority/vault
pipeline; GoSessionAuthority then bootstraps Personal through Account. It does not
adopt guest Notes. Confirmed authenticated binding clears obsolete registration
presentation. First verified recovery email remains a later explicit action.

Duplicate login is an expected refusal, never proof of ownership or permission
to sign in. It cannot overwrite an existing name or password. Unavailable,
timeout, cancellation, malformed or unexpected replies may follow a committed
creation. Mark creation unconfirmed and suggest trying sign-in before another
explicit registration. Cancellation is not rollback. Do not invent receipts,
automatic replay, a durable password journal or an exactly-once claim.

## Acceptance

Exercise shared normalization/Unicode bounds, secret redaction, strict replies,
one public call and cancellation. Owner checks cover no implicit grant/vault
write, double submit, expected duplicate versus uncertainty, and late publication
after cancellation/session replacement. Native UI checks create real unseeded
users, clear password fields, explicitly sign in, create Notes and restore after
process restart. Re-register with a different password and prove original
credentials still work. A test-owned proxy loses a committed registration reply;
assert one registration attempt/commit and recovery through explicit sign-in.

Read [construction/evidence](../../docs/blueprints/native-registration.md).
Contact change, account deletion, invitations/membership roles, registration mail
policy, MFA/OIDC/passkeys, automatic synchronization and release/device acceptance
remain separate product capabilities.
