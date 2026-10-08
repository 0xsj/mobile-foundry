package dev.mobilefoundry.catalog

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import dev.mobilefoundry.catalog.ui.main.MainScreen
import dev.mobilefoundry.catalog.ui.health.HealthCatalogScreen
import dev.mobilefoundry.catalog.ui.notes.NotesCatalogScreen
import dev.mobilefoundry.catalog.ui.query.QueryCatalogScreen
import dev.mobilefoundry.catalog.ui.tokens.TokenCatalogScreen
import dev.mobilefoundry.catalog.ui.forms.FormsCatalogScreen
import dev.mobilefoundry.catalog.ui.graphics.GPUEffectsScreen
import dev.mobilefoundry.catalog.ui.graphics.CompositorStudioScreen
import dev.mobilefoundry.catalog.ui.graphics.ImageStudioScreen
import dev.mobilefoundry.catalog.ui.graphics.ProductStudioScreen

@Composable
fun MainNavigation(glassTheme: Boolean = false, onGlassThemeChange: (Boolean) -> Unit = {}, onExit: (() -> Unit)? = null) {
  val backStack = rememberNavBackStack(Main)

  NavDisplay(
    backStack = backStack,
    entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
    onBack = { if (backStack.size > 1) backStack.removeLastOrNull() },
    entryProvider =
      entryProvider {
        entry<CompositorStudioCatalog> {
          CompositorStudioScreen(onBack = { backStack.removeLastOrNull() }, modifier = Modifier.safeDrawingPadding().padding(16.dp))
        }
        entry<ImageStudioCatalog> {
          ImageStudioScreen(onBack = { backStack.removeLastOrNull() }, modifier = Modifier.safeDrawingPadding().padding(16.dp))
        }
        entry<ProductStudioCatalog> {
          ProductStudioScreen(onBack = { backStack.removeLastOrNull() }, modifier = Modifier.safeDrawingPadding().padding(16.dp))
        }
        entry<GPUEffectsCatalog> {
          GPUEffectsScreen(onBack = { backStack.removeLastOrNull() }, modifier = Modifier.safeDrawingPadding().padding(16.dp))
        }
        entry<FormsCatalog> {
          FormsCatalogScreen(onBack = { backStack.removeLastOrNull() }, modifier = Modifier.safeDrawingPadding().padding(16.dp))
        }
        entry<TokensCatalog> {
          TokenCatalogScreen(onBack = { backStack.removeLastOrNull() }, modifier = Modifier.safeDrawingPadding().padding(16.dp))
        }
        entry<QueryCatalog> {
          QueryCatalogScreen(onBack = { backStack.removeLastOrNull() }, modifier = Modifier.safeDrawingPadding().padding(16.dp))
        }
        entry<NotesCatalog> {
          NotesCatalogScreen(onBack = { backStack.removeLastOrNull() }, modifier = Modifier.safeDrawingPadding().padding(16.dp))
        }
        entry<HealthCatalog> {
          HealthCatalogScreen(onBack = { backStack.removeLastOrNull() }, modifier = Modifier.safeDrawingPadding().padding(16.dp))
        }
        entry<Main> {
          MainScreen(onItemClick = { navKey -> backStack.add(navKey) }, modifier = Modifier.safeDrawingPadding().padding(16.dp),
            glassTheme = glassTheme, onGlassThemeChange = onGlassThemeChange, onExit = onExit)
        }
      },
  )
  BackHandler(enabled = backStack.size == 1 && onExit != null) { onExit?.invoke() }
}
