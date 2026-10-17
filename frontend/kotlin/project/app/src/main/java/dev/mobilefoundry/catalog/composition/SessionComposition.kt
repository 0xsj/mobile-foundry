package dev.mobilefoundry.catalog.composition

import android.content.Context
import dev.mobilefoundry.session.*
import dev.mobilefoundry.http.OkHttpTransport
import dev.mobilefoundry.services.InstallationVault
import dev.mobilefoundry.catalog.installations.KeystoreInstallationVault
import dev.mobilefoundry.catalog.installations.MemoryInstallationVault

enum class SessionProvider(val label: String) { DEVICE("Device"), MEMORY("Memory") }
object SessionComposition {
    private var deviceVault: CredentialVault? = null
    private val goVaults = mutableMapOf<String, CredentialVault>()
    private val installationVaults = mutableMapOf<String, InstallationVault>()
    @Synchronized fun installationVault(context: Context, provider: SessionProvider, authorityId: String): InstallationVault =
        installationVaults.getOrPut("${provider.name}.$authorityId") {
            if (provider == SessionProvider.DEVICE) KeystoreInstallationVault(context,"catalog-go.$authorityId") else MemoryInstallationVault()
        }
    val goTransport = OkHttpTransport()
    @Synchronized fun vault(context: Context, provider: SessionProvider): CredentialVault = when (provider) {
        SessionProvider.DEVICE -> deviceVault ?: KeystoreCredentialVault(context, "catalog-demo.v1").also { deviceVault = it }
        SessionProvider.MEMORY -> MemoryCredentialVault()
    }
    @Synchronized fun goVault(context: Context, provider: SessionProvider, authorityId: String): CredentialVault = when (provider) {
        SessionProvider.MEMORY -> MemoryCredentialVault()
        SessionProvider.DEVICE -> goVaults.getOrPut(authorityId) { KeystoreCredentialVault(context, "catalog-go.$authorityId") }
    }
}
