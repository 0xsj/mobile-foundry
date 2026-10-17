package dev.mobilefoundry.catalog.systemnotifications

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import dev.mobilefoundry.catalog.MainActivity
import dev.mobilefoundry.catalog.installations.PushMessagingService
import dev.mobilefoundry.services.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class RemoteNotificationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private fun preview() = RemoteNotificationPreview.make(UUID.randomUUID().toString())!!
    private fun launcher(value: RemoteNotificationPreview) = Intent(context, MainActivity::class.java)
        .setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        .apply {
            value.payload.forEach { (key, item) -> putExtra(key, item) }
            putExtra("google.message_id", "fixture-message"); putExtra("google.sent_time", 1234L)
            putExtra("google.ttl", 0); putExtra("from", "123456")
        }
    private fun opened(value: RemoteNotificationPreview) {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("notifications.opened").fetchSemanticsNodes().firstOrNull()
                ?.config?.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text } == "Opened push preview: ${value.id}"
        }
    }

    @Test fun foregroundReceiptWaitsForTapAndLauncherOpensSurviveRecreation() {
        if (Build.VERSION.SDK_INT >= 33) android.os.ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS")
        ).use { it.readBytes() }
        val manager = context.getSystemService(NotificationManager::class.java)
        val first = preview()
        try {
            compose.runOnIdle {
                val pending = SystemNotificationComposition.opens.state.value.pending
                assertTrue(PushMessagingService.receivePreview(context, first.payload))
                assertEquals(pending, SystemNotificationComposition.opens.state.value.pending)
                assertFalse(PushMessagingService.receivePreview(context, first.payload + ("account_id" to "foreign")))
            }
            compose.waitUntil(8_000) { SystemNotificationComposition.opens.state.value.lastReceived == first }
            val notification = manager.activeNotifications.single { it.tag == AndroidRemoteNotifications.TAG }
            assertEquals("Mobile push preview", notification.notification.extras.getString("android.title"))
            if (Build.VERSION.SDK_INT >= 31) assertTrue(notification.notification.contentIntent.isImmutable)
            notification.notification.contentIntent.send(); opened(first)
            notification.notification.contentIntent.send(); compose.waitForIdle()
            compose.activityRule.scenario.recreate(); opened(first)
            compose.runOnIdle { assertNull(AndroidRemoteNotifications.readPreview(context, compose.activity.intent)) }
            val next = preview()
            context.startActivity(launcher(next)); opened(next)
            compose.activityRule.scenario.recreate(); opened(next)
            compose.runOnIdle {
                assertNull(SystemNotificationComposition.opens.state.value.pending)
                assertNull(AndroidRemoteNotifications.readPreview(context, compose.activity.intent))
            }
        } finally { manager.cancel(AndroidRemoteNotifications.TAG, 1) }
    }

    @Test fun envelopesRefuseExtraAuthorityWrongTypesAndForeignRoutes() {
        val value = preview()
        val own = AndroidRemoteNotifications.previewIntent(context, value)
        assertEquals(value, AndroidRemoteNotifications.readPreview(context, own))
        assertNull(AndroidRemoteNotifications.readPreview(context, Intent(own).putExtra("account_id", "foreign")))
        assertNull(AndroidRemoteNotifications.readPreview(context, Intent(own).putExtra("version", 1)))
        assertNull(AndroidRemoteNotifications.readPreview(context, Intent(own).setData(android.net.Uri.parse(own.dataString + "?account=foreign"))))
        val sdk = launcher(value).putExtra("gcm.n.analytics_data", Bundle().apply { putString("google.c.a.e", "1") })
        assertEquals(value, AndroidRemoteNotifications.readPreview(context, sdk))
        assertNull(AndroidRemoteNotifications.readPreview(context, Intent(sdk).setAction(Intent.ACTION_VIEW)))
        assertNull(AndroidRemoteNotifications.readPreview(context, Intent(sdk).setComponent(ComponentName("foreign", "foreign.Activity"))))
        assertNull(AndroidRemoteNotifications.readPreview(context, Intent(sdk).putExtra("account_id", "foreign")))
        assertNull(AndroidRemoteNotifications.readPreview(context, Intent(sdk).putExtra("google.message_id", Bundle())))
        assertNull(AndroidRemoteNotifications.readPreview(context, Intent(sdk).putExtra("preview_id", "x".repeat(10_000))))
    }
}
