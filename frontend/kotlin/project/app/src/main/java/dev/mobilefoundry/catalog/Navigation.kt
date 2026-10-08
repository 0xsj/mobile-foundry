package dev.mobilefoundry.catalog

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

@Composable
fun MainNavigation() {
  val backStack = rememberNavBackStack(Main)

  NavDisplay(
    backStack = backStack,
    entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
    onBack = { backStack.removeLastOrNull() },
    entryProvider =
      entryProvider {
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
          MainScreen(onItemClick = { navKey -> backStack.add(navKey) }, modifier = Modifier.safeDrawingPadding().padding(16.dp))
        }
      },
  )
}
