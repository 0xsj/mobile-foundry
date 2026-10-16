package dev.mobilefoundry.product.screens

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import dev.mobilefoundry.product.R

@Composable
fun ActivityScreen() {
    ProductPage("Activity", "Keep up with what matters", R.drawable.ic_tab_activity) { Text("You're all caught up.") }
}
