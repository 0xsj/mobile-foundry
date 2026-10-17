# Unicode scalars and byte bounds

Claim: JVM code-point counting needs explicit surrogate admission before it can
match a Swift/Go scalar-based input contract. Origin: native registration,
2026-10-17, Kotlin 2.3.20/JVM toolchain 17. Evidence: shared registration vectors
and the direct unpaired-surrogate cases in
[IdentityRegistrationTest](../../project/core/session/src/test/kotlin/dev/mobilefoundry/session/IdentityRegistrationTest.kt).

## What and why

JVM String indexes UTF-16 code units; a supplementary emoji uses two units.
codePointCount combines valid surrogate pairs but still counts unpaired surrogates.
codePoints exposes those unpaired values, allowing admission to reject the
surrogate range. UTF-8 encoding with a Charset replaces malformed input, so
encoding first can silently change a password.
[JDK String reference](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/String.html#codePointCount(int,int)),
[encoding reference](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/String.html#getBytes(java.nio.charset.Charset)).

```kotlin
// Excerpt: RegistrationAdmission checks scalar validity before byte bounds.
fun unicode(value: String) = value.codePoints().noneMatch { it in 0xD800..0xDFFF }
```

RegisterIdentity then measures at least twelve code points and at most 256 UTF-8
bytes. It keeps the password unchanged. Login and display name have separate
normalization rules: an explicit Go whitespace set and ASCII-only login input.
Locale.ROOT prevents ambient locale from defining login case policy.

## Gotchas, actual use and next

Scalar count is not user-perceived grapheme count. A decomposed accent sequence
can contain two scalars; a 64-emoji password reaches 256 UTF-8 bytes. The shared
fixtures exercise both limits, preserve spaces/NUL and reject 65 four-byte emoji.
This does not define a password-strength or locale-aware username policy.

Read [the command](../../project/core/session/src/main/kotlin/dev/mobilefoundry/session/IdentityRegistration.kt),
[registration boundary](../../../../contracts/behavior/native-registration.md),
[Swift byte comparison](../../../swift/notes/language/swift-string-equality-and-command-bytes.md)
and [actual execution](../../../../docs/blueprints/native-registration.md).
Next: should a real product support internationalized usernames, and which
canonicalization/version policy must all providers implement before adding them?
