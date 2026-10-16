package dev.mobilefoundry.product.screens

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import dev.mobilefoundry.product.R

@Composable
fun TodayScreen(displayName: String) {
    ProductPage(displayName, "Today", R.drawable.ic_tab_home) { Text("A fresh start for your day.") }
}
