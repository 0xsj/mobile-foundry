package dev.mobilefoundry.product

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ProductEntryTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun select(title: String) = compose.onNode(hasText(title) and isSelectable()).performClick()

    @Test fun cleanEntryTabsAndMaterialChoiceSurviveRecreation() {
        compose.onNodeWithText(compose.activity.getString(R.string.app_name)).assertIsDisplayed()
        compose.onNode(hasText("Today") and isSelectable()).assertIsSelected()
        select("Library"); compose.onNodeWithText("Your collection starts here.").assertIsDisplayed()
        select("Activity"); compose.onNodeWithText("You're all caught up.").assertIsDisplayed()
        select("Account")
        compose.onNodeWithText("Glass").assertIsOn().performClick().assertIsOff()
        compose.onNode(hasText("Account") and isSelectable()).assertIsSelected()
        compose.activityRule.scenario.recreate()
        compose.waitUntil(8_000) { compose.onAllNodesWithText("Your preferences").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasText("Account") and isSelectable()).assertIsSelected()
        compose.onNodeWithText("Glass").assertIsOff()
        select("Today"); compose.onNodeWithText("A fresh start for your day.").assertIsDisplayed()
        select("Account"); compose.onNodeWithText("Glass").assertIsOff()
    }

    @Test fun runtimeBrandingAndPermissionsBelongToTheProduct() {
        val activity = compose.activity
        assertNotEquals("dev.mobilefoundry.catalog", activity.packageName)
        assertEquals(activity.getString(R.string.app_name), activity.applicationInfo.loadLabel(activity.packageManager).toString())
        @Suppress("DEPRECATION")
        val permissions = activity.packageManager.getPackageInfo(activity.packageName, android.content.pm.PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
        assertFalse(permissions.contains("android.permission.CAMERA"))
        assertFalse(permissions.contains("android.permission.INTERNET"))
    }
}
