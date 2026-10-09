package dev.mobilefoundry.catalog.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.*
import dev.mobilefoundry.ui.components.forms.button.ActionButton
import dev.mobilefoundry.ui.components.navigation.accountswitcher.*
import dev.mobilefoundry.ui.components.patterns.permissioncard.PermissionCard
import dev.mobilefoundry.ui.components.patterns.profileheader.ProfileHeader
import dev.mobilefoundry.ui.components.patterns.sessionrow.SessionRow
import dev.mobilefoundry.ui.theme.FoundryTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AccountComponentTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun option(title: String) = compose.onNode(hasText(title) and hasClickAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected))
    @Test fun nativeAccountMenuRetainsControlledSelectionAndGuardsDisabledOrCurrentChoices() {
        var selected by mutableStateOf<String?>("personal")
        var enabled by mutableStateOf(true)
        var options by mutableStateOf(AccountValues.options)
        val selections = mutableListOf<String>()
        compose.setContent { FoundryTheme { AccountSwitcher("Switch account", options, selected, "Choose an account", { selections += it }, enabled = enabled) } }
        compose.onNodeWithContentDescription("Switch account").performClick()
        option("Personal").assertIsSelected().performClick()
        assertTrue(selections.isEmpty())
        compose.onNodeWithContentDescription("Switch account").performClick()
        option("Invited workspace").assertIsNotEnabled().performClick()
        option("Studio team").performClick()
        assertEquals(listOf("studio"), selections)
        compose.onNodeWithContentDescription("Switch account").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Personal"))
        compose.runOnIdle { selected = "missing" }
        compose.onNodeWithContentDescription("Switch account").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Choose an account"))
        compose.onNodeWithContentDescription("Switch account").performClick()
        option("Personal").assertIsNotSelected()
        compose.runOnIdle { enabled = false }
        compose.onNode(hasText("Studio team") and hasClickAction()).assertDoesNotExist()
        compose.onNodeWithContentDescription("Switch account").assertIsNotEnabled().performClick()
        compose.runOnIdle { enabled = true; options = emptyList() }
        compose.onNodeWithContentDescription("Switch account").assertIsNotEnabled()
        assertEquals(listOf("studio"), selections)
    }
    @Test fun accountCopyGrowsAndActionsRemainIndependentAtNarrowWidthLargeTextAndRTL() {
        var fontScale by mutableFloatStateOf(1f)
        var direction by mutableStateOf(LayoutDirection.Ltr)
        var profileHeight = 0
        var actions = 0
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale), LocalLayoutDirection provides direction) { FoundryTheme {
                Column(Modifier.width(240.dp).verticalScroll(rememberScrollState()).testTag("account-probe")) {
                    ProfileHeader("Mira Chen and the studio", Modifier.onSizeChanged { profileHeight = it.height }, detail = "A longer account description for the preview",
                        avatar = { Text("Decorative avatar", Modifier.size(64.dp)) }, status = { Text("Team member") },
                        actions = { ActionButton({ actions++ }, Modifier.testTag("profile-action")) { Text("Preview profile") } })
                    SessionRow("Desktop workspace", "Last active yesterday", detail = "A longer browser description that wraps",
                        icon = { Text("Decorative device") }, status = { Text("Other device") },
                        actions = { ActionButton({ actions++ }, Modifier.testTag("session-action")) { Text("Remove device") } })
                    PermissionCard("Photos", "Choose a photo for an editor preview.", icon = { Text("Decorative photo") }, status = { Text("Ask") },
                        actions = { ActionButton({ actions++ }, Modifier.testTag("permission-action")) { Text("Try photo access") } })
                }
            } }
        }
        compose.waitForIdle(); val normal = profileHeight
        compose.runOnIdle { fontScale = 2f; direction = LayoutDirection.Rtl }
        compose.waitForIdle(); assertTrue(profileHeight > normal + 40)
        compose.onNodeWithText("Decorative avatar").assertDoesNotExist()
        compose.onNodeWithText("Decorative device").assertDoesNotExist()
        val viewport = compose.onNodeWithTag("account-probe").fetchSemanticsNode().boundsInRoot
        for (tag in listOf("profile-action", "session-action", "permission-action")) {
            compose.onNodeWithTag(tag).performScrollTo().assertHeightIsAtLeast(48.dp).assertHasClickAction().performClick()
            val action = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
            assertTrue(action.left >= viewport.left - 1f && action.right <= viewport.right + 1f)
        }
        assertEquals(3, actions)
    }
}
