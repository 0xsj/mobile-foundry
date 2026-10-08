package dev.mobilefoundry.catalog

import android.os.Bundle
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

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    enableEdgeToEdge()
    setContent {
      FoundryCatalogRoot()
    }
  }
}

@Composable
fun FoundryCatalogRoot() {
  var glassTheme by rememberSaveable { mutableStateOf(false) }
  FoundryCatalogTheme(style = if (glassTheme) FoundryThemeStyle.GLASS else FoundryThemeStyle.SOLID) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
      MainNavigation(glassTheme = glassTheme, onGlassThemeChange = { glassTheme = it })
    }
  }
}
