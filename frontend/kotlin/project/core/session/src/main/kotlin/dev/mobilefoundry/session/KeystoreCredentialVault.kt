package dev.mobilefoundry.session

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import dev.mobilefoundry.kernel.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** One shared instance per namespace; file commits and key operations run on IO. */
class KeystoreCredentialVault(context: Context, namespace: String) : CredentialVault {
    init { require(namespace.matches(Regex("[A-Za-z0-9._-]{1,100}"))) }
    private val mutex = Mutex()
    private val alias = "dev.mobilefoundry.session.$namespace"
    private val file = AtomicFile(File(context.applicationContext.noBackupFilesDir, "session-$namespace.bin"))
    private val aad = "session.v1.$namespace".toByteArray(Charsets.UTF_8)
    override suspend fun load(): AppResult<StoredCredential?> = locked {
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return@locked Outcome.Ok(null)
        val bytes = file.openRead().use { input ->
            val output = ByteArrayOutputStream(); val buffer = ByteArray(4096)
            while (true) { val count = input.read(buffer); if (count < 0) break; if (output.size() + count > 65_600) return@locked Outcome.Err(SessionFailures.invalidCredential); output.write(buffer, 0, count) }
            output.toByteArray()
        }
        if (bytes.size < 30 || bytes[0] != 1.toByte()) return@locked Outcome.Err(SessionFailures.invalidCredential)
        val key = store().getKey(alias, null) as? SecretKey ?: return@locked Outcome.Err(SessionFailures.invalidCredential)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, bytes.copyOfRange(1, 13))); cipher.updateAAD(aad)
        when (val decoded = StoredCredential.decode(cipher.doFinal(bytes.copyOfRange(13, bytes.size)))) {
            is Outcome.Err -> decoded
            is Outcome.Ok -> Outcome.Ok(decoded.value)
        }
    }
    override suspend fun save(credential: StoredCredential): AppResult<Unit> = locked {
        val keyStore = store()
        val key = (keyStore.getKey(alias, null) as? SecretKey) ?: run {
            // A lost key cannot silently replace an existing unreadable record.
            if (file.baseFile.exists() || File(file.baseFile.path + ".bak").exists()) return@locked Outcome.Err(SessionFailures.invalidCredential)
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
                init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
            }.generateKey()
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key); cipher.updateAAD(aad)
        val encrypted = cipher.doFinal(credential.encoded())
        check(cipher.iv.size == 12)
        val envelope = byteArrayOf(1) + cipher.iv + encrypted
        currentCoroutineContext().ensureActive()
        val stream = file.startWrite()
        try { stream.write(envelope); file.finishWrite(stream) }
        catch (error: Exception) { file.failWrite(stream); throw error }
        // finishWrite returns void; also verify replacement before acknowledging it.
        if (file.baseFile.length() == envelope.size.toLong() && file.openRead().use { it.readBytes() }.contentEquals(envelope)) Outcome.Ok(Unit)
        else Outcome.Err(SessionFailures.storage)
    }
    override suspend fun clear(): AppResult<Unit> = locked {
        file.delete()
        if (listOf("", ".bak", ".new").any { File(file.baseFile.path + it).exists() }) Outcome.Err(SessionFailures.storage) else Outcome.Ok(Unit)
    }
    private fun store() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private suspend fun <T> locked(block: suspend () -> AppResult<T>): AppResult<T> = mutex.withLock {
        withContext(Dispatchers.IO) {
            currentCoroutineContext().ensureActive()
            try { block() }
            catch (canceled: CancellationException) { throw canceled }
            catch (_: javax.crypto.AEADBadTagException) { Outcome.Err(SessionFailures.invalidCredential) }
            catch (_: GeneralSecurityException) { Outcome.Err(SessionFailures.storage) }
            catch (_: java.security.ProviderException) { Outcome.Err(SessionFailures.storage) }
            catch (_: SecurityException) { Outcome.Err(SessionFailures.storage) }
            catch (_: IOException) { Outcome.Err(SessionFailures.storage) }
        }
    }
}
