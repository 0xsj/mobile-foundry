package dev.mobilefoundry.product.screens

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import dev.mobilefoundry.product.R

@Composable
fun LibraryScreen() {
    ProductPage("Library", "Saved for later", R.drawable.ic_tab_library) { Text("Your collection starts here.") }
}
