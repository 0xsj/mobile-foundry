package dev.mobilefoundry.catalog.installations

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mobilefoundry.services.*
import dev.mobilefoundry.ui.components.display.card.Card
import dev.mobilefoundry.ui.components.forms.button.ActionButton
import dev.mobilefoundry.ui.components.forms.button.ButtonVariant
import dev.mobilefoundry.ui.components.forms.password.PasswordField
import dev.mobilefoundry.ui.components.patterns.sectionheader.SectionHeader
import dev.mobilefoundry.ui.theme.FoundryTheme

@Composable fun InstallationCatalogCard(model: InstallationController) {
    val state = model.state.collectAsStateWithLifecycle().value
    val token = remember { TextFieldState() }
    val application = LocalContext.current.packageName
    val route = remember(application) { PushRoute.make(PushProvider.FCM,application,PushEnvironment.PRODUCTION)!! }
    DisposableEffect(token) { onDispose { token.edit { replace(0,length,"") } } }
    PushTokenCatalogCard(model)
    Card {
        SectionHeader("Manual device registration","Alternate registry probe. Use the collected token above for a real provider address; neither control sends a push.")
        state.installationId?.let { Text(it,style=FoundryTheme.tokens.typography.code) }
        state.record?.let {
            Text(if (it.registered) "Registered target · revision ${it.version}" else "Disabled target · revision ${it.version}")
            Text(if (it.currentBinding) "Bound to this session" else "Bound to another session; register to transfer ownership")
            Text("${it.route.provider.wire} · ${it.route.environment.wire} · ${it.route.application}")
            Text("Session binding expires ${it.expiresAt}",style=FoundryTheme.tokens.typography.caption)
        } ?: if (!state.needsRefresh) Text("No saved target on this backend") else Unit
        PasswordField("Opaque token or registry probe address",token,Modifier.fillMaxWidth(),enabled=!state.busy)
        ActionButton({
            DeliveryTarget.make(route,token.text.toString())?.let { model.register(it);token.edit { replace(0,length,"") } }
        },enabled=model.canRegister && DeliveryTarget.make(route,token.text.toString()) != null) { Text(if (state.record == null) "Register target" else "Replace target / bind this session") }
        ActionButton({ model.refresh() },variant=ButtonVariant.SECONDARY,isBusy=state.busy,enabled=!state.busy) { Text("Refresh registration") }
        ActionButton({ model.unregister() },variant=ButtonVariant.DESTRUCTIVE,enabled=model.canUnregister) { Text("Unregister target") }
        if (state.needsRefresh) Text("Refresh before another change. The previous request may have committed.",style=FoundryTheme.tokens.typography.caption)
        state.failure?.let { Text(it.meta.message,style=FoundryTheme.tokens.typography.caption) }
        Text("The device key stays in secure storage when you sign out. Server session revocation retires its target. No automatic registration or write retry.",style=FoundryTheme.tokens.typography.caption)
    }
}
