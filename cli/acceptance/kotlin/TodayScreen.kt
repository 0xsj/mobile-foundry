package dev.mobilefoundry.product.screens

import androidx.compose.material3.Text
import androidx.compose.runtime.*
import dev.mobilefoundry.product.*
import dev.mobilefoundry.product.R
import dev.mobilefoundry.query.value
import dev.mobilefoundry.ui.components.forms.button.ActionButton

@Composable
fun TodayScreen(displayName: String) {
    val model = remember { CounterModel(MemoryCounterService()) }
    ProductPage(displayName, "Today", R.drawable.ic_tab_home) {
        Text("A fresh start for your day.")
        Text("Counter: ${model.query.value ?: 0}")
        model.mutation.value?.let { Text("Confirmed: $it") }
        ActionButton(onClick = model::increment) { Text("Increment") }
    }
}
