package dev.mobilefoundry.ui.components.navigation.tabs

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/** Unique, nonempty options containing the selected value. Caller owns destination state. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> Tabs(title: String, options: List<T>, selected: T, onSelect: (T) -> Unit,
             modifier: Modifier = Modifier, label: (T) -> String) {
    require(options.isNotEmpty() && options.distinct().size == options.size && selected in options)
    PrimaryScrollableTabRow(selectedTabIndex = options.indexOf(selected),
        modifier = modifier.semantics { contentDescription = title }) {
        options.forEach { option ->
            Tab(selected = selected == option, onClick = { onSelect(option) }, text = { Text(label(option)) })
        }
    }
}
