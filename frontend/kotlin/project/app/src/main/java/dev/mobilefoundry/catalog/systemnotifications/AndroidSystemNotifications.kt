package dev.mobilefoundry.catalog.systemnotifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.mobilefoundry.catalog.MainActivity
import dev.mobilefoundry.catalog.R
import dev.mobilefoundry.kernel.*
import dev.mobilefoundry.services.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

class AndroidSystemNotifications(context: Context, private val request: suspend () -> Unit) : SystemNotificationPort {
    private val context = context.applicationContext
    private val manager = this.context.getSystemService(NotificationManager::class.java)
    override suspend fun access(): AppResult<NotificationAccess> {
        currentCoroutineContext().ensureActive()
        val granted = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val enabled = granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
        val channelEnabled = Build.VERSION.SDK_INT < 26 || manager.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
        return Outcome.Ok(NotificationAccess(if (enabled) NotificationAuthorization.ALLOWED else NotificationAuthorization.NOT_GRANTED,enabled && channelEnabled))
    }
    override suspend fun requestPermission(): AppResult<NotificationAccess> {
        currentCoroutineContext().ensureActive()
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) request()
        return access()
    }
    override suspend fun postPreview(preview: LocalNotificationPreview): AppResult<Unit> {
        when (val access = access()) { is Outcome.Err -> return access;is Outcome.Ok -> if (!access.value.canPost) return Outcome.Err(SystemNotificationFailures.blocked) }
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel(CHANNEL,"Local notification preview",NotificationManager.IMPORTANCE_DEFAULT).apply { description = "Explicit catalog test notifications" })
        val pending = PendingIntent.getActivity(context,0,previewIntent(context,preview),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context,CHANNEL)
            .setSmallIcon(R.drawable.ic_notification_preview)
            .setContentTitle("Mobile notification preview").setContentText("Tap to return to System notifications.")
            .setContentIntent(pending).setAutoCancel(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setTimeoutAfter(300_000).build()
        currentCoroutineContext().ensureActive()
        return try { manager.notify(TAG,1,notification);Outcome.Ok(Unit) }
        catch (_: SecurityException) { Outcome.Err(SystemNotificationFailures.blocked) }
    }
    override suspend fun clearPreview(): AppResult<Unit> {
        currentCoroutineContext().ensureActive();manager.cancel(TAG,1);return Outcome.Ok(Unit)
    }
    override suspend fun openSettings(): AppResult<Unit> {
        currentCoroutineContext().ensureActive()
        val intent = if (Build.VERSION.SDK_INT >= 26) Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName)
            else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${context.packageName}"))
        return try { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));Outcome.Ok(Unit) }
        catch (_: ActivityNotFoundException) { Outcome.Err(SystemNotificationFailures.unavailable) }
        catch (_: SecurityException) { Outcome.Err(SystemNotificationFailures.unavailable) }
    }
    companion object {
        const val CHANNEL = "catalog.local-preview.v1"
        const val TAG = "catalog.local-preview.v1"
        private const val ACTION = "dev.mobilefoundry.catalog.NOTIFICATION_PREVIEW"
        private const val EXTRA_ID = "notification_preview_id"
        private fun uri(context: Context): Uri = Uri.Builder().scheme("mobile-foundry-preview").authority(context.packageName).appendPath("v1").appendPath("local-preview").build()
        // Fixed PendingIntent identity bounds ownership to one replaceable preview slot.
        fun previewIntent(context: Context,preview: LocalNotificationPreview): Intent = Intent(context,MainActivity::class.java)
            .setAction(ACTION).setData(uri(context)).putExtra(EXTRA_ID,preview.id)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        fun clearedIntent(base: Intent): Intent = Intent(base).setAction(Intent.ACTION_MAIN).setData(null).apply { removeExtra(EXTRA_ID) }
        fun readPreview(context: Context,intent: Intent): LocalNotificationPreview? {
            if (intent.action != ACTION || intent.dataString != uri(context).toString()) return null
            return try {
                if (intent.extras?.keySet() != setOf(EXTRA_ID)) return null
                intent.getStringExtra(EXTRA_ID)?.takeIf { it.length == 36 }?.let(LocalNotificationPreview::make)
            } catch (_: RuntimeException) { null } // Malformed external Intent payloads are untrusted input.
        }
    }
}
object SystemNotificationComposition { val opens = NotificationOpenRouter() }
