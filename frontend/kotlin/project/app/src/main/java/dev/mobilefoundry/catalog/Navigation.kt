package dev.mobilefoundry.catalog

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import dev.mobilefoundry.services.NotificationOpen
import dev.mobilefoundry.catalog.systemnotifications.SystemNotificationCatalogScreen
import dev.mobilefoundry.catalog.systemnotifications.SystemNotificationComposition
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import dev.mobilefoundry.catalog.ui.main.MainScreen
import dev.mobilefoundry.catalog.ui.physics.PhysicsPlaygroundScreen
import dev.mobilefoundry.catalog.ui.health.HealthCatalogScreen
import dev.mobilefoundry.catalog.ui.notes.LocalNotesScreen
import dev.mobilefoundry.catalog.ui.notes.LiveNotesScreen
import dev.mobilefoundry.catalog.ui.shell.patterns.ProductShellCatalogScreen
import dev.mobilefoundry.catalog.ui.session.SessionCatalogScreen
import dev.mobilefoundry.catalog.ui.session.GoSessionCatalogScreen
import dev.mobilefoundry.catalog.ui.notes.NotesCatalogScreen
import dev.mobilefoundry.catalog.ui.query.QueryCatalogScreen
import dev.mobilefoundry.catalog.ui.tokens.TokenCatalogScreen
import dev.mobilefoundry.catalog.ui.forms.FormsCatalogScreen
import dev.mobilefoundry.catalog.ui.components.ComponentCatalogScreen
import dev.mobilefoundry.catalog.ui.graphics.GPUEffectsScreen
import dev.mobilefoundry.catalog.ui.graphics.CompositorStudioScreen
import dev.mobilefoundry.catalog.ui.graphics.ImageStudioScreen
import dev.mobilefoundry.catalog.ui.graphics.ProductStudioScreen

@Composable
fun MainNavigation(glassTheme: Boolean = false, onGlassThemeChange: (Boolean) -> Unit = {}, onExit: (() -> Unit)? = null, notificationOpen: NotificationOpen? = null) {
  val backStack = rememberNavBackStack(Main)
  LaunchedEffect(notificationOpen?.id) {
    if (notificationOpen != null) {
      if (backStack.lastOrNull() != SystemNotificationsCatalog) backStack.add(SystemNotificationsCatalog)
      SystemNotificationComposition.opens.consume(notificationOpen)
    }
  }

  NavDisplay(
    backStack = backStack,
    entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
    onBack = { if (backStack.size > 1) backStack.removeLastOrNull() },
    entryProvider =
      entryProvider {
        entry<SystemNotificationsCatalog> {
          SystemNotificationCatalogScreen(onBack = { backStack.removeLastOrNull() }, modifier = Modifier.safeDrawingPadding().padding(16.dp))
        }
        entry<PhysicsPlaygroundCatalog> {
          PhysicsPlaygroundScreen(onBack = { backStack.removeLastOrNull() }, modifier = Modifier.safeDrawingPadding().padding(16.dp))
        }
        entry<ProductShellsCatalog> {
          ProductShellCatalogScreen(onBack = { backStack.removeLastOrNull() }, modifier = Modifier.safeDrawingPadding())
        }
        entry<ComponentsCatalog> {
          ComponentCatalogScreen(onBack = { backStack.removeLastOrNull() }, modifier = Modifier.safeDrawingPadding().padding(16.dp))
        }
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
        entry<LocalNotesCatalog> {
          LocalNotesScreen(onBack = { backStack.removeLastOrNull() }, modifier = Modifier.safeDrawingPadding().padding(16.dp))
        }
        entry<LiveNotesCatalog> {
          LiveNotesScreen(onBack = { backStack.removeLastOrNull() }, modifier = Modifier.safeDrawingPadding().padding(16.dp))
        }
        entry<SessionCatalog> {
          SessionCatalogScreen(onBack = { backStack.removeLastOrNull() }, modifier = Modifier.safeDrawingPadding().padding(16.dp))
        }
        entry<GoSessionCatalog> {
          GoSessionCatalogScreen(onBack = { backStack.removeLastOrNull() }, modifier = Modifier.safeDrawingPadding().padding(16.dp))
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
