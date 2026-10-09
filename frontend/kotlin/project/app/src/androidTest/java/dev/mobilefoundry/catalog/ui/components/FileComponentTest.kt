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
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.*
import dev.mobilefoundry.ui.components.display.filetypemark.FileTypeMark
import dev.mobilefoundry.ui.components.forms.button.ActionButton
import dev.mobilefoundry.ui.components.patterns.treerow.TreeDisclosure
import dev.mobilefoundry.ui.components.patterns.treerow.TreeRow
import dev.mobilefoundry.ui.theme.FoundryTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class FileComponentTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun disclosureOpeningAndSlotActionsHaveIndependentNativeEligibility() {
        var selected by mutableStateOf(false); var enabled by mutableStateOf(true); var expanded by mutableStateOf(false)
        var discloseEnabled by mutableStateOf(true)
        var opens = 0; var toggles = 0; var favorites = 0
        compose.setContent { FoundryTheme { Column {
            TreeRow("References", "Open References", { selected = true; opens++ }, selected = selected, enabled = enabled,
                disclosure = TreeDisclosure(expanded, "Toggle References", if (expanded) "Expanded" else "Collapsed", discloseEnabled) { expanded = !expanded; toggles++ },
                leading = { FileTypeMark("DIR", "Folder artwork") }, actions = { ActionButton({ favorites++ }) { Text("Favorite References") } })
            FileTypeMark("PNG", "PNG image format")
            TreeRow("Leaf", "Open leaf", {}, enabled = false)
        } } }
        compose.onNodeWithContentDescription("PNG image format").assertHasNoClickAction()
        compose.onNodeWithContentDescription("Folder artwork").assertDoesNotExist()
        compose.onNodeWithContentDescription("Open References").assertIsNotSelected().performClick().assertIsSelected()
        compose.runOnIdle { enabled = false }
        compose.onNodeWithContentDescription("Open References").assertIsNotEnabled().performClick()
        compose.onNodeWithContentDescription("Toggle References").assertIsEnabled().performClick()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Expanded"))
        compose.onNodeWithText("Favorite References").assertIsEnabled().performClick()
        compose.runOnIdle { discloseEnabled = false }
        compose.onNodeWithContentDescription("Toggle References").assertIsNotEnabled().performClick()
        compose.onNodeWithContentDescription("Open leaf").assertIsNotEnabled()
        assertEquals(1, opens); assertEquals(1, toggles); assertEquals(1, favorites)
    }
    @Test fun extremeDepthIsCappedAndLargeTextRTLTargetsFitNarrowBounds() {
        var fontScale by mutableFloatStateOf(1f); var direction by mutableStateOf(LayoutDirection.Ltr)
        var height = 0
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale), LocalLayoutDirection provides direction) { FoundryTheme {
                Column(Modifier.width(240.dp).verticalScroll(rememberScrollState()).testTag("files-probe")) {
                    TreeRow("A detailed reference image for the next project", "Open reference image", {}, Modifier.onSizeChanged { height = it.height },
                        subtitle = "Supplied metadata grows vertically", depth = Int.MAX_VALUE, selected = true,
                        disclosure = TreeDisclosure(true, "Collapse references", "Expanded") {},
                        leading = { FileTypeMark("PNG", "PNG artwork") }, actions = { ActionButton({}) { Text("Favorite reference image") } })
                }
            } }
        }
        compose.waitForIdle(); val normal = height
        val ltr = compose.onNodeWithText("Favorite reference image").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle { fontScale = 2f; direction = LayoutDirection.Rtl }
        compose.waitForIdle(); assertTrue(height > normal + 80)
        val viewport = compose.onNodeWithTag("files-probe").fetchSemanticsNode().boundsInRoot
        for (label in listOf("Collapse references", "Open reference image")) {
            compose.onNodeWithContentDescription(label).performScrollTo().assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
            val bounds = compose.onNodeWithContentDescription(label).fetchSemanticsNode().boundsInRoot
            assertTrue(bounds.left >= viewport.left - 1 && bounds.right <= viewport.right + 1)
        }
        compose.onNodeWithText("Favorite reference image").performScrollTo().assertHeightIsAtLeast(48.dp)
        val rtl = compose.onNodeWithText("Favorite reference image").fetchSemanticsNode().boundsInRoot
        assertTrue(rtl.left < ltr.left && rtl.right < viewport.right)
    }
}
