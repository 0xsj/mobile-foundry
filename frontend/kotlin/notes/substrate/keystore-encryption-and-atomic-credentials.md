# Keystore encryption and atomic credentials

Keystore protects an app-owned encryption key while the app separately owns its ciphertext file, versioning and session lifecycle.

## Origin and versions

Session foundation observed 2026-10-10 with Kotlin 2.3.20, AGP 9.0.1,
JDK 17, coroutines 1.10.2, Android API 36 emulator and minimum API 24.
The module uses AndroidKeyStore, AES/GCM/NoPadding, AtomicFile and noBackupFilesDir.

## What and why

Android documents non-exportable key material and recommends moving Keystore
operations off Main. Hardware backing depends on the device/key configuration;
this adapter does not request or establish StrongBox/TEE backing.
[Keystore reference](https://developer.android.com/privacy-and-security/keystore).

The adapter holds a Mutex across its IO operation. AES-GCM authenticates the
encrypted record, with an independently generated IV and namespace/version
associated data. The envelope is version byte + twelve IV bytes + ciphertext/tag.
Load bounds file size, rejects unknown envelope/record versions and strictly
decodes UTF-8/domain values. The key is generated only for new storage. A missing
key alongside existing ciphertext refuses both load and replacement without
deleting bytes or generating an unusable substitute key.

AtomicFile supports replacement through startWrite/finishWrite and restoration
through failWrite. It does not provide locking; callers must serialize access.
[AtomicFile reference](https://developer.android.com/reference/android/util/AtomicFile).
Save also reads back the committed envelope before acknowledging replacement.
Clear verifies base/backup/new files are gone; it retains the encryption key.
An owned test cleanup may delete its isolated alias afterward.

## Example and gotchas

```kotlin
// Excerpt: key policy inside KeystoreCredentialVault.save.
KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
    .setKeySize(256)
    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
    .build()
```

The ciphertext file is outside automatic backups; key/file transfer recovery
remains a future product policy. Ciphertext authentication failures and lost keys
preserve the record. Do not introduce a plaintext fallback to make the UI pass.
Mutex serialization is per adapter, so composition shares the device instance.
An IO call can commit after cancellation; the controller's sign-out barrier
waits for its completion before clearing. Secret-bearing classes override
toString; private factories also use ConsistentCopyVisibility to close copy bypasses.

## Actual use and checks

[Adapter](../../project/core/session/src/main/kotlin/dev/mobilefoundry/session/KeystoreCredentialVault.kt),
[native checks](../../project/app/src/androidTest/java/dev/mobilefoundry/catalog/session/KeystoreCredentialVaultTest.kt)
and [Session walkthrough](../modules/project/core/session/README.md).
Three native cases pass: fresh-instance restoration/replacement/random IV/non-export,
tampering/unknown version retention, and missing-key refusal plus explicit clear.
These do not establish physical hardware, locked-device, backup, power-loss,
storage-pressure, competing-instance or multi-process behavior.
Read [scope lifetime](../../../../notes/patterns/session-scope-and-credential-lifetime.md)
and [coroutine cancellation](../language/kotlin-suspending-ports-and-cancellation.md).
Next: what should a provider report after uncertain refresh rotation, and how
should a real product recover when its saved ciphertext has lost its key?
