package dev.mobilefoundry.product

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ProductEntryTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
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
