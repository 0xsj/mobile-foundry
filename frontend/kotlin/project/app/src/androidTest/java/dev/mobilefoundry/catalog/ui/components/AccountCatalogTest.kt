package dev.mobilefoundry.catalog.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.mobilefoundry.ui.theme.FoundryTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AccountCatalogTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun click(text: String) { compose.onNode(hasText(text) and hasClickAction()).performScrollTo().performClick() }
    private fun confirm(text: String) { compose.onNode(hasText(text) and hasClickAction()).performClick() }
    private fun switchAccount(title: String) {
        compose.onNodeWithContentDescription("Switch account").performScrollTo().performClick()
        compose.onNode(hasText(title) and hasClickAction()).performClick()
    }
    @Test fun removalsAndPermissionValuesSurviveContextsRoutesThemesAndRestorationWithoutRestoringPrompts() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { FoundryTheme { ComponentCatalogScreen({}) } }
        click("Account"); click("Open account center")
        compose.onNodeWithContentDescription("Remove iPhone 17").performScrollTo().assertIsNotEnabled().performClick()
        compose.onNodeWithContentDescription("Remove MacBook Pro").performScrollTo().performClick()
        confirm("Keep device"); compose.onNodeWithText("Devices: 3").performScrollTo().assertExists()
        compose.onNodeWithContentDescription("Remove MacBook Pro").performScrollTo().performClick(); confirm("Remove preview device")
        compose.onNodeWithText("Devices: 2").performScrollTo().assertExists()
        switchAccount("Studio team"); compose.onNodeWithText("Devices: 3").performScrollTo().assertExists()
        switchAccount("Personal"); compose.onNodeWithText("Devices: 2").performScrollTo().assertExists()
        click("Try photo access"); confirm("Not now")
        compose.onNodeWithText("Preview: Ask").performScrollTo().assertExists()
        click("Try photo access"); confirm("Allow preview")
        switchAccount("Studio team"); compose.onNodeWithText("Preview: Allowed").performScrollTo().assertExists()
        switchAccount("Personal"); click("Preview profile")
        compose.onNodeWithContentDescription("Remove iPad Air").performScrollTo().performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Remove preview device").assertDoesNotExist()
        compose.onNodeWithText("Devices: 2").performScrollTo().assertExists()
        compose.onNodeWithContentDescription("Remove iPad Air").performScrollTo().performClick(); confirm("Remove preview device")
        compose.onNodeWithText("Devices: 1").performScrollTo().assertExists()
        click("Enable account actions")
        compose.onNodeWithContentDescription("Switch account").performScrollTo().assertIsNotEnabled().performClick()
        compose.onNode(hasText("Preview profile") and hasClickAction()).performScrollTo().assertIsNotEnabled().performClick()
        compose.onNodeWithText("Back to components").performClick(); click("Content"); click("Account")
        compose.onNodeWithText("Dark preview").performClick(); compose.onNodeWithText("Glass preview").performClick(); click("Open account center")
        compose.onNodeWithText("Active account: Personal").assertExists()
        compose.onNodeWithText("Profile previews: 1").performScrollTo().assertExists()
        compose.onNodeWithText("Preview: Allowed").performScrollTo().assertExists()
        compose.onNodeWithText("Devices: 1").performScrollTo().assertExists()
    }
    @Test fun changingContextOrAvailabilityDismissesPendingIntents() {
        var values by mutableStateOf(AccountValues())
        compose.setContent { FoundryTheme { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { AccountContent(values, { values = it }) } } }
        compose.onNodeWithContentDescription("Remove MacBook Pro").performScrollTo().performClick()
        compose.runOnIdle { values = values.select("studio") }
        compose.onNodeWithText("Remove preview device").assertDoesNotExist()
        assertEquals(3, values.sessions.size)
        click("Try photo access")
        compose.runOnIdle { values = values.copy(enabled = false) }
        compose.onNodeWithText("Allow preview").assertDoesNotExist()
        assertEquals(PermissionScenario.ASK, values.permission)
        compose.runOnIdle { values = values.copy(enabled = true).setPermission(PermissionScenario.DENIED) }
        click("Preview settings")
        assertEquals(1, values.settingsPreviews)
        assertEquals(PermissionScenario.DENIED, values.permission)
    }
}
