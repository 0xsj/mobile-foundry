package dev.mobilefoundry.catalog.ui.shell

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import dev.mobilefoundry.catalog.MainNavigation
import dev.mobilefoundry.catalog.R
import dev.mobilefoundry.catalog.ui.camera.CameraScreen
import dev.mobilefoundry.graphics.RasterImage
import dev.mobilefoundry.ui.components.layout.surface.Backdrop
import dev.mobilefoundry.ui.components.layout.surface.Surface
import dev.mobilefoundry.ui.components.layout.surface.SurfaceRole
import dev.mobilefoundry.ui.components.navigation.tabbar.TabBar
import dev.mobilefoundry.ui.components.navigation.tabbar.TabItem
import dev.mobilefoundry.ui.theme.FoundryTheme

private enum class ShellTab(val label: String, val icon: Int) {
    HOME("Home", R.drawable.ic_tab_home),
    LIBRARY("Library", R.drawable.ic_tab_library),
    CAMERA("Camera", R.drawable.ic_tab_camera),
    STUDIO("Studio", R.drawable.ic_tab_studio),
    ACCOUNT("Account", R.drawable.ic_tab_account),
}

/** App-owned destinations. No feature is kept running under an invisible tab. */
@Composable
fun AppShell(glassTheme: Boolean, onGlassThemeChange: (Boolean) -> Unit) {
    var selectedId by rememberSaveable { mutableStateOf(ShellTab.HOME.name) }
    var catalogPresented by rememberSaveable { mutableStateOf(false) }
    var cameraPhoto by remember { mutableStateOf<RasterImage?>(null) }
    val tab = ShellTab.entries.firstOrNull { it.name == selectedId } ?: ShellTab.HOME
    val tokens = FoundryTheme.tokens

    if (catalogPresented) {
        // Scope entry ViewModels to this presentation instead of the whole Activity.
        val owner = remember { object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        } }
        DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
        CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
            MainNavigation(glassTheme, onGlassThemeChange, onExit = { catalogPresented = false })
        }
    } else {
        Backdrop(Modifier.fillMaxSize(), background = {
            Canvas(Modifier.fillMaxSize()) {
                drawRect(tokens.colors.surfaceGround.color)
                drawRect(Brush.radialGradient(
                    listOf(tokens.colors.accent.color.copy(alpha = 0.12f), Color.Transparent),
                    center = Offset(size.width, size.height),
                    radius = maxOf(size.width, size.height) * 0.7f))
            }
        }) {
            Column(Modifier.fillMaxSize()) {
                if (tab == ShellTab.CAMERA) {
                    CameraScreen(cameraPhoto, onPhotoChange = { cameraPhoto = it },
                        modifier = Modifier.weight(1f).windowInsetsPadding(
                            WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                            .padding(tokens.space.page))
                } else {
                    ShellPlaceholder(tab, glassTheme, onGlassThemeChange,
                        onOpenCatalog = { catalogPresented = true },
                        modifier = Modifier.weight(1f).windowInsetsPadding(
                            WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)))
                }
                TabBar(
                    items = ShellTab.entries.map { destination ->
                        TabItem(destination.name, destination.label) {
                            Icon(painterResource(destination.icon), contentDescription = null)
                        }
                    }, selectedId = tab.name, onSelect = { selectedId = it },
                    modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                        .padding(horizontal = tokens.space.page, vertical = tokens.space.stack))
            }
        }
    }
}

@Composable
private fun ShellPlaceholder(
    tab: ShellTab,
    glassTheme: Boolean,
    onGlassThemeChange: (Boolean) -> Unit,
    onOpenCatalog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = FoundryTheme.tokens
    Column(modifier.fillMaxWidth().padding(tokens.space.page)) {
        Text(tab.label, style = MaterialTheme.typography.headlineLarge)
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(tokens.space.stack)) {
                Icon(painterResource(tab.icon), contentDescription = null,
                    modifier = Modifier.size(48.dp), tint = tokens.colors.inkMuted.color)
                Text("Nothing here yet.", color = tokens.colors.inkSecondary.color)
            }
        }
        if (tab == ShellTab.STUDIO) {
            Button(onClick = onOpenCatalog, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Open catalog")
            }
        }
        if (tab == ShellTab.ACCOUNT) {
            Surface(role = SurfaceRole.FLOATING) {
                Row(Modifier.fillMaxWidth().padding(tokens.space.page),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("Glass surfaces", modifier = Modifier.weight(1f))
                    Switch(glassTheme, onCheckedChange = onGlassThemeChange,
                        modifier = Modifier.semantics { contentDescription = "Glass surfaces" })
                }
            }
        }
    }
}
