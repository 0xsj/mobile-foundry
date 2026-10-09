package dev.mobilefoundry.catalog.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.mobilefoundry.ui.components.forms.iconaction.IconAction
import dev.mobilefoundry.ui.components.forms.rating.RatingField
import dev.mobilefoundry.ui.components.navigation.pageindicator.PageIndicator
import dev.mobilefoundry.ui.components.patterns.mediaoverlay.MediaOverlay
import dev.mobilefoundry.ui.theme.FoundryTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MediaComponentTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun passiveArtworkAndPositionDoNotHideIndependentLargeTextActions() {
        var rating by mutableIntStateOf(0)
        var actions = 0
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                FoundryTheme {
                    Column(Modifier.width(240.dp)) {
                        MediaOverlay(Modifier.fillMaxWidth().height(220.dp), artwork = { Text("Decorative artwork") }, overlay = {
                            Text("A small collection")
                            IconAction("Inspect art", { actions++ }) { Text("+") }
                        })
                        PageIndicator(3, 1, "Study two of three")
                        RatingField("Rate this study", rating, { rating = it }, "Rating: $rating", { "Choose $it of five" })
                    }
                }
            }
        }
        compose.onNodeWithText("Decorative artwork").assertDoesNotExist()
        compose.onNodeWithText("+").assertDoesNotExist()
        compose.onNodeWithContentDescription("Inspect art").performClick()
        assertEquals(1, actions)
        compose.onNodeWithContentDescription("Study two of three").assert(
            SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
        val choice = compose.onNodeWithContentDescription("Choose 5 of five")
        choice.assertIsDisplayed().performClick().assertIsSelected()
        assertEquals(5, rating)
        val bounds = choice.fetchSemanticsNode().boundsInRoot
        compose.onNodeWithContentDescription("Choose 1 of five").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.Selected, false))
        check(bounds.width >= 48 * compose.density.density && bounds.height >= 48 * compose.density.density)
    }
}
