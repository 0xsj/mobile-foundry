package dev.mobilefoundry.catalog.systemnotifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.mobilefoundry.catalog.MainActivity
import dev.mobilefoundry.catalog.R
import dev.mobilefoundry.services.RemoteNotificationPreview

/** App-only FCM envelope/presentation adapter. A valid hint grants no authority. */
object AndroidRemoteNotifications {
    const val CHANNEL = "catalog.push-preview.v1"
    const val TAG = "catalog.push-preview.v1"
    private const val ACTION = "dev.mobilefoundry.catalog.PUSH_PREVIEW"
    private val fields = setOf("version", "kind", "preview_id")
    // SDK transport metadata is ignored, never projected into application data.
    private val strings = setOf("google.message_id", "google.original_priority", "google.delivered_priority",
        "google.priority", "google.to", "google.c.sender.id", "from", "collapse_key")
    private val numbers = setOf("google.sent_time", "google.ttl")
    private const val ANALYTICS = "gcm.n.analytics_data"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CHANNEL, "Push previews", NotificationManager.IMPORTANCE_DEFAULT))
    }
    private fun uri(context: Context): Uri = Uri.Builder().scheme("mobile-foundry-preview")
        .authority(context.packageName).appendPath("v1").appendPath("push-preview").build()

    fun previewIntent(context: Context, preview: RemoteNotificationPreview): Intent = Intent(context, MainActivity::class.java)
        .setAction(ACTION).setData(uri(context)).apply { preview.payload.forEach { (key, value) -> putExtra(key, value) } }
        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    fun readPreview(context: Context, intent: Intent): RemoteNotificationPreview? = try {
        val own = intent.action == ACTION && intent.dataString == uri(context).toString()
        val launcher = intent.action == Intent.ACTION_MAIN && intent.data == null &&
            intent.component == ComponentName(context, MainActivity::class.java) &&
            (intent.categories.isNullOrEmpty() || intent.categories == setOf(Intent.CATEGORY_LAUNCHER))
        if (!own && !launcher) null else {
            val extras = intent.extras
            if (extras == null || extras.size() > 20 || !admittedExtras(extras, launcher)) null else {
                val payload = fields.associateWith { extras.getString(it) ?: "" }
                RemoteNotificationPreview.decode(payload)
            }
        }
    } catch (_: RuntimeException) { null } // Exported Activity input may contain malformed parcels/types.

    @Suppress("DEPRECATION")
    private fun admittedExtras(extras: Bundle, metadata: Boolean): Boolean {
        if (!extras.keySet().containsAll(fields)) return false
        return extras.keySet().all { key ->
            val value = extras.get(key)
            when {
                key in fields -> value is String && value.length <= 128
                !metadata -> false
                key in strings -> value is String && value.length <= 4096
                key in numbers -> value is Int || value is Long || (value is String && value.length <= 32 && value.toLongOrNull() != null)
                key == ANALYTICS && value is Bundle -> value.size() <= 16 && value.keySet().all { name ->
                    name.length <= 128 && (value.get(name) as? String)?.length?.let { it <= 4096 } == true
                }
                else -> false
            }
        }
    }

    /** Foreground FCM callback: one replaceable remote slot, separate from local previews. */
    fun post(context: Context, preview: RemoteNotificationPreview): Boolean {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        ensureChannel(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26 && manager.getNotificationChannel(CHANNEL)?.importance == NotificationManager.IMPORTANCE_NONE) return false
        val pending = PendingIntent.getActivity(context, 1, previewIntent(context, preview), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_notification_preview)
            .setContentTitle("Mobile push preview").setContentText("Open the app to continue.")
            .setContentIntent(pending).setAutoCancel(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setTimeoutAfter(300_000).build()
        return try { manager.notify(TAG, 1, notification); true } catch (_: SecurityException) { false }
    }

    fun clearedIntent(context: Context): Intent = Intent(context, MainActivity::class.java)
        .setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
}
