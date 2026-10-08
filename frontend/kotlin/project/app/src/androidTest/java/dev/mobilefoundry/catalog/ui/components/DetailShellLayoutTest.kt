package dev.mobilefoundry.catalog.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.mobilefoundry.ui.components.display.keyvalue.KeyValueRow
import dev.mobilefoundry.ui.components.forms.button.ActionButton
import dev.mobilefoundry.ui.components.patterns.actionbar.ActionBar
import dev.mobilefoundry.ui.components.shells.detailshell.DetailShell
import dev.mobilefoundry.ui.theme.FoundryTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class DetailShellLayoutTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun measuredShellKeepsHeaderAndActionsOutsideScrollAtLargeText() {
        var fontScale by mutableFloatStateOf(1f)
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
                FoundryTheme {
                    DetailShell(Modifier.requiredSize(300.dp, 500.dp).testTag("viewport"), header = {
                        Text("Detail heading", Modifier.height(70.dp).testTag("header"))
                    }, content = {
                        Column(Modifier.fillMaxSize().testTag("body").verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            repeat(20) { index ->
                                KeyValueRow("Record $index", "A long readable value for this item", Modifier.testTag("record $index"))
                            }
                        }
                    }, actions = {
                        ActionBar(Modifier.testTag("footer"), summary = "Review before continuing") {
                            ActionButton({}) { Text("Continue") }
                        }
                    })
                }
            }
        }
        fun bounds(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        val footer = bounds("footer")
        val rowHeight = bounds("record 0").height
        compose.onNodeWithText("Record 19").performScrollTo().assertIsDisplayed()
        assertEquals(footer.top, bounds("footer").top, 1f)
        assertTrue(bounds("header").bottom <= bounds("body").top + 1)
        assertTrue(bounds("body").bottom <= bounds("footer").top + 1)
        compose.runOnIdle { fontScale = 2f }
        compose.onNodeWithText("Record 0").performScrollTo().assertIsDisplayed()
        assertTrue("Readable copy grows rather than truncates", bounds("record 0").height > rowHeight)
        assertTrue(bounds("body").height > 0)
        assertTrue(bounds("body").bottom <= bounds("footer").top + 1)
        assertTrue(bounds("footer").bottom <= bounds("viewport").bottom + 1)
        compose.onNodeWithText("Continue").assertIsDisplayed()
    }
}
