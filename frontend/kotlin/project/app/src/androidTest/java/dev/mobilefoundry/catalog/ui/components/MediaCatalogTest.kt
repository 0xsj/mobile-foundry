package dev.mobilefoundry.catalog.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.mobilefoundry.ui.theme.FoundryTheme
import org.junit.Rule
import org.junit.Test

class MediaCatalogTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun click(text: String) { compose.onNode(hasText(text) and hasClickAction()).performScrollTo().performClick() }
    private fun icon(label: String) = compose.onNodeWithContentDescription(label)
    private fun iconClick(label: String) { icon(label).performScrollTo().performClick() }
    private fun open() { click("Media"); click("Open media preview") }
    private fun page(title: String, position: Int) {
        compose.onNodeWithText("Selected: $title").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Study $position of 3").assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
    }
    @Test fun nativePagingRatingAndOverlayActionsKeepIndependentItemValues() {
        compose.setContent { FoundryTheme { ComponentCatalogScreen({}) } }
        open()
        icon("Previous study").performScrollTo().assertIsNotEnabled()
        iconClick("Rate Orbit study 4 of 5")
        icon("Rate Orbit study 4 of 5").assertIsSelected()
        compose.onNodeWithTag("media-carousel").performScrollTo()
        icon("Favorite Orbit study").performClick()
        iconClick("Next study"); page("Field study", 2)
        compose.onNodeWithText("Rating: 0 of 5").performScrollTo().assertIsDisplayed()
        iconClick("Rate Field study 2 of 5")
        compose.onNodeWithTag("media-carousel").performScrollTo().performTouchInput { swipeLeft() }
        page("Arc study", 3)
        icon("Next study").assertIsNotEnabled()
        iconClick("Previous study"); page("Field study", 2)
        compose.onNodeWithText("Rating: 2 of 5").performScrollTo().assertIsDisplayed()
        iconClick("Previous study"); page("Orbit study", 1)
        compose.onNodeWithText("Rating: 4 of 5").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("media-carousel").performScrollTo()
        icon("Remove favorite Orbit study").assertIsDisplayed()
        click("Clear study rating")
        compose.onNodeWithText("Rating: 0 of 5").assertIsDisplayed()
        compose.onNodeWithText("Clear study rating").assertIsNotEnabled()
    }
    @Test fun mediaValuesAndNativePositionSurviveBackFamilyThemeAndRestoration() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { FoundryTheme { ComponentCatalogScreen({}) } }
        open(); iconClick("Next study"); iconClick("Rate Field study 3 of 5")
        compose.onNodeWithTag("media-carousel").performScrollTo()
        icon("Favorite Field study").performClick()
        click("Use selected study")
        compose.onNodeWithText("Used previews: 1").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Back to components").performClick()
        click("Content"); click("Media")
        compose.onNodeWithText("Dark preview").performClick()
        compose.onNodeWithText("Glass preview").performClick()
        click("Open media preview")
        restoration.emulateSavedInstanceStateRestore()
        page("Field study", 2)
        compose.onNodeWithText("Rating: 3 of 5").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Favorite study").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Used previews: 1").performScrollTo().assertIsDisplayed()
    }
    @Test fun disabledMediaRejectsSwipesRatingsAndActions() {
        compose.setContent { FoundryTheme { ComponentCatalogScreen({}) } }
        click("Media"); click("Enable media controls"); click("Open media preview")
        icon("Next study").performScrollTo().assertIsNotEnabled()
        icon("Rate Orbit study 1 of 5").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("media-carousel").performScrollTo().performTouchInput { swipeLeft() }
        page("Orbit study", 1)
        compose.onNodeWithTag("media-carousel").performScrollTo()
        icon("Favorite Orbit study").assertIsNotEnabled()
        compose.onNodeWithText("Use selected study").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Used previews: 0").performScrollTo().assertIsDisplayed()
    }
}
