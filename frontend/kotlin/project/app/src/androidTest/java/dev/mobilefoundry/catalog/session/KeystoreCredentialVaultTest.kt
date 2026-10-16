package dev.mobilefoundry.catalog.session

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.mobilefoundry.kernel.*
import dev.mobilefoundry.session.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.KeyStore
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class KeystoreCredentialVaultTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun credential(person: String) = StoredCredential.make(SessionScope.make("catalog-demo.v1", person, "$person-personal")!!, "refresh.$person.opaque")!!
    private fun file(namespace: String) = File(context.noBackupFilesDir, "session-$namespace.bin")
    private fun store() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private fun cleanup(namespace: String) { listOf("", ".bak", ".new").forEach { File(file(namespace).path + it).delete() }; store().deleteEntry("dev.mobilefoundry.session.$namespace") }
    @Test fun nativeEncryptionRestoresAcrossAdapterInstancesAndReplacesWithNewNonce() = runBlocking {
        val namespace = "test-${UUID.randomUUID()}"; val vault = KeystoreCredentialVault(context, namespace)
        try {
            assertEquals(Outcome.Ok(null), vault.load()); assertEquals(Outcome.Ok(Unit), vault.save(credential("alex")))
            val first = file(namespace).readBytes()
            assertFalse(first.toString(Charsets.UTF_8).contains("refresh.alex.opaque")); assertFalse(first.toString(Charsets.UTF_8).contains("alex-personal"))
            assertNull(store().getKey("dev.mobilefoundry.session.$namespace", null).encoded)
            assertEquals(Outcome.Ok(credential("alex")), KeystoreCredentialVault(context, namespace).load())
            assertEquals(Outcome.Ok(Unit), vault.save(credential("alex"))); assertFalse(first.contentEquals(file(namespace).readBytes()))
            assertEquals(Outcome.Ok(Unit), vault.save(credential("sam"))); assertEquals(Outcome.Ok(credential("sam")), vault.load())
            assertEquals(Outcome.Ok(Unit), vault.clear()); assertEquals(Outcome.Ok(Unit), vault.clear()); assertEquals(Outcome.Ok(null), KeystoreCredentialVault(context, namespace).load())
        } finally { cleanup(namespace) }
    }
    @Test fun tamperedCiphertextAndUnknownVersionsAreKeptUntilExplicitClear() = runBlocking {
        val namespace = "test-${UUID.randomUUID()}"; val vault = KeystoreCredentialVault(context, namespace)
        try {
            assertEquals(Outcome.Ok(Unit), vault.save(credential("alex"))); val original = file(namespace).readBytes()
            for (bytes in listOf(original.clone().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() }, original.clone().apply { this[0] = 2 })) {
                file(namespace).writeBytes(bytes); assertEquals(Outcome.Err(SessionFailures.invalidCredential), vault.load()); assertArrayEquals(bytes, file(namespace).readBytes())
            }
            assertEquals(Outcome.Ok(Unit), vault.clear()); assertFalse(file(namespace).exists())
        } finally { cleanup(namespace) }
    }
    @Test fun lostKeystoreKeyDoesNotCreateReplacementOrDiscardUnreadableCredential() = runBlocking {
        val namespace = "test-${UUID.randomUUID()}"; val vault = KeystoreCredentialVault(context, namespace)
        try {
            assertEquals(Outcome.Ok(Unit), vault.save(credential("alex"))); val bytes = file(namespace).readBytes()
            store().deleteEntry("dev.mobilefoundry.session.$namespace")
            val reopened = KeystoreCredentialVault(context, namespace)
            assertEquals(Outcome.Err(SessionFailures.invalidCredential), reopened.load())
            assertEquals(Outcome.Err(SessionFailures.invalidCredential), reopened.save(credential("sam")))
            assertArrayEquals(bytes, file(namespace).readBytes()); assertFalse(store().containsAlias("dev.mobilefoundry.session.$namespace"))
            assertEquals(Outcome.Ok(Unit), reopened.clear()); assertEquals(Outcome.Ok(Unit), reopened.save(credential("sam")))
        } finally { cleanup(namespace) }
    }
}
