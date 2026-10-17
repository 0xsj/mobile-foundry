package dev.mobilefoundry.catalog.systemnotifications

import android.Manifest
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mobilefoundry.services.*
import dev.mobilefoundry.catalog.installations.PushTokenCatalogCard
import dev.mobilefoundry.ui.components.display.card.Card
import dev.mobilefoundry.ui.components.forms.button.ActionButton
import dev.mobilefoundry.ui.components.forms.button.ButtonVariant
import dev.mobilefoundry.ui.components.layout.stack.VerticalStack
import dev.mobilefoundry.ui.components.patterns.sectionheader.SectionHeader
import dev.mobilefoundry.ui.theme.FoundryTheme
import kotlinx.coroutines.CompletableDeferred

private class PermissionRequest {
    private var pending: CompletableDeferred<Unit>? = null
    suspend fun launch(start: () -> Unit) {
        check(pending == null)
        val reply = CompletableDeferred<Unit>();pending=reply
        try { start();reply.await() } finally { if (pending === reply) pending=null }
    }
    fun finish() { pending?.complete(Unit) }
    fun cancel() { pending?.cancel();pending=null }
}
@Composable fun SystemNotificationCatalogScreen(onBack: () -> Unit,modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope();val request = remember { PermissionRequest() }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { request.finish() }
    val port = remember(context) { AndroidSystemNotifications(context) { request.launch { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) } } }
    val model = remember(port) { SystemNotificationController(scope,port) { Log.e("SystemNotifications","Unexpected system notification failure.") } }
    val state = model.state.collectAsStateWithLifecycle().value
    val opens = SystemNotificationComposition.opens.state.collectAsStateWithLifecycle().value
    DisposableEffect(model) { onDispose { model.close();request.cancel() } }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { model.refresh() }
    VerticalStack(modifier.fillMaxSize().verticalScroll(rememberScrollState()),spacing=FoundryTheme.tokens.space.section) {
        TextButton(onClick=onBack) { Text("Back") }
        Text("System notifications",style=FoundryTheme.tokens.typography.title)
        Card {
            SectionHeader("Notification permission","Device permission is separate from your account's in-app inbox preference.")
            Text("Permission: ${state.access?.authorization?.name ?: "Unknown"}",Modifier.testTag("notifications.permission"))
            Text(if (state.access?.deliveryEnabled == true) "Delivery settings: enabled" else "Delivery settings: disabled or unread")
            ActionButton({ model.requestPermission() },enabled=!state.busy,modifier=Modifier.testTag("notifications.allow")) { Text("Allow notifications") }
            ActionButton({ model.refresh() },variant=ButtonVariant.SECONDARY,enabled=!state.busy) { Text("Refresh permission") }
            ActionButton({ model.openSettings() },variant=ButtonVariant.QUIET,enabled=!state.busy) { Text("Open notification settings") }
            Text("If permission was denied, use Settings. The app switch, channel settings and Do Not Disturb can affect presentation.",style=FoundryTheme.tokens.typography.caption)
        }
        Card {
            SectionHeader("Local preview","A generic device notification, with no backend or account content.")
            ActionButton({ model.postPreview() },enabled=model.canPost,modifier=Modifier.testTag("notifications.post")) { Text("Show test notification") }
            ActionButton({ model.clearPreview() },variant=ButtonVariant.SECONDARY,enabled=!state.busy,modifier=Modifier.testTag("notifications.clear")) { Text("Clear test notification") }
            state.lastAcceptedId?.let { Text("Accepted by OS: $it",Modifier.testTag("notifications.accepted")) }
            Text("Open the notification drawer and tap the preview to return here. OS acceptance does not prove display or remote push delivery.",style=FoundryTheme.tokens.typography.caption)
        }
        Card {
            SectionHeader("Notification open")
            Text(opens.lastOpened?.let { (if (it.isRemote) "Opened push preview: " else "Opened preview: ") + it.previewId } ?: "No preview opened in this process",Modifier.testTag("notifications.opened"))
            Text("Preview taps only open this screen. Account selection, inbox reads and installation registration stay separate.",style=FoundryTheme.tokens.typography.caption)
        }
        Card {
            SectionHeader("Remote preview","APNs / FCM notification opens return here without selecting an account.")
            Text(opens.lastReceived?.let { "Received push preview: ${it.id}" } ?: "No remote preview received in this foreground process",Modifier.testTag("notifications.remote-received"))
            Text("Send a preview from your configured backend to test delivery. Receiving a foreground preview does not navigate; tap its notification to open this screen. Device permission and provider setup are required.",style=FoundryTheme.tokens.typography.caption)
        }
        state.failure?.let { Text(it.meta.message,style=FoundryTheme.tokens.typography.caption) }
        PushTokenCatalogCard()
    }
}
