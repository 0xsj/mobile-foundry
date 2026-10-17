package dev.mobilefoundry.catalog.installations

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailabilityLight
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dev.mobilefoundry.catalog.systemnotifications.AndroidRemoteNotifications
import dev.mobilefoundry.catalog.systemnotifications.SystemNotificationComposition
import dev.mobilefoundry.catalog.R
import dev.mobilefoundry.kernel.*
import dev.mobilefoundry.services.*
import kotlinx.coroutines.*
import kotlin.coroutines.resume

/** UI-dispatcher provider adapter. SDK callbacks are independent of session leases. */
class AndroidPushTokens(
    private val tokens: PushTokenStore,
    private val route: () -> PushRoute?,
    private val request: suspend () -> AppResult<String>,
) : PushTokenPort {
    private var busy = false

    companion object {
        /** Called on the UI dispatcher after the provider's app route is admitted. */
        fun received(tokens: PushTokenStore, route: PushRoute, token: String) {
            val target = DeliveryTarget.make(route,token)
            if (target == null) tokens.invalidate() else tokens.receive(target)
        }
    }

    override suspend fun collect(): AppResult<Unit> {
        currentCoroutineContext().ensureActive()
        val admitted = route() ?: return Outcome.Err(PushTokenFailures.unconfigured)
        if (busy) return Outcome.Err(PushTokenFailures.unavailable)
        busy = true
        val revision = tokens.state.value.revision
        try {
            val result = withTimeoutOrNull(30_000) { request() }
                ?: return Outcome.Err(PushTokenFailures.unavailable)
            currentCoroutineContext().ensureActive()
            if (result is Outcome.Err) return result
            val target = DeliveryTarget.make(admitted, (result as Outcome.Ok).value)
                ?: return Outcome.Err(PushTokenFailures.unavailable)
            // A newer onNewToken callback wins over an earlier Task completion.
            if (tokens.state.value.revision == revision) tokens.receive(target)
            return if (tokens.state.value.target != null) Outcome.Ok(Unit) else Outcome.Err(PushTokenFailures.unavailable)
        } finally { busy = false }
    }
}

internal class FirebaseTokenClient(context: Context) {
    private val context = context.applicationContext
    private val values get() = listOf(R.string.google_app_id, R.string.project_id,
        R.string.gcm_defaultSenderId, R.string.google_api_key).map { context.getString(it) }

    fun route(): PushRoute? {
        val config = values
        if (config.any { it.isBlank() || it != it.trim() } || !config[0].startsWith("1:${config[2]}:android:") ||
            !config[2].all(Char::isDigit)) return null
        return PushRoute.make(PushProvider.FCM, context.packageName, PushEnvironment.PRODUCTION)
    }

    @Suppress("DEPRECATION") // Registry v1 uses registration tokens; FID-based sending is a separate contract.
    suspend fun token(): AppResult<String> {
        if (route() == null) return Outcome.Err(PushTokenFailures.unconfigured)
        if (GoogleApiAvailabilityLight.getInstance().isGooglePlayServicesAvailable(context) != ConnectionResult.SUCCESS)
            return Outcome.Err(PushTokenFailures.unavailable)
        val config = values
        val task = try {
            val options = FirebaseOptions.Builder().setApplicationId(config[0]).setProjectId(config[1])
                .setGcmSenderId(config[2]).setApiKey(config[3]).build()
            val existing = FirebaseApp.getApps(context).firstOrNull { it.name == FirebaseApp.DEFAULT_APP_NAME }
            if (existing != null && existing.options != options) return Outcome.Err(PushTokenFailures.unconfigured)
            if (existing == null) FirebaseApp.initializeApp(context, options)
            val messaging = FirebaseMessaging.getInstance()
            // Also overrides any persisted SDK preference from an earlier build.
            messaging.isAutoInitEnabled = false
            messaging.token
        } catch (_: IllegalArgumentException) { return Outcome.Err(PushTokenFailures.unconfigured) }
          catch (_: IllegalStateException) { return Outcome.Err(PushTokenFailures.unconfigured) }
        return task.awaitProviderResult()
    }
}

private suspend fun Task<String>.awaitProviderResult(): AppResult<String> = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { answer ->
        if (continuation.isActive) continuation.resume(
            if (answer.isSuccessful) Outcome.Ok(answer.result) else Outcome.Err(PushTokenFailures.unavailable))
    }
}

object PushTokenComposition {
    val tokens = PushTokenStore()
    fun provider(context: Context): PushTokenPort {
        val client = FirebaseTokenClient(context)
        return AndroidPushTokens(tokens, client::route, client::token)
    }
}

class PushMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        // This contract is notification + data. Ignore data-only/background-work messages.
        if (message.notification == null) return
        receivePreview(this, message.data)
    }
    companion object {
        internal fun receivePreview(context: Context, payload: Map<String, String>): Boolean {
            val preview = RemoteNotificationPreview.decode(payload) ?: return false
            Handler(Looper.getMainLooper()).post { SystemNotificationComposition.opens.recordReceipt(preview) }
            AndroidRemoteNotifications.post(context, preview)
            return true
        }
    }
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onNewToken(token: String) {
        val route = FirebaseTokenClient(this).route() ?: return
        Handler(Looper.getMainLooper()).post { AndroidPushTokens.received(PushTokenComposition.tokens,route,token) }
    }
}
