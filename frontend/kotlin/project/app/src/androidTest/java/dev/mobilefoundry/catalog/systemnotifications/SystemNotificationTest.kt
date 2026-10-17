package dev.mobilefoundry.catalog.systemnotifications

import android.Manifest
import android.app.NotificationManager
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.test.platform.app.InstrumentationRegistry
import dev.mobilefoundry.catalog.MainActivity
import dev.mobilefoundry.catalog.R
import dev.mobilefoundry.services.LocalNotificationPreview
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class SystemNotificationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private fun click(text: String,scroll: Boolean = false) {
        val node = compose.onNode(hasText(text) and hasClickAction());if (scroll) node.performScrollTo();node.performClick()
    }
    private fun tagText(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().firstOrNull()?.config?.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }
    private fun clickSystemNode(find: (AccessibilityNodeInfo) -> AccessibilityNodeInfo?) {
        val end = SystemClock.uptimeMillis()+8_000
        while (SystemClock.uptimeMillis() < end) {
            val automation = instrumentation.uiAutomation
            var node = (automation.windows.mapNotNull { it.root } + listOfNotNull(automation.rootInActiveWindow)).firstNotNullOfOrNull(find)
            while (node != null) {
                if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return
                node = node.parent
            }
            SystemClock.sleep(100)
        }
        fail("Expected OS notification control was not available")
    }
    @Test fun realPermissionPostReplaceOpenAndOwnedClear() {
        val manager = context.getSystemService(NotificationManager::class.java)
        val automation = instrumentation.uiAutomation
        automation.serviceInfo = automation.serviceInfo.apply { flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS }
        fun shell(command: String) { android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use { it.readBytes() } }
        try {
            shell("cmd statusbar collapse")
            click("Studio");click("Open catalog");click("System notifications",scroll=true)
            compose.onNodeWithTag("notifications.allow").performScrollTo().performClick()
            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                clickSystemNode { it.findAccessibilityNodeInfosByViewId("com.android.permissioncontroller:id/permission_allow_button").firstOrNull() }
            }
            compose.waitUntil(8_000) { compose.onAllNodesWithTag("notifications.post").fetchSemanticsNodes().singleOrNull()?.config?.contains(SemanticsProperties.Disabled) == false }
            compose.onNodeWithTag("notifications.post").performScrollTo().performClick()
            compose.waitUntil(8_000) { tagText("notifications.accepted") != null }
            val first = tagText("notifications.accepted")!!.takeLast(36)
            compose.onNodeWithTag("notifications.post").performClick()
            compose.waitUntil(8_000) { tagText("notifications.accepted")?.takeLast(36)?.let { it != first } == true }
            val next = tagText("notifications.accepted")!!.takeLast(36)
            val preview = manager.activeNotifications.filter { it.tag == AndroidSystemNotifications.TAG }.single()
            assertEquals("Mobile notification preview",preview.notification.extras.getString("android.title"))
            if (Build.VERSION.SDK_INT >= 31) assertTrue(preview.notification.contentIntent.isImmutable)
            click("Back");click("Close catalog",scroll=true)
            preview.notification.contentIntent.send()
            compose.waitUntil(10_000) { tagText("notifications.opened") == "Opened preview: $next" }
            // The OS PendingIntent is a navigation hint; duplicate delivery adds no second route.
            preview.notification.contentIntent.send();compose.waitForIdle()
            compose.waitUntil(8_000) { compose.activityRule.scenario.state == androidx.lifecycle.Lifecycle.State.RESUMED }
            compose.activityRule.scenario.recreate()
            compose.waitUntil(8_000) { tagText("notifications.opened") == "Opened preview: $next" }
            assertNull(SystemNotificationComposition.opens.state.value.pending)
            val unrelated = NotificationCompat.Builder(context,AndroidSystemNotifications.CHANNEL).setSmallIcon(R.drawable.ic_notification_preview).setContentTitle("Unrelated test notification").build()
            manager.notify("catalog.test.unrelated",9,unrelated)
            compose.onNodeWithTag("notifications.clear").performScrollTo().performClick();compose.waitForIdle()
            assertTrue(manager.activeNotifications.none { it.tag == AndroidSystemNotifications.TAG })
            assertTrue(manager.activeNotifications.any { it.tag == "catalog.test.unrelated" })
            click("Back");compose.onNode(hasText("Mobile Foundry") and !hasClickAction()).assertExists()
        } finally { manager.cancel(AndroidSystemNotifications.TAG,1);manager.cancel("catalog.test.unrelated",9) }
    }
    @Test fun intentAdmissionRejectsForeignRoutesAndExtraAuthority() {
        val preview = LocalNotificationPreview.make(UUID.randomUUID().toString())!!
        val valid = AndroidSystemNotifications.previewIntent(context,preview)
        assertEquals(preview,AndroidSystemNotifications.readPreview(context,valid))
        assertNull(AndroidSystemNotifications.readPreview(context,android.content.Intent(valid).setAction("unowned")))
        assertNull(AndroidSystemNotifications.readPreview(context,android.content.Intent(valid).setData(Uri.parse(valid.dataString!!+"?account=foreign"))))
        assertNull(AndroidSystemNotifications.readPreview(context,android.content.Intent(valid).putExtra("account_id","untrusted")))
        assertNull(AndroidSystemNotifications.readPreview(context,android.content.Intent(valid).putExtra("notification_preview_id","../escape")))
    }
}
