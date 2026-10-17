package dev.mobilefoundry.catalog.installations

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.mobilefoundry.kernel.*
import dev.mobilefoundry.services.*
import dev.mobilefoundry.session.KeystoreCredentialVault
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.KeyStore
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class InstallationVaultTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun file(ns: String) = File(context.noBackupFilesDir,"installation-$ns.bin")
    private fun store() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private fun cleanup(ns: String) { listOf("",".bak",".new").forEach { File(file(ns).path+it).delete() };store().deleteEntry("dev.mobilefoundry.installation.$ns") }
    @Test fun encryptedIdentitySurvivesReopenAndConcurrentCreation(): Unit = runBlocking {
        val ns = "test-${UUID.randomUUID()}";val other = ns+".other"
        try {
            val a = async { (KeystoreInstallationVault(context,ns).loadOrCreate() as Outcome.Ok).value }
            val b = async { (KeystoreInstallationVault(context,ns).loadOrCreate() as Outcome.Ok).value }
            val first = a.await();assertEquals(first.key,b.await().key)
            assertEquals(Outcome.Ok(Unit),KeystoreCredentialVault(context,ns).clear())
            val reopened = (KeystoreInstallationVault(context,ns).loadOrCreate() as Outcome.Ok).value
            assertEquals(first.id,reopened.id);assertEquals(first.key,reopened.key)
            assertFalse(file(ns).readBytes().toString(Charsets.UTF_8).contains(first.key))
            assertNull(store().getKey("dev.mobilefoundry.installation.$ns",null).encoded)
            assertNotEquals(first.id,(KeystoreInstallationVault(context,other).loadOrCreate() as Outcome.Ok).value.id)
        } finally { cleanup(ns);cleanup(other) }
    }
    @Test fun tamperedRecordAndLostKeyAreNeverReset(): Unit = runBlocking {
        val ns = "test-${UUID.randomUUID()}"
        try {
            assertTrue(KeystoreInstallationVault(context,ns).loadOrCreate() is Outcome.Ok)
            val original = file(ns).readBytes();val corrupted = original.clone().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() }
            for (bad in listOf(corrupted,original.clone().apply { this[0] = 2 })) {
                file(ns).writeBytes(bad)
                assertEquals(Outcome.Err(InstallationFailures.invalidStorage),KeystoreInstallationVault(context,ns).loadOrCreate());assertArrayEquals(bad,file(ns).readBytes())
            }
            file(ns).writeBytes(original);store().deleteEntry("dev.mobilefoundry.installation.$ns")
            assertEquals(Outcome.Err(InstallationFailures.invalidStorage),KeystoreInstallationVault(context,ns).loadOrCreate())
            assertArrayEquals(original,file(ns).readBytes());assertFalse(store().containsAlias("dev.mobilefoundry.installation.$ns"))
        } finally { cleanup(ns) }
    }
}
