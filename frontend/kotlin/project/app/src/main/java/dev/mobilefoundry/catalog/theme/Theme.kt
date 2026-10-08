package dev.mobilefoundry.catalog.theme

import androidx.compose.runtime.Composable
import dev.mobilefoundry.ui.styles.tokens.FoundryAppearance
import dev.mobilefoundry.ui.styles.tokens.FoundryThemeStyle
import dev.mobilefoundry.ui.theme.FoundryTheme

@Composable
fun FoundryCatalogTheme(
  darkTheme: Boolean? = null,
  style: FoundryThemeStyle? = null,
  content: @Composable () -> Unit,
) {
  FoundryTheme(appearance = darkTheme?.let { if (it) FoundryAppearance.DARK else FoundryAppearance.LIGHT }, style = style, content = content)
}
