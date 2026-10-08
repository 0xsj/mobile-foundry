package dev.mobilefoundry.catalog.ui.main

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import dev.mobilefoundry.catalog.HealthCatalog
import dev.mobilefoundry.catalog.NotesCatalog
import dev.mobilefoundry.catalog.QueryCatalog
import dev.mobilefoundry.catalog.TokensCatalog
import dev.mobilefoundry.catalog.FormsCatalog
import dev.mobilefoundry.catalog.ComponentsCatalog
import dev.mobilefoundry.catalog.GPUEffectsCatalog
import dev.mobilefoundry.catalog.CompositorStudioCatalog
import dev.mobilefoundry.catalog.ImageStudioCatalog
import dev.mobilefoundry.catalog.ProductStudioCatalog
import dev.mobilefoundry.catalog.data.DefaultDataRepository
import dev.mobilefoundry.catalog.theme.FoundryCatalogTheme

@Composable
fun MainScreen(
  onItemClick: (NavKey) -> Unit,
  modifier: Modifier = Modifier,
  viewModel: MainScreenViewModel = viewModel { MainScreenViewModel(DefaultDataRepository()) },
  glassTheme: Boolean = false,
  onGlassThemeChange: (Boolean) -> Unit = {},
  onExit: (() -> Unit)? = null,
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  Column(modifier.verticalScroll(rememberScrollState())) {
    if (onExit != null) Button(onClick = onExit) { Text("Close catalog") }
    Text("Mobile Foundry")
    Row {
      Text("Glass surfaces")
      Switch(checked = glassTheme, onCheckedChange = onGlassThemeChange,
        modifier = Modifier.semantics { contentDescription = "Glass surfaces" })
    }
    Button(onClick = { onItemClick(TokensCatalog) }) { Text("Tokens") }
    Button(onClick = { onItemClick(ComponentsCatalog) }) { Text("Components") }
    Button(onClick = { onItemClick(FormsCatalog) }) { Text("Forms and mutations") }
    Button(onClick = { onItemClick(GPUEffectsCatalog) }) { Text("GPU effects") }
    Button(onClick = { onItemClick(CompositorStudioCatalog) }) { Text("Compositor studio") }
    Button(onClick = { onItemClick(ImageStudioCatalog) }) { Text("Image studio") }
    Button(onClick = { onItemClick(ProductStudioCatalog) }) { Text("Product studio") }
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
