package dev.mobilefoundry.catalog.installations

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import dev.mobilefoundry.kernel.*
import dev.mobilefoundry.services.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** App/deployment namespace, independent of auth credentials; one process owner per namespace. */
class KeystoreInstallationVault(context: Context, namespace: String) : InstallationVault {
    init { require(namespace.matches(Regex("[A-Za-z0-9._-]{1,100}"))) }
    private val mutex = synchronized(locks) { locks.getOrPut(namespace) { Mutex() } }
    private val alias = "dev.mobilefoundry.installation.$namespace"
    private val file = AtomicFile(File(context.applicationContext.noBackupFilesDir,"installation-$namespace.bin"))
    private val aad = "installation.v1.$namespace".toByteArray(Charsets.UTF_8)
    override suspend fun loadOrCreate(): AppResult<InstallationIdentity> = mutex.withLock {
        withContext(Dispatchers.IO) {
            currentCoroutineContext().ensureActive()
            try {
                val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                val exists = file.baseFile.exists() || File(file.baseFile.path + ".bak").exists()
                if (exists) {
                    val bytes = file.openRead().use { input ->
                        val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(512)
                        while (true) { val n = input.read(buffer); if (n < 0) break; if (output.size() + n > 1100) return@withContext Outcome.Err(InstallationFailures.invalidStorage); output.write(buffer,0,n) }
                        output.toByteArray()
                    }
                    if (bytes.size !in 30..1100 || bytes[0] != 1.toByte()) return@withContext Outcome.Err(InstallationFailures.invalidStorage)
                    val key = store.getKey(alias,null) as? SecretKey ?: return@withContext Outcome.Err(InstallationFailures.invalidStorage)
                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    cipher.init(Cipher.DECRYPT_MODE,key,GCMParameterSpec(128,bytes.copyOfRange(1,13))); cipher.updateAAD(aad)
                    return@withContext InstallationIdentity.decode(cipher.doFinal(bytes.copyOfRange(13,bytes.size)))
                }
                val key = store.getKey(alias,null) as? SecretKey ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
                    init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setKeySize(256)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
                }.generateKey()
                val identity = newInstallationIdentity()
                val cipher = Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key);cipher.updateAAD(aad)
                val encrypted = cipher.doFinal(identity.encoded()); check(cipher.iv.size == 12)
                val envelope = byteArrayOf(1) + cipher.iv + encrypted
                currentCoroutineContext().ensureActive()
                val stream = file.startWrite()
                try { stream.write(envelope);file.finishWrite(stream) } catch (error: Exception) { file.failWrite(stream);throw error }
                if (file.baseFile.length() == envelope.size.toLong() && file.openRead().use { it.readBytes() }.contentEquals(envelope)) Outcome.Ok(identity) else Outcome.Err(InstallationFailures.storage)
            } catch (error: CancellationException) { throw error }
            catch (_: javax.crypto.AEADBadTagException) { Outcome.Err(InstallationFailures.invalidStorage) }
            catch (_: GeneralSecurityException) { Outcome.Err(InstallationFailures.storage) }
            catch (_: java.security.ProviderException) { Outcome.Err(InstallationFailures.storage) }
            catch (_: SecurityException) { Outcome.Err(InstallationFailures.storage) }
            catch (_: IOException) { Outcome.Err(InstallationFailures.storage) }
        }
    }
    private companion object { val locks = mutableMapOf<String,Mutex>() }
}
class MemoryInstallationVault : InstallationVault {
    private val mutex = Mutex(); private var identity: InstallationIdentity? = null
    override suspend fun loadOrCreate(): AppResult<InstallationIdentity> = mutex.withLock {
        currentCoroutineContext().ensureActive()
        Outcome.Ok(identity ?: newInstallationIdentity().also { identity = it })
    }
}
private fun newInstallationIdentity(): InstallationIdentity {
    val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
    return checkNotNull(InstallationIdentity.make(UUID.randomUUID().toString(),"i1." + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)))
}
