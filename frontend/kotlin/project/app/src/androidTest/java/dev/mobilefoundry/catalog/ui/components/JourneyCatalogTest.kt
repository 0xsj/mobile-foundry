package dev.mobilefoundry.catalog.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.mobilefoundry.ui.theme.FoundryTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class JourneyCatalogTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun click(text: String, scroll: Boolean = true) {
        val node = compose.onNode(hasText(text) and hasClickAction())
        if (scroll) node.performScrollTo()
        node.performClick()
    }
    private fun status(title: String, value: String) = compose.onNodeWithText(title).assert(
        SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, value))

    @Test fun nativeSecureInputUpdatesChecklistAndAccountActionWithoutRestoringPassword() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { FoundryTheme { ComponentCatalogScreen(onBack = {}) } }
        click("Journeys")
        status("Password entered", "Needed")
        val password = compose.onNode(hasText("Preview password") and hasSetTextAction())
        password.performScrollTo().assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
        password.performTextInput("example-input")
        click("Done editing password")
        status("Password entered", "Satisfied")
        compose.onNodeWithText("About you").performScrollTo().performTextInput("Hello\nfrom Atlas")
        click("Done editing")
        status("Profile note added", "Satisfied")
        click("Open account preview")
        click("Continue account preview")
        compose.onNodeWithText("Account previews: 1").assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Continue account preview").performScrollTo().assertIsNotEnabled()
        status("Password entered", "Needed")
        status("Profile note added", "Satisfied")
        compose.onNodeWithText("Account previews: 1").performScrollTo().assertIsDisplayed()
        click("Back to components", scroll = false)
        compose.onNodeWithText("About you").performScrollTo().assertTextContains("Hello\nfrom Atlas")
    }

    @Test fun onboardingGatesFirstStepRetainsDraftAndFinishesOnlyOnExplicitAction() {
        compose.setContent { FoundryTheme { ComponentCatalogScreen(onBack = {}) } }
        click("Journeys"); click("Open onboarding preview")
        compose.onNodeWithText("Next step").assertIsNotEnabled()
        compose.onNodeWithText("Previous step").assertIsNotEnabled()
        status("Profile", "Current"); status("Review", "Upcoming")
        compose.onNodeWithText("Profile introduction").performScrollTo().performTextInput("A small studio\nfor new ideas")
        click("Done editing introduction")
        val footer = compose.onNodeWithText("Next step").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        compose.onNodeWithText("Profile").performScrollTo()
        assertEquals(footer.top, compose.onNodeWithText("Next step").fetchSemanticsNode().boundsInRoot.top, 1f)
        click("Next step", scroll = false)
        status("Profile", "Completed"); status("Preferences", "Current")
        click("Include activity updates")
        click("Previous step", scroll = false)
        compose.onNodeWithText("Profile introduction").performScrollTo().assertTextContains("A small studio\nfor new ideas")
        click("Next step", scroll = false)
        compose.onNodeWithText("Include activity updates").performScrollTo().assertIsOff()
        click("Next step", scroll = false)
        status("Review", "Current")
        compose.onNodeWithText("A small studio\nfor new ideas").performScrollTo().assertIsDisplayed()
        click("Finish preview", scroll = false)
        compose.onNodeWithText("Finish preview").assertIsNotEnabled()
        status("Review", "Completed")
        click("Restart steps", scroll = false)
        compose.onNodeWithText("Profile introduction").performScrollTo().assertTextContains("A small studio\nfor new ideas")
        click("Back to components", scroll = false)
        compose.onNodeWithText("Finished previews: 1").performScrollTo().assertIsDisplayed()
    }

    @Test fun inputDisablingAndJourneyProgressSurviveFamilyThemeAndSavedStateChanges() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { FoundryTheme { ComponentCatalogScreen(onBack = {}) } }
        click("Journeys")
        click("Enable preview inputs")
        compose.onNodeWithText("Preview password").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("About you").performScrollTo().assertIsNotEnabled()
        click("Content"); click("Journeys")
        compose.onNodeWithText("Enable preview inputs").performScrollTo().assertIsOff()
        click("Enable preview inputs")
        compose.onNodeWithText("About you").performScrollTo().performTextInput("An Atlas note")
        click("Done editing")
        compose.onNodeWithText("Dark preview").performClick()
        compose.onNodeWithText("Glass preview").performClick()
        click("Open onboarding preview")
        click("Next step", scroll = false)
        restoration.emulateSavedInstanceStateRestore()
        status("Preferences", "Current")
        click("Previous step", scroll = false)
        compose.onNodeWithText("Profile introduction").performScrollTo().assertTextContains("An Atlas note")
    }
}
