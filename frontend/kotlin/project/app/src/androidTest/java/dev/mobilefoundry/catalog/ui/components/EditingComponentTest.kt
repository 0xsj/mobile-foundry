package dev.mobilefoundry.catalog.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.mobilefoundry.ui.components.forms.removablechip.RemovableChip
import dev.mobilefoundry.ui.components.patterns.selectionrow.SelectionRow
import dev.mobilefoundry.ui.components.patterns.swipeactionrow.SwipeAction
import dev.mobilefoundry.ui.components.patterns.swipeactionrow.SwipeActionRow
import dev.mobilefoundry.ui.components.layout.wrap.WrapLayout
import dev.mobilefoundry.ui.theme.FoundryTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class EditingComponentTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun wrappingLargeLabelsKeepsIndependentTargetsAndSelectionSemantics() {
        var narrow by mutableStateOf(false)
        var removes = 0
        var selected by mutableStateOf(false)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                FoundryTheme {
                    Column(Modifier.width(if (narrow) 230.dp else 390.dp)) {
                        WrapLayout {
                            RemovableChip("A long review tag", "Remove review", { removes++ })
                            RemovableChip("Sketch", "Remove sketch", { removes++ })
                        }
                        SelectionRow("A selectable library item", selected, if (selected) "Selected" else "Not selected", { selected = !selected })
                    }
                }
            }
        }
        compose.onNodeWithContentDescription("Remove review").performClick()
        compose.runOnIdle { narrow = true }
        val first = compose.onNodeWithContentDescription("Remove review").fetchSemanticsNode().boundsInRoot
        val second = compose.onNodeWithContentDescription("Remove sketch").fetchSemanticsNode().boundsInRoot
        check(second.top >= first.bottom)
        check(first.height >= 48 * compose.density.density && second.height >= 48 * compose.density.density)
        compose.onNodeWithContentDescription("Remove sketch").assertIsDisplayed().performClick()
        compose.onNode(hasText("A selectable library item") and isToggleable()).performClick().assertIsOn()
        assertEquals(2, removes)
    }
    @Test fun accessibilitySwipeActionsAreExplicitAndDisabledGesturesDoNotDispatch() {
        var enabled by mutableStateOf(true)
        var actions = 0
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            FoundryTheme {
                SwipeActionRow("Example item actions", Modifier.width(300.dp).testTag("swipe-probe"),
                    leading = SwipeAction("Archive example", { actions++ }), enabled = enabled) {
                    SelectionRow("Example item", false, "Not selected", {}, enabled = enabled)
                }
            }
        }
        val availableActions = compose.onNodeWithTag("swipe-probe").fetchSemanticsNode().config[SemanticsActions.CustomActions]
        compose.runOnIdle { check(availableActions.single().action()) }
        assertEquals(1, actions)
        compose.runOnIdle { enabled = false }
        compose.onNodeWithTag("swipe-probe").performTouchInput { swipeRight() }
        restoration.emulateSavedInstanceStateRestore()
        assertEquals(1, actions)
        check(compose.onNodeWithTag("swipe-probe").fetchSemanticsNode().config[SemanticsActions.CustomActions].isEmpty())
    }
}
