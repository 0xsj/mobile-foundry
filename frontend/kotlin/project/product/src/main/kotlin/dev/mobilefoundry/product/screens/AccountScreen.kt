package dev.mobilefoundry.product.screens

import androidx.compose.runtime.Composable
import dev.mobilefoundry.product.R
import dev.mobilefoundry.ui.components.forms.toggle.ToggleField

@Composable
fun AccountScreen(glass: Boolean, onGlassChange: (Boolean) -> Unit) {
    ProductPage("Account", "Your preferences", R.drawable.ic_tab_account) {
        ToggleField("Glass", glass, onGlassChange, help = "Use translucent surfaces.")
    }
}
