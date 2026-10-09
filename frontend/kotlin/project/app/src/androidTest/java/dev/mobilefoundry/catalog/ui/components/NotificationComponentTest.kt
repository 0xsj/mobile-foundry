package dev.mobilefoundry.catalog.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.*
import dev.mobilefoundry.ui.components.display.countbadge.CountBadge
import dev.mobilefoundry.ui.components.forms.button.ActionButton
import dev.mobilefoundry.ui.components.patterns.notificationrow.NotificationRow
import dev.mobilefoundry.ui.theme.FoundryTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class NotificationComponentTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun suppliedNarrationPreservesOpenRoleAndSeparateActionEligibility() {
        var enabled by mutableStateOf(true)
        var unread by mutableStateOf(true)
        var opens = 0; var readActions = 0
        compose.setContent { FoundryTheme { Column {
            CountBadge("99+", "128 unread updates")
            NotificationRow("Review", "Ready to view", "Now", if (unread) "Unread" else "Read", unread,
                "Review, ready to view, now, ${if (unread) "unread" else "read"}", { opens++ }, enabled = enabled,
                leading = { Text("Decorative artwork") }, actions = { ActionButton({ unread = false; readActions++ }) { Text("Mark read") } })
        } } }
        compose.onNodeWithContentDescription("128 unread updates").assertHasNoClickAction()
        compose.onNodeWithText("Decorative artwork").assertDoesNotExist()
        val row = compose.onNodeWithContentDescription("Review, ready to view, now, unread")
        row.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).performClick()
        compose.runOnIdle { enabled = false }
        row.assertIsNotEnabled().performClick()
        compose.onNodeWithText("Mark read").assertIsEnabled().performClick()
        compose.onNodeWithContentDescription("Review, ready to view, now, read").assertIsNotEnabled()
        assertEquals(1, opens); assertEquals(1, readActions)
    }
    @Test fun narrowLargeTextRTLLayoutKeepsActionsReadableAndIndependent() {
        var fontScale by mutableFloatStateOf(1f)
        var direction by mutableStateOf(LayoutDirection.Ltr)
        var height = 0; var actions = 0
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale), LocalLayoutDirection provides direction) { FoundryTheme {
                Column(Modifier.width(240.dp).verticalScroll(rememberScrollState()).testTag("inbox-probe")) {
                    CountBadge("99+", "128 unread updates")
                    NotificationRow("A new review is ready for your attention", "Readable copy wraps beside passive artwork and above independent actions.",
                        "A few minutes ago", "Unread", true, "Supplied update, a few minutes ago, unread", {},
                        Modifier.onSizeChanged { height = it.height }, leading = { Text("Decorative artwork", Modifier.size(40.dp)) }, actions = {
                            ActionButton({ actions++ }, Modifier.testTag("read")) { Text("Mark this update as read") }
                            ActionButton({ actions++ }, Modifier.testTag("archive")) { Text("Archive this update") }
                        })
                }
            } }
        }
        compose.waitForIdle(); val normal = height
        compose.runOnIdle { fontScale = 2f; direction = LayoutDirection.Rtl }
        compose.waitForIdle(); assertTrue(height > normal + 80)
        val viewport = compose.onNodeWithTag("inbox-probe").fetchSemanticsNode().boundsInRoot
        for (tag in listOf("read", "archive")) {
            compose.onNodeWithTag(tag).performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
            val bounds = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
            assertTrue(bounds.left >= viewport.left - 1 && bounds.right <= viewport.right + 1)
        }
        assertEquals(2, actions)
    }
}
