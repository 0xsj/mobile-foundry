package dev.mobilefoundry.catalog.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.*
import dev.mobilefoundry.ui.components.layout.divider.SectionDivider
import dev.mobilefoundry.ui.components.layout.stack.HorizontalStack
import dev.mobilefoundry.ui.components.layout.stack.VerticalStack
import dev.mobilefoundry.ui.components.navigation.tabbar.TabBar
import dev.mobilefoundry.ui.components.navigation.tabbar.TabItem
import dev.mobilefoundry.ui.components.shells.appshell.AppShell
import dev.mobilefoundry.ui.theme.FoundryTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ShellComponentTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun tokenSpacingOverrideAndRTLRespectBoundedShellSlots() {
        var spacing by mutableStateOf<Dp?>(null); var direction by mutableStateOf(LayoutDirection.Ltr)
        val frames = mutableMapOf<String, Rect>(); var density = 1f
        fun frame(id: String) = Modifier.onGloballyPositioned { frames[id] = it.boundsInRoot() }
        compose.setContent { CompositionLocalProvider(LocalLayoutDirection provides direction) { FoundryTheme {
            density = androidx.compose.ui.platform.LocalDensity.current.density
            AppShell(Modifier.width(240.dp).height(500.dp).then(frame("viewport")), background = { Text("Passive background") }, content = {
                VerticalStack(Modifier.fillMaxSize().then(frame("content")), spacing = spacing) {
                    Box(Modifier.size(40.dp, 20.dp).then(frame("first")))
                    Box(Modifier.size(40.dp, 20.dp).then(frame("second")))
                    HorizontalStack(Modifier.fillMaxWidth(), spacing = spacing) {
                        Box(Modifier.size(40.dp, 20.dp).then(frame("left")))
                        Box(Modifier.size(40.dp, 20.dp).then(frame("right")))
                    }
                    SectionDivider(Modifier.then(frame("divider")), inset = 8.dp, thickness = 2.dp)
                }
            }, navigation = { Box(Modifier.fillMaxWidth().height(60.dp).then(frame("navigation"))) })
        } } }
        compose.waitForIdle(); assertEquals(8, frames.size)
        fun gap(a: String, b: String, vertical: Boolean) = if (vertical) (frames.getValue(b).top - frames.getValue(a).bottom) / density else (frames.getValue(b).left - frames.getValue(a).right) / density
        assertEquals(12f, gap("first", "second", true), 1f); assertEquals(8f, gap("left", "right", false), 1f)
        assertEquals(frames.getValue("navigation").top, frames.getValue("content").bottom, 1f)
        assertEquals(frames.getValue("viewport").bottom, frames.getValue("navigation").bottom, 1f)
        assertEquals(440f, frames.getValue("content").height / density, 1f)
        assertEquals(2f, frames.getValue("divider").height / density, 1f)
        compose.onNodeWithText("Passive background").assertDoesNotExist()
        compose.runOnIdle { spacing = 24.dp }; compose.waitForIdle()
        assertEquals(24f, gap("first", "second", true), 1f); assertEquals(24f, gap("left", "right", false), 1f)
        compose.runOnIdle { direction = LayoutDirection.Rtl }; compose.waitForIdle()
        assertTrue(frames.getValue("left").left > frames.getValue("right").left)
        assertTrue(frames.getValue("left").right <= frames.getValue("viewport").right + 1)
    }
    @Test fun nativeTabSelectionEmitsOneChangeWhilePageActionsStayIndependent() {
        var selected by mutableStateOf("a"); var changes = 0; var markers = 0
        compose.setContent { FoundryTheme {
            AppShell(Modifier.fillMaxSize(), background = {}, content = {
                androidx.compose.material3.TextButton({ markers++ }) { Text("Mark page") }
            }, navigation = {
                TabBar(listOf(TabItem("a", "Overview") { Text("A") }, TabItem("b", "Activity") { Text("B") }), selected, { selected = it; changes++ })
            })
        } }
        compose.onNode(hasText("Overview") and hasClickAction()).assertIsSelected().performClick()
        assertEquals(0, changes)
        compose.onNode(hasText("Activity") and hasClickAction()).assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp).performClick().assertIsSelected()
        compose.onNodeWithText("Mark page").performClick()
        compose.onNode(hasText("Activity") and hasClickAction()).performClick()
        assertEquals(1, changes); assertEquals(1, markers)
        compose.runOnIdle { selected = "a" }
        compose.onNode(hasText("Overview") and hasClickAction()).assertIsSelected()
    }
}
