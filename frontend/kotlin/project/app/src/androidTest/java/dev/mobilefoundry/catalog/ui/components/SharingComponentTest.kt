package dev.mobilefoundry.catalog.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.*
import dev.mobilefoundry.ui.components.forms.button.ActionButton
import dev.mobilefoundry.ui.components.patterns.memberrow.MemberRow
import dev.mobilefoundry.ui.components.patterns.sharelinkcard.ShareLinkCard
import dev.mobilefoundry.ui.theme.FoundryTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SharingComponentTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun passiveIdentityAndLinkKeepAccessAndActionSlotsIndependent() {
        var enabled by mutableStateOf(true); var active by mutableStateOf(true)
        var roles = 0; var removals = 0; var copies = 0
        compose.setContent { FoundryTheme { Column {
            MemberRow("Jamie Park", "Jamie Park, jamie@example.test", detail = "jamie@example.test", avatar = { Text("Decorative avatar") },
                access = { ActionButton({ roles++ }, enabled = enabled) { Text("Change access") } },
                actions = { ActionButton({ removals++ }) { Text("Remove member") } })
            ShareLinkCard("Workspace link", if (active) SharingValues.LINK else null, "Link is unavailable",
                status = { Text(if (active) "Can view" else "Off") }, actions = { ActionButton({ copies++ }, enabled = active) { Text("Copy link") } })
        } } }
        compose.onNodeWithContentDescription("Jamie Park, jamie@example.test").assertHasNoClickAction()
        compose.onNodeWithText("Decorative avatar").assertDoesNotExist()
        compose.onNodeWithText("Change access").performClick()
        compose.onNodeWithText("Copy link").performClick()
        compose.runOnIdle { enabled = false; active = false }
        compose.onNodeWithText("Change access").assertIsNotEnabled().performClick()
        compose.onNodeWithText("Remove member").assertIsEnabled().performClick()
        compose.onNodeWithText("Copy link").assertIsNotEnabled().performClick()
        compose.onNodeWithText(SharingValues.LINK).assertDoesNotExist()
        compose.onNodeWithText("Link is unavailable").assertHasNoClickAction()
        assertEquals(1, roles); assertEquals(1, removals); assertEquals(1, copies)
    }
    @Test fun identityAndLinkGrowAtLargeTextWithNativeTargetsInsideNarrowRTLBounds() {
        var fontScale by mutableFloatStateOf(1f); var direction by mutableStateOf(LayoutDirection.Ltr)
        var memberHeight = 0; var linkHeight = 0; var mark = Rect.Zero
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale), LocalLayoutDirection provides direction) { FoundryTheme {
                Column(Modifier.width(240.dp).verticalScroll(rememberScrollState()).testTag("sharing-probe")) {
                    MemberRow("A teammate with a detailed display name", "Supplied teammate identity", Modifier.onSizeChanged { memberHeight = it.height },
                        detail = "teammate@example.test", avatar = { Box(Modifier.size(24.dp).onGloballyPositioned { mark = it.boundsInRoot() }) },
                        access = { ActionButton({}) { Text("Change member access") } }, actions = { ActionButton({}) { Text("Remove teammate") } })
                    ShareLinkCard("Workspace link", SharingValues.LINK, "Link is unavailable", Modifier.onSizeChanged { linkHeight = it.height },
                        detail = "Anyone with this supplied link can view", actions = { ActionButton({}) { Text("Copy workspace link") } })
                }
            } }
        }
        compose.waitForIdle(); val normalMember = memberHeight; val normalLink = linkHeight; val ltrMark = mark
        compose.runOnIdle { fontScale = 2f; direction = LayoutDirection.Rtl }
        compose.waitForIdle(); assertTrue(memberHeight > normalMember + 60 && linkHeight > normalLink + 60)
        assertTrue(mark.left > ltrMark.left + 100)
        val viewport = compose.onNodeWithTag("sharing-probe").fetchSemanticsNode().boundsInRoot
        for (label in listOf("Change member access", "Remove teammate", "Copy workspace link")) {
            compose.onNodeWithText(label).performScrollTo().assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
            val bounds = compose.onNodeWithText(label).fetchSemanticsNode().boundsInRoot
            assertTrue(bounds.left >= viewport.left - 1 && bounds.right <= viewport.right + 1)
        }
        compose.onNodeWithText(SharingValues.LINK).performScrollTo().assertExists()
    }
}
