package dev.mobilefoundry.catalog.ui.main

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import dev.mobilefoundry.catalog.HealthCatalog
import dev.mobilefoundry.catalog.NotesCatalog
import dev.mobilefoundry.catalog.QueryCatalog
import dev.mobilefoundry.catalog.data.DefaultDataRepository
import dev.mobilefoundry.catalog.theme.FoundryCatalogTheme

@Composable
fun MainScreen(
  onItemClick: (NavKey) -> Unit,
  modifier: Modifier = Modifier,
  viewModel: MainScreenViewModel = viewModel { MainScreenViewModel(DefaultDataRepository()) },
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  Column(modifier) {
    Text("Mobile Foundry")
    Button(onClick = { onItemClick(HealthCatalog) }) { Text("HTTP health") }
    Button(onClick = { onItemClick(NotesCatalog) }) { Text("Notes service seam") }
    Button(onClick = { onItemClick(QueryCatalog) }) { Text("Async UI patterns") }
    when (state) {
      MainScreenUiState.Loading -> {
        // Blank
      }
      is MainScreenUiState.Success -> {
        MainScreen(data = (state as MainScreenUiState.Success).data)
      }
      is MainScreenUiState.Error -> {
        Text("Error loading data: ${(state as MainScreenUiState.Error).throwable.message}")
      }
    }
  }
}

@Composable
internal fun MainScreen(data: List<String>, modifier: Modifier = Modifier) {
  Column(modifier) { data.forEach { Greeting(it) } }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
  Text(text = "Hello $name!", modifier = modifier)
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
  FoundryCatalogTheme { MainScreen(listOf("Android")) }
}

@Preview(showBackground = true, widthDp = 340)
@Composable
fun MainScreenPortraitPreview() {
  FoundryCatalogTheme { MainScreen(listOf("Android")) }
}
