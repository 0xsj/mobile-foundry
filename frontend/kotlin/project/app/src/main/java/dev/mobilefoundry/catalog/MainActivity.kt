package dev.mobilefoundry.catalog

import android.os.Bundle
import android.content.Intent
import dev.mobilefoundry.catalog.systemnotifications.AndroidSystemNotifications
import dev.mobilefoundry.catalog.systemnotifications.SystemNotificationComposition
import dev.mobilefoundry.catalog.systemnotifications.AndroidRemoteNotifications
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import dev.mobilefoundry.catalog.theme.FoundryCatalogTheme
import dev.mobilefoundry.ui.styles.tokens.FoundryThemeStyle
import dev.mobilefoundry.catalog.ui.shell.AppShell

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    AndroidRemoteNotifications.ensureChannel(this)
    acceptNotificationIntent(intent)

    enableEdgeToEdge()
    setContent {
      FoundryCatalogRoot()
    }
  }
  override fun onNewIntent(intent: Intent) { super.onNewIntent(intent);acceptNotificationIntent(intent) }
  private fun acceptNotificationIntent(incoming: Intent) {
    val local = AndroidSystemNotifications.readPreview(this,incoming)
    val remote = AndroidRemoteNotifications.readPreview(this,incoming)
    when {
      local != null -> SystemNotificationComposition.opens.receive(local)
      remote != null -> SystemNotificationComposition.opens.receive(remote)
      else -> return
    }
    // Do not replay a consumed notification when the Activity is recreated.
    setIntent(AndroidRemoteNotifications.clearedIntent(this))
  }
}

@Composable
fun FoundryCatalogRoot() {
  var glassTheme by rememberSaveable { mutableStateOf(true) }
  FoundryCatalogTheme(style = if (glassTheme) FoundryThemeStyle.GLASS else FoundryThemeStyle.SOLID) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
      AppShell(glassTheme = glassTheme, onGlassThemeChange = { glassTheme = it })
    }
  }
}
