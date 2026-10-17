package dev.mobilefoundry.catalog.installations

import android.util.Log
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mobilefoundry.services.*
import dev.mobilefoundry.ui.components.display.card.Card
import dev.mobilefoundry.ui.components.forms.button.ActionButton
import dev.mobilefoundry.ui.components.forms.button.ButtonVariant
import dev.mobilefoundry.ui.components.patterns.sectionheader.SectionHeader
import dev.mobilefoundry.ui.theme.FoundryTheme

@Composable fun PushTokenCatalogCard(installations: InstallationController? = null) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val model = remember(context) {
        PushTokenController(scope, PushTokenComposition.tokens, PushTokenComposition.provider(context)) {
            Log.e("PushTokens", "Unexpected push token failure.")
        }
    }
    val state = model.state.collectAsStateWithLifecycle().value
    val tokens = model.tokens.state.collectAsStateWithLifecycle().value
    val registration = installations?.state?.collectAsStateWithLifecycle()?.value
    DisposableEffect(model) { onDispose { model.close() } }
    Card {
        SectionHeader("Provider token", "Explicit FCM collection. Permission and backend registration are separate steps.")
        Text(if (model.target == null) "No collected token" else "Token ready · local revision ${tokens.revision}", Modifier.testTag("push.status"))
        model.target?.let { Text("${it.route.provider.wire} · ${it.route.environment.wire} · ${it.route.application}") }
        ActionButton({ model.collect() }, isBusy = state.busy, enabled = !state.busy, modifier = Modifier.testTag("push.collect")) { Text("Collect device token") }
        if (installations != null) {
            ActionButton({ model.target?.let(installations::register) }, variant = ButtonVariant.SECONDARY,
                enabled = registration != null && installations.canRegister && model.target != null,
                modifier = Modifier.testTag("push.register")) { Text("Register collected token") }
        }
        state.failure?.let { Text(it.meta.message, Modifier.testTag("push.failure")) }
        Text("Token values stay hidden. Rotation updates this candidate; it never writes to the backend automatically. Firebase setup and Play services are required. Collection does not prove push delivery.", style = FoundryTheme.tokens.typography.caption)
    }
}
